/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.PLog.i
import com.parse.boltsinternal.Continuation
import com.parse.boltsinternal.Task
import com.parse.boltsinternal.TaskCompletionSource
import com.parse.http.ParseHttpBody
import com.parse.http.ParseHttpRequest
import com.parse.http.ParseHttpResponse
import java.io.IOException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * ParseRequest takes an arbitrary HttpUriRequest and retries it a number of times with
 * exponential backoff.
 */
internal abstract class ParseRequest<Response>(/* package */
    var method: ParseHttpRequest.Method, /* package */
    var url: String?
) {
    constructor(url: String?) : this(ParseHttpRequest.Method.GET, url)

    protected open fun newBody(uploadProgressCallback: ProgressCallback?): ParseHttpBody? {
        // do nothing
        return null
    }

    protected open fun newRequest(
        method: ParseHttpRequest.Method,
        url: String?,
        uploadProgressCallback: ProgressCallback?
    ): ParseHttpRequest {
        val requestBuilder = ParseHttpRequest.Builder()
            .setMethod(method)
            .setUrl(url)
        when (method) {
            ParseHttpRequest.Method.GET, ParseHttpRequest.Method.DELETE -> {
            }
            ParseHttpRequest.Method.POST, ParseHttpRequest.Method.PUT -> requestBuilder.setBody(
                newBody(uploadProgressCallback)
            )
        }
        return requestBuilder.build()
    }

    /*
     * Runs one iteration of the request.
     */
    private fun sendOneRequestAsync(
        client: ParseHttpClient,
        request: ParseHttpRequest,
        downloadProgressCallback: ProgressCallback?
    ): Task<Response> {
        return Task.forResult<Void?>(null).onSuccessTask({
            val response = client.execute(request)
            onResponseAsync(response, downloadProgressCallback)
        }, NETWORK_EXECUTOR).continueWithTask(Continuation continueWithTask@{ task: Task<Response> ->
            if (task.isFaulted) {
                val error = task.error
                if (error is IOException) {
                    return@continueWithTask Task.forError(
                        newTemporaryException(
                            "i/o failure",
                            error
                        )
                    )
                }
            }
            task
        }, Task.BACKGROUND_EXECUTOR)
    }

    protected abstract fun onResponseAsync(
        response: ParseHttpResponse?,
        downloadProgressCallback: ProgressCallback?
    ): Task<Response?>?

    /*
     * Starts retrying the block.
     */
    fun executeAsync(client: ParseHttpClient): Task<Response> {
        return executeAsync(client, null as ProgressCallback?, null, null)
    }

    fun executeAsync(client: ParseHttpClient, cancellationToken: Task<Void>?): Task<Response> {
        return executeAsync(client, null as ProgressCallback?, null, cancellationToken)
    }

    fun executeAsync(
        client: ParseHttpClient,
        uploadProgressCallback: ProgressCallback?,
        downloadProgressCallback: ProgressCallback?
    ): Task<Response> {
        return executeAsync(client, uploadProgressCallback, downloadProgressCallback, null)
    }

    open fun executeAsync(
        client: ParseHttpClient,
        uploadProgressCallback: ProgressCallback?,
        downloadProgressCallback: ProgressCallback?,
        cancellationToken: Task<Void>?
    ): Task<Response> {
        val request = newRequest(method, url, uploadProgressCallback)
        return executeAsync(
            client,
            request,
            downloadProgressCallback,
            cancellationToken
        )
    }

    // Although we can not cancel a single request, but we allow cancel between retries so we need a
    // cancellationToken here.
    private fun executeAsync(
        client: ParseHttpClient,
        request: ParseHttpRequest,
        downloadProgressCallback: ProgressCallback?,
        cancellationToken: Task<Void>?
    ): Task<Response> {
        val delay = defaultInitialRetryDelay + (defaultInitialRetryDelay * Math.random()).toLong()
        return executeAsync(
            client,
            request,
            0,
            delay,
            downloadProgressCallback,
            cancellationToken
        )
    }

    private fun executeAsync(
        client: ParseHttpClient,
        request: ParseHttpRequest,
        attemptsMade: Int,
        delay: Long,
        downloadProgressCallback: ProgressCallback?,
        cancellationToken: Task<Void>?
    ): Task<Response> {
        return if (cancellationToken != null && cancellationToken.isCancelled) {
            Task.cancelled()
        } else sendOneRequestAsync(
            client,
            request,
            downloadProgressCallback
        ).continueWithTask { task: Task<Response> ->
            val e = task.error
            if (task.isFaulted && e is ParseException) {
                if (cancellationToken != null && cancellationToken.isCancelled) {
                    return@continueWithTask Task.cancelled()
                }
                if (e is ParseRequestException &&
                    e.isPermanentFailure
                ) {
                    return@continueWithTask task
                }
                if (attemptsMade < maxRetries()) {
                    i(
                        "com.parse.ParseRequest", "Request failed. Waiting " + delay
                                + " milliseconds before attempt #" + (attemptsMade + 1)
                    )
                    val retryTask = TaskCompletionSource<Response>()
                    ParseExecutors.scheduled().schedule({
                        executeAsync(
                            client,
                            request,
                            attemptsMade + 1,
                            delay * 2,
                            downloadProgressCallback,
                            cancellationToken
                        ).continueWithTask(Continuation<Response, Task<Void>?> { task1: Task<Response> ->
                            when {
                                task1.isCancelled -> {
                                    retryTask.setCancelled()
                                }
                                task1.isFaulted -> {
                                    retryTask.setError(task1.error)
                                }
                                else -> {
                                    retryTask.setResult(task1.result)
                                }
                            }
                            null
                        })
                    }, delay, TimeUnit.MILLISECONDS)
                    return@continueWithTask retryTask.task
                }
            }
            task
        }
    }

    /**
     * Constructs a permanent exception that won't be retried.
     */
    protected fun newPermanentException(code: Int, message: String?): ParseException {
        val e = ParseRequestException(code, message)
        e.isPermanentFailure = true
        return e
    }

    /**
     * Constructs a temporary exception that will be retried.
     */
    protected fun newTemporaryException(code: Int, message: String?): ParseException {
        val e = ParseRequestException(code, message)
        e.isPermanentFailure = false
        return e
    }

    /**
     * Constructs a temporary exception that will be retried with json error code 100.
     *
     * @see ParseException.CONNECTION_FAILED
     */
    protected fun newTemporaryException(message: String?, t: Throwable?): ParseException {
        val e = ParseRequestException(
            ParseException.CONNECTION_FAILED, message, t
        )
        e.isPermanentFailure = false
        return e
    }

    private class ParseRequestException : ParseException {
        var isPermanentFailure = false

        constructor(theCode: Int, theMessage: String?) : super(theCode, theMessage)
        constructor(theCode: Int, message: String?, cause: Throwable?) : super(
            theCode,
            message,
            cause
        )
    }

    companion object {
        const val DEFAULT_MAX_RETRIES = 4

        /* package */
        const val DEFAULT_INITIAL_RETRY_DELAY = 1000L
        private val sThreadFactory: ThreadFactory = object : ThreadFactory {
            private val mCount = AtomicInteger(1)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "ParseRequest.NETWORK_EXECUTOR-thread-" + mCount.getAndIncrement())
            }
        }

        /**
         * We want to use more threads than default in `bolts.Executors` since most of the time
         * the threads will be asleep waiting for data.
         */
        private val CPU_COUNT = Runtime.getRuntime().availableProcessors()
        private val CORE_POOL_SIZE = CPU_COUNT * 2 + 1
        private val MAX_POOL_SIZE = CPU_COUNT * 2 * 2 + 1
        private const val KEEP_ALIVE_TIME = 1L
        private const val MAX_QUEUE_SIZE = 128
        protected val NETWORK_EXECUTOR: ExecutorService = newThreadPoolExecutor(
            CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
            LinkedBlockingQueue(MAX_QUEUE_SIZE), sThreadFactory
        )
        @JvmStatic
        var defaultInitialRetryDelay = DEFAULT_INITIAL_RETRY_DELAY
        private fun newThreadPoolExecutor(
            corePoolSize: Int, maxPoolSize: Int,
            keepAliveTime: Long, timeUnit: TimeUnit, workQueue: BlockingQueue<Runnable>,
            threadFactory: ThreadFactory
        ): ThreadPoolExecutor {
            val executor = ThreadPoolExecutor(
                corePoolSize, maxPoolSize,
                keepAliveTime, timeUnit, workQueue, threadFactory
            )
            executor.allowCoreThreadTimeOut(true)
            return executor
        }

        private fun maxRetries(): Int {
            //typically happens just within tests
            return if (ParsePlugins.get() == null) {
                DEFAULT_MAX_RETRIES
            } else {
                ParsePlugins.get().configuration().maxRetries
            }
        }
    }
}