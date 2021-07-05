/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import android.annotation.SuppressLint
import android.util.Log

/**
 * Parse Logger. See [.setLogLevel]
 */
object PLog {
    const val LOG_LEVEL_NONE = Int.MAX_VALUE
    /**
     * Returns the level of logging that will be displayed.
     */
    /**
     * Sets the level of logging to display, where each level includes all those below it. The default
     * level is [.LOG_LEVEL_NONE]. Please ensure this is set to [Log.ERROR]
     * or [.LOG_LEVEL_NONE] before deploying your app to ensure no sensitive information is
     * logged. The levels are:
     *
     *  * [Log.VERBOSE]
     *  * [Log.DEBUG]
     *  * [Log.INFO]
     *  * [Log.WARN]
     *  * [Log.ERROR]
     *  * [.LOG_LEVEL_NONE]
     *
     *
     * @param logLevel The level of logcat logging that Parse should do.
     */
    @JvmStatic
    var logLevel = Int.MAX_VALUE
    @SuppressLint("WrongConstant")
    private fun log(messageLogLevel: Int, tag: String, message: String, tr: Throwable?) {
        if (messageLogLevel >= logLevel) {
            if (tr == null) {
                Log.println(logLevel, tag, message)
            } else {
                Log.println(
                    logLevel, tag, """
     $message
     ${Log.getStackTraceString(tr)}
     """.trimIndent()
                )
            }
        }
    }

    @JvmOverloads
    fun v(tag: String, message: String, tr: Throwable? = null) {
        log(Log.VERBOSE, tag, message, tr)
    }

    @JvmOverloads
    fun d(tag: String, message: String, tr: Throwable? = null) {
        log(Log.DEBUG, tag, message, tr)
    }

    @JvmOverloads
    fun i(tag: String, message: String, tr: Throwable? = null) {
        log(Log.INFO, tag, message, tr)
    }

    @JvmStatic
    @JvmOverloads
    fun w(tag: String, message: String, tr: Throwable? = null) {
        log(Log.WARN, tag, message, tr)
    }

    @JvmStatic
    @JvmOverloads
    fun e(tag: String, message: String, tr: Throwable? = null) {
        log(Log.ERROR, tag, message, tr)
    }
}