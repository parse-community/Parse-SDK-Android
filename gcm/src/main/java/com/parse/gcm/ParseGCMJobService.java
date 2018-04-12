/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.gcm;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.parse.PLog;
import com.parse.ParseInstallation;

import java.util.concurrent.Callable;

import bolts.Task;

/**
 * Handles saving the GCM token to the Parse Installation
 */
public class ParseGCMJobService extends JobService {

    @Override
    public boolean onStartJob(final JobParameters job) {
        PLog.v(ParseGCM.TAG, "Updating GCM token");

        Task.callInBackground(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try {
                    InstanceID instanceID = InstanceID.getInstance(getApplicationContext());
                    String senderId = ParseGCM.gcmSenderFromManifest(getApplicationContext());
                    String token = instanceID.getToken(senderId,
                            GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
                    ParseInstallation installation = ParseInstallation.getCurrentInstallation();
                    installation.setDeviceToken(token);
                    //even though this is FCM, calling it gcm will work on the backend
                    installation.setPushType("gcm");
                    installation.save();
                    PLog.v(ParseGCM.TAG, "GCM registration success");
                } catch (Exception e) {
                    PLog.e(ParseGCM.TAG, "GCM registration failed", e);
                    jobFinished(job, true);
                }
                return null;
            }
        });
        return true; // Answers the question: "Is there still work going on?"
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        return true; // Answers the question: "Should this job be retried?"
    }
}
