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
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.RetryStrategy;
import com.parse.ManifestInfo;
import com.parse.PLog;

/**
 * Entry point into setting up Parse GCM Push
 */
public class ParseGCM {

    private static final String SENDER_ID_EXTRA = "com.parse.push.gcm_sender_id";

    static final String TAG = "ParseGCM";

    private static final String JOB_TAG_REGISTER = "register";

    /**
     * Register your app to start receiving GCM pushes
     *
     * @param context context
     */
    public static void register(Context context) {
        //kicks off the background job
        PLog.v(TAG, "Scheduling job to register Parse GCM");
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(context.getApplicationContext()));
        Job job = dispatcher.newJobBuilder()
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

        dispatcher.mustSchedule(job);
    }

    @Nullable
    static String gcmSenderFromManifest(Context context) {
        // Look for an element like this as a child of the <application> element:
        //
        //   <meta-data android:name="com.parse.push.gcm_sender_id"
        //              android:value="id:567327206255" />
        //
        // The reason why the "id:" prefix is necessary is because Android treats any metadata value
        // that is a string of digits as an integer. So the call to Bundle.getString() will actually
        // return null for `android:value="567327206255"`. Additionally, Bundle.getInteger() returns
        // a 32-bit integer. For `android:value="567327206255"`, this returns a truncated integer
        // because 567327206255 is larger than the largest 32-bit integer.
        Bundle metaData = ManifestInfo.getApplicationMetadata(context);
        String senderID = null;

        if (metaData != null) {
            Object senderIDExtra = metaData.get(SENDER_ID_EXTRA);

            if (senderIDExtra != null) {
                senderID = actualSenderIDFromExtra(senderIDExtra);

                if (senderID == null) {
                    PLog.e(TAG, "Found " + SENDER_ID_EXTRA + " <meta-data> element with value \"" +
                            senderIDExtra.toString() + "\", but the value is missing the expected \"id:\" " +
                            "prefix.");
                    return null;
                }
            }
        }

        if (senderID == null) {
            PLog.e(TAG, "You must provide " + SENDER_ID_EXTRA + " in your AndroidManifest.xml\n" +
                    "Make sure to prefix with the value with id:\n\n" +
                    "<meta-data\n" +
                    "    android:name=\"com.parse.push.gcm_sender_id\"\n" +
                    "    android:value=\"id:<YOUR_GCM_SENDER_ID>\" />");
            return null;
        }
        return senderID;
    }

    private static String actualSenderIDFromExtra(Object senderIDExtra) {
        if (!(senderIDExtra instanceof String)) {
            return null;
        }

        String senderID = (String) senderIDExtra;
        if (!senderID.startsWith("id:")) {
            return null;
        }

        return senderID.substring(3);
    }
}
