/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.gcm;

import android.content.Context;
import android.support.annotation.NonNull;

import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.parse.PLog;

/**
 * Entry point into setting up Parse GCM Push
 */
public class ParseGCM {

    static final String TAG = "ParseGCM";

    /**
     * Register your app to start receiving GCM pushes
     *
     * @param context     context
     * @param gcmSenderId the GCM sender ID from the Firebase console
     */
    public static void register(@NonNull Context context, @NonNull String gcmSenderId) {
        //kicks off the background job
        PLog.v(TAG, "Scheduling job to register Parse GCM");
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(context.getApplicationContext()));
        Job job = ParseGCMJobService.createJob(dispatcher, gcmSenderId);
        dispatcher.mustSchedule(job);
    }
}
