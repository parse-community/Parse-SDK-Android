/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.ParseQuery
import com.parse.boltsinternal.Continuation
import com.parse.boltsinternal.Task
import java.util.*

internal class OfflineObjectStore<T : ParseObject>(
    private val className: String,
    private val pinName: String?,
    private val legacy: ParseObjectStore<T>
) : ParseObjectStore<T> {
    constructor(clazz: Class<T>?, pinName: String?, legacy: ParseObjectStore<T>) : this(
        subclassingController.getClassName(clazz), pinName, legacy
    ) {
    }

    override fun setAsync(`object`: T): Task<Void> {
        return ParseObject.unpinAllInBackground(pinName).continueWithTask {
            `object`.pinInBackground(
                pinName!!, false
            )
        }
    }

    // We need to set `ignoreACLs` since we can't use ACLs without the current user.
    override val getAsync: Task<T>
        get() {
            // We need to set `ignoreACLs` since we can't use ACLs without the current user.
            val query = ParseQuery.getQuery<T>(className)
                .fromPin(pinName)
                .ignoreACLs()
            return query.findInBackground().onSuccessTask { task: Task<List<T>?> ->
                val results = task.result
                if (results != null) {
                    if (results.size == 1) {
                        return@onSuccessTask Task.forResult(results[0])
                    } else {
                        return@onSuccessTask ParseObject.unpinAllInBackground(pinName).cast()
                    }
                }
                Task.forResult<Any?>(null)
            }.onSuccessTask {
                val ldsObject = it.result
                if (ldsObject != null) {
                    return@onSuccessTask it.cast()
                }
                migrate(legacy, this@OfflineObjectStore).cast()
            }
        }

    override fun existsAsync(): Task<Boolean> {
        // We need to set `ignoreACLs` since we can't use ACLs without the current user.
        val query = ParseQuery.getQuery<T>(className)
            .fromPin(pinName)
            .ignoreACLs()
        return query.countInBackground().onSuccessTask { task: Task<Int> ->
            val exists = task.result == 1
            if (exists) {
                return@onSuccessTask Task.forResult(true)
            }
            legacy.existsAsync()
        }
    }

    override fun deleteAsync(): Task<Void> {
        val ldsTask = ParseObject.unpinAllInBackground(
            pinName
        )
        return Task.whenAll(
            listOf(
                legacy.deleteAsync(),
                ldsTask
            )
        ).continueWithTask { ldsTask }
    }

    companion object {
        private val subclassingController: ParseObjectSubclassingController
            get() = ParseCorePlugins.getInstance().subclassingController

        private fun <T : ParseObject> migrate(
            from: ParseObjectStore<T>, to: ParseObjectStore<T>
        ): Task<T> {
            return from.getAsync.onSuccessTask { task: Task<T> ->
                val `object` = task.result ?: return@onSuccessTask task
                Task.whenAll(
                    listOf(
                        from.deleteAsync(),
                        to.setAsync(`object`)
                    )
                ).continueWith { `object` }
            }
        }
    }
}