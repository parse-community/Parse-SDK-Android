/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.gcm;

import android.os.Bundle;

import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.firebase.jobdispatcher.RetryStrategy;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.parse.PLog;
import com.parse.ParseInstallation;

import java.util.concurrent.Callable;

import com.parse.boltsinternal.Task;

/**
 * Handles saving the GCM token to the Parse Installation
 */
public class ParseGCMJobService extends JobService {

    private static final String JOB_TAG_REGISTER = "register";
    private static final String KEY_GCM_SENDER_ID = "gcm_sender_id";
    private static final String PUSH_TYPE = "gcm";

    static Job createJob(FirebaseJobDispatcher dispatcher, String gcmSenderId) {
        Bundle extras = new Bundle();
        extras.putString(KEY_GCM_SENDER_ID, gcmSenderId);
        return dispatcher.newJobBuilder()
                .setExtras(extras)
                .setRecurring(false)
                .setReplaceCurrent(true)
                // retry with exponential backoff
                .setRetryStrategy(RetryStrategy.DEFAULT_EXPONENTIAL)
                .setConstraints(
                        // only run on a network
                        Constraint.ON_ANY_NETWORK
                )
                .setService(ParseGCMJobService.class) // the JobService that will be called
                .setTag(JOB_TAG_REGISTER)        // uniquely identifies the job
                .build();
    }

    @Override
    public boolean onStartJob(final JobParameters job) {
        PLog.d(ParseGCM.TAG, "Updating GCM token");

        Task.callInBackground(new Callable<Void>() {
            @Override
            public Void call() {
                try {
                    InstanceID instanceID = InstanceID.getInstance(getApplicationContext());
                    String senderId = job.getExtras().getString(KEY_GCM_SENDER_ID);
                    String token = instanceID.getToken(senderId,
                            GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
                    ParseInstallation installation = ParseInstallation.getCurrentInstallation();
                    installation.setDeviceToken(token);
                    //even though this is FCM, calling it gcm will work on the backend
                    installation.setPushType(PUSH_TYPE);
                    installation.save();
                    PLog.d(ParseGCM.TAG, "GCM registration success");
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
