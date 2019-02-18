/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.fcm;

import com.parse.PLog;
import com.parse.ParseException;
import com.parse.ParseInstallation;
import com.parse.SaveCallback;

public class ParseFCM {

    static final String TAG = "ParseFCM";

    private static final String PUSH_TYPE = "gcm"; // Backwards compatability with Parse servers

    /**
     * You can call this manually if you are overriding the {@link com.google.firebase.messaging.FirebaseMessagingService}
     * fetching the token via {@link com.google.firebase.messaging.FirebaseMessagingService#onNewToken(String)}
     *
     * @param token the token
     */
    public static void register(String token) {
        PLog.d(ParseFCM.TAG, "Updating FCM token");
        ParseInstallation installation = ParseInstallation.getCurrentInstallation();
        if (installation != null && token != null) {
            installation.setDeviceToken(token);
            //even though this is FCM, calling it gcm will work on the backend
            installation.setPushType(PUSH_TYPE);
            installation.saveInBackground(new SaveCallback() {
                @Override
                public void done(ParseException e) {
                    if (e == null) {
                        PLog.d(ParseFCM.TAG, "FCM token saved to installation");
                    } else {
                        PLog.e(ParseFCM.TAG, "FCM token upload failed", e);
                    }
                }
            });
        }
    }
}
