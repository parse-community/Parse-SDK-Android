/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.fcm;

import android.support.annotation.CallSuper;

import com.google.firebase.iid.FirebaseInstanceIdService;
import com.parse.ParseInstallation;

/**
 * Assures the {@link ParseInstallation#getDeviceToken()} stays up to date. If you need to do custom things with the token, make sure you extend this
 * class and call super.
 */
public class ParseFirebaseInstanceIdService extends FirebaseInstanceIdService {

    @CallSuper
    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();
        ParseFCM.register(getApplicationContext());
    }
}
