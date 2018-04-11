/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

/**
 * shhh don't talk about this
 */
public class InternalParseFCMParseAccess {

    private static final String TAG = "ParseFCM";

    public static void logVerbose(String message) {
        PLog.v(TAG, message);
    }

    public static void logError(String message, Throwable tr) {
        PLog.e(TAG, message, tr);
    }

    public static void setToken(ParseInstallation installation, String token) {
        installation.setDeviceToken(token);
        //Yes, we are actually FCM, but its the same for our purposes
        installation.setPushType(PushType.GCM);
    }
}
