/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.fcm;

import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.firebase.jobdispatcher.RetryStrategy;
import com.google.firebase.iid.FirebaseInstanceId;
import com.parse.PLog;
import com.parse.ParseException;
import com.parse.ParseInstallation;
import com.parse.SaveCallback;

/**
 * Handles saving the FCM token to the {@link ParseInstallation} in the background
 */
public class ParseFirebaseJobService extends JobService {

    private static final String JOB_TAG_UPLOAD_TOKEN = "upload-token";
    private static final String PUSH_TYPE = "gcm"; // Backwards compatability with Parse servers

    static Job createJob(FirebaseJobDispatcher dispatcher) {
        return dispatcher.newJobBuilder()
                .setRecurring(false)
                .setReplaceCurrent(true)
                // retry with exponential backoff
                .setRetryStrategy(RetryStrategy.DEFAULT_EXPONENTIAL)
                .setConstraints(
                        // only run on a network
                        Constraint.ON_ANY_NETWORK
                )
                .setService(ParseFirebaseJobService.class) // the JobService that will be called
                .setTag(JOB_TAG_UPLOAD_TOKEN)        // uniquely identifies the job
                .build();
    }

    @Override
    public boolean onStartJob(final JobParameters job) {
        PLog.d(ParseFCM.TAG, "Updating FCM token");
        ParseInstallation installation = ParseInstallation.getCurrentInstallation();
        String token = FirebaseInstanceId.getInstance().getToken();
        if (installation != null && token != null) {
            installation.setDeviceToken(token);
            //even though this is FCM, calling it gcm will work on the backend
            installation.setPushType(PUSH_TYPE);
            installation.saveInBackground(new SaveCallback() {
                @Override
                public void done(ParseException e) {
                    if (e == null) {
                        PLog.d(ParseFCM.TAG, "FCM token saved to installation");
                        jobFinished(job, false);
                    } else {
                        PLog.e(ParseFCM.TAG, "FCM token upload failed", e);
                        jobFinished(job, true);
                    }
                }
            });
            return true;
        }
        return false; // Answers the question: "Is there still work going on?"
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        return true; // Answers the question: "Should this job be retried?"
    }
}
