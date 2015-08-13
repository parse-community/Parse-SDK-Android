/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.app.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/** package */ class PPNSUtil {
  /* package for tests */ static String CLASS_PPNS_SERVICE = "com.parse.PPNSService";

  public static boolean isPPNSAvailable() {
    try {
      Class.forName(CLASS_PPNS_SERVICE);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  @SuppressWarnings("TryWithIdenticalCatches")
  public static ProxyService newPPNSService(Service service) {
    try {
      Class<?> clazz = Class.forName(CLASS_PPNS_SERVICE);
      Constructor<?> cons = clazz.getDeclaredConstructor(Service.class);
      return (ProxyService) cons.newInstance(service);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
