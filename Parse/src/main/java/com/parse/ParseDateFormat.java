/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.SimpleTimeZone;

/**
 * This is the currently used date format. It is precise to the millisecond.
 */
/* package */ class ParseDateFormat {
  private static final String TAG = "ParseDateFormat";

  private static final ParseDateFormat INSTANCE = new ParseDateFormat();
  public static ParseDateFormat getInstance() {
    return INSTANCE;
  }

  // SimpleDateFormat isn't inherently thread-safe
  private final Object lock = new Object();

  private final DateFormat dateFormat;

  private ParseDateFormat() {
    DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    format.setTimeZone(new SimpleTimeZone(0, "GMT"));
    dateFormat = format;
  }

  /* package */ Date parse(String dateString) {
    synchronized (lock) {
      try {
        return dateFormat.parse(dateString);
      } catch (java.text.ParseException e) {
        // Should never happen
        PLog.e(TAG, "could not parse date: " + dateString, e);
        return null;
      }
    }
  }

  /* package */ String format(Date date) {
    synchronized (lock) {
      return dateFormat.format(date);
    }
  }
}
