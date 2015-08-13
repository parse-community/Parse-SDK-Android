/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.util.Log;

/** package */ class PLog {
  public static final int LOG_LEVEL_NONE = Integer.MAX_VALUE;

  private static int logLevel = Integer.MAX_VALUE;

  /**
   * Sets the level of logging to display, where each level includes all those below it. The default
   * level is {@link #LOG_LEVEL_NONE}. Please ensure this is set to {@link Log#ERROR}
   * or {@link #LOG_LEVEL_NONE} before deploying your app to ensure no sensitive information is
   * logged. The levels are:
   * <ul>
   * <li>{@link Log#VERBOSE}</li>
   * <li>{@link Log#DEBUG}</li>
   * <li>{@link Log#INFO}</li>
   * <li>{@link Log#WARN}</li>
   * <li>{@link Log#ERROR}</li>
   * <li>{@link #LOG_LEVEL_NONE}</li>
   * </ul>
   *
   * @param logLevel
   *          The level of logcat logging that Parse should do.
   */
  public static void setLogLevel(int logLevel) {
    PLog.logLevel = logLevel;
  }

  /**
   * Returns the level of logging that will be displayed.
   */
  public static int getLogLevel() {
    return logLevel;
  }

  private static void log(int messageLogLevel, String tag, String message, Throwable tr) {
    if (messageLogLevel >= logLevel) {
      if (tr == null) {
        Log.println(logLevel, tag, message);
      } else {
        Log.println(logLevel, tag, message + '\n' + Log.getStackTraceString(tr));
      }
    }
  }

  /* package */ static void v(String tag, String message, Throwable tr) {
    log(Log.VERBOSE, tag, message, tr);
  }

  /* package */ static void v(String tag, String message) {
    v(tag, message, null);
  }

  /* package */ static void d(String tag, String message, Throwable tr) {
    log(Log.DEBUG, tag, message, tr);
  }

  /* package */ static void d(String tag, String message) {
    d(tag, message, null);
  }

  /* package */ static void i(String tag, String message, Throwable tr) {
    log(Log.INFO, tag, message, tr);
  }

  /* package */ static void i(String tag, String message) {
    i(tag, message, null);
  }

  /* package */ static void w(String tag, String message, Throwable tr) {
    log(Log.WARN, tag, message, tr);
  }

  /* package */ static void w(String tag, String message) {
    w(tag, message, null);
  }

  /* package */ static void e(String tag, String message, Throwable tr) {
    log(Log.ERROR, tag, message, tr);
  }

  /* package */ static void e(String tag, String message) {
    e(tag, message, null);
  }
}
