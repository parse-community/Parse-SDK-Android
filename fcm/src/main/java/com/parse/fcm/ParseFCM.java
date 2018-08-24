/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.fcm;

import android.content.Context;

import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.parse.PLog;

public class ParseFCM {

    static final String TAG = "ParseFCM";

    /**
     * You can call this manually if you are overriding the {@link com.google.firebase.iid.FirebaseInstanceIdService}
     *
     * @param context context
     */
    public static void register(Context context) {
        //kicks off the background job
        PLog.d(TAG, "Scheduling job to register Parse FCM");
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(context.getApplicationContext()));
        Job job = ParseFirebaseJobService.createJob(dispatcher);
        dispatcher.mustSchedule(job);
    }
}
