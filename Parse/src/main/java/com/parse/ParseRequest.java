/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.os.Build;

import com.parse.http.ParseHttpBody;
import com.parse.http.ParseHttpRequest;
import com.parse.http.ParseHttpResponse;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import bolts.Continuation;
import bolts.Task;
import bolts.TaskCompletionSource;

/**
 * ParseRequest takes an arbitrary HttpUriRequest and retries it a number of times with
 * exponential backoff.
 */
/** package */ abstract class ParseRequest<Response> {

  private static final ThreadFactory sThreadFactory = new ThreadFactory() {
    private final AtomicInteger mCount = new AtomicInteger(1);

    public Thread newThread(Runnable r) {
      return new Thread(r, "ParseRequest.NETWORK_EXECUTOR-thread-" + mCount.getAndIncrement());
    }
  };

  /**
   * We want to use more threads than default in {@code bolts.Executors} since most of the time
   * the threads will be asleep waiting for data.
   */
  private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
  private static final int CORE_POOL_SIZE = CPU_COUNT * 2 + 1;
  private static final int MAX_POOL_SIZE = CPU_COUNT * 2 * 2 + 1;
  private static final long KEEP_ALIVE_TIME = 1L;
  private static final int MAX_QUEUE_SIZE = 128;

  private static ThreadPoolExecutor newThreadPoolExecutor(int corePoolSize, int maxPoolSize,
      long keepAliveTime, TimeUnit timeUnit, BlockingQueue<Runnable> workQueue,
      ThreadFactory threadFactory) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(corePoolSize, maxPoolSize,
        keepAliveTime, timeUnit, workQueue, threadFactory);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
      executor.allowCoreThreadTimeOut(true);
    }
    return executor;
  }

  protected static final ExecutorService NETWORK_EXECUTOR = newThreadPoolExecutor(
      CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
      new LinkedBlockingQueue<Runnable>(MAX_QUEUE_SIZE), sThreadFactory);

  protected static final int DEFAULT_MAX_RETRIES = 4;
  /* package */ static final long DEFAULT_INITIAL_RETRY_DELAY = 1000L;

  private static long defaultInitialRetryDelay = DEFAULT_INITIAL_RETRY_DELAY;

  public static void setDefaultInitialRetryDelay(long delay) {
    defaultInitialRetryDelay = delay;
  }
  public static long getDefaultInitialRetryDelay() {
    return defaultInitialRetryDelay;
  }

  private int maxRetries = DEFAULT_MAX_RETRIES;

  /* package */ ParseHttpRequest.Method method;
  /* package */ String url;

  public ParseRequest(String url) {
    this(ParseHttpRequest.Method.GET, url);
  }

  public ParseRequest(ParseHttpRequest.Method method, String url) {
    this.method = method;
    this.url = url;
  }

  public void setMaxRetries(int max) {
    maxRetries = max;
  }

  protected ParseHttpBody newBody(ProgressCallback uploadProgressCallback) {
    // do nothing
    return null;
  }

  protected ParseHttpRequest newRequest(
      ParseHttpRequest.Method method,
      String url,
      ProgressCallback uploadProgressCallback)  {
    ParseHttpRequest.Builder requestBuilder = new ParseHttpRequest.Builder()
        .setMethod(method)
        .setUrl(url);

    switch (method) {
      case GET:
      case DELETE:
        break;
      case POST:
      case PUT:
        requestBuilder.setBody(newBody(uploadProgressCallback));
        break;
      default:
        throw new IllegalStateException("Invalid method " + method);
    }
    return requestBuilder.build();
  }

  /*
   * Runs one iteration of the request.
   */
  private Task<Response> sendOneRequestAsync(
      final ParseHttpClient client,
      final ParseHttpRequest request,
      final ProgressCallback downloadProgressCallback) {
    return Task.<Void>forResult(null).onSuccessTask(new Continuation<Void, Task<Response>>() {
      @Override
      public Task<Response> then(Task<Void> task) throws Exception {
        ParseHttpResponse response = client.execute(request);
        return onResponseAsync(response, downloadProgressCallback);
      }
    }, NETWORK_EXECUTOR).continueWithTask(new Continuation<Response, Task<Response>>() {
      @Override
      public Task<Response> then(Task<Response> task) throws Exception {
        if (task.isFaulted()) {
          Exception error = task.getError();
          if (error instanceof IOException) {
            return Task.forError(newTemporaryException("i/o failure", error));
          }
        }
        return task;
        // Jump off the network executor so this task continuations won't steal network threads
      }
    }, Task.BACKGROUND_EXECUTOR);
  }

  protected abstract Task<Response> onResponseAsync(ParseHttpResponse response,
      ProgressCallback downloadProgressCallback);

  /*
   * Starts retrying the block.
   */
  public Task<Response> executeAsync(final ParseHttpClient client) {
    return executeAsync(client, (ProgressCallback) null, null, null);
  }

  public Task<Response> executeAsync(final ParseHttpClient client, Task<Void> cancellationToken) {
    return executeAsync(client, (ProgressCallback) null, null, cancellationToken);
  }

  public Task<Response> executeAsync(
      final ParseHttpClient client,
      final ProgressCallback uploadProgressCallback,
      final ProgressCallback downloadProgressCallback) {
    return executeAsync(client, uploadProgressCallback, downloadProgressCallback, null);
  }

  public Task<Response> executeAsync(
      final ParseHttpClient client,
      final ProgressCallback uploadProgressCallback,
      final ProgressCallback downloadProgressCallback,
      Task<Void> cancellationToken) {
    ParseHttpRequest request = newRequest(method, url, uploadProgressCallback);
    return executeAsync(
        client,
        request,
        downloadProgressCallback,
        cancellationToken);
  }

  // Although we can not cancel a single request, but we allow cancel between retries so we need a
  // cancellationToken here.
  private Task<Response> executeAsync(
      final ParseHttpClient client,
      final ParseHttpRequest request,
      final ProgressCallback downloadProgressCallback,
      final Task<Void> cancellationToken) {
    long delay = defaultInitialRetryDelay + (long) (defaultInitialRetryDelay * Math.random());

    return executeAsync(
        client,
        request,
        0,
        delay,
        downloadProgressCallback,
        cancellationToken);
  }

  private Task<Response> executeAsync(
      final ParseHttpClient client,
      final ParseHttpRequest request,
      final int attemptsMade,
      final long delay,
      final ProgressCallback downloadProgressCallback,
      final Task<Void> cancellationToken) {
    if (cancellationToken != null && cancellationToken.isCancelled()) {
      return Task.cancelled();
    }
    return sendOneRequestAsync(client, request, downloadProgressCallback).continueWithTask(new Continuation<Response, Task<Response>>() {
      @Override
      public Task<Response> then(Task<Response> task) throws Exception {
        Exception e = task.getError();
        if (task.isFaulted() && e instanceof ParseException) {
          if (cancellationToken != null && cancellationToken.isCancelled()) {
            return Task.cancelled();
          }

          if (e instanceof ParseRequestException &&
              ((ParseRequestException) e).isPermanentFailure) {
            return task;
          }

          if (attemptsMade < maxRetries) {
            PLog.i("com.parse.ParseRequest", "Request failed. Waiting " + delay
                + " milliseconds before attempt #" + (attemptsMade + 1));

            final TaskCompletionSource<Response> retryTask = new TaskCompletionSource<>();
            ParseExecutors.scheduled().schedule(new Runnable() {
              @Override
              public void run() {
                executeAsync(
                    client,
                    request,
                    attemptsMade + 1,
                    delay * 2,
                    downloadProgressCallback,
                    cancellationToken).continueWithTask(new Continuation<Response, Task<Void>>() {
                  @Override
                  public Task<Void> then(Task<Response> task) throws Exception {
                    if (task.isCancelled()) {
                      retryTask.setCancelled();
                    } else if (task.isFaulted()) {
                      retryTask.setError(task.getError());
                    } else {
                      retryTask.setResult(task.getResult());
                    }
                    return null;
                  }
                });
              }
            }, delay, TimeUnit.MILLISECONDS);
            return retryTask.getTask();
          }
        }
        return task;
      }
    });
  }

  /**
   * Constructs a permanent exception that won't be retried.
   */
  protected ParseException newPermanentException(int code, String message) {
    ParseRequestException e = new ParseRequestException(code, message);
    e.isPermanentFailure = true;
    return e;
  }

  /**
   * Constructs a temporary exception that will be retried.
   */
  protected ParseException newTemporaryException(int code, String message) {
    ParseRequestException e = new ParseRequestException(code, message);
    e.isPermanentFailure = false;
    return e;
  }

  /**
   * Constructs a temporary exception that will be retried with json error code 100.
   *
   * @see ParseException#CONNECTION_FAILED
   */
  protected ParseException newTemporaryException(String message, Throwable t) {
    ParseRequestException e = new ParseRequestException(
        ParseException.CONNECTION_FAILED, message, t);
    e.isPermanentFailure = false;
    return e;
  }

  private static class ParseRequestException extends ParseException {
    boolean isPermanentFailure = false;

    public ParseRequestException(int theCode, String theMessage) {
      super(theCode, theMessage);
    }

    public ParseRequestException(int theCode, String message, Throwable cause) {
      super(theCode, message, cause);
    }
  }
}
