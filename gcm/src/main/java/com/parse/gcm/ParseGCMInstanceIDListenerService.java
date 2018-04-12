package com.parse.gcm;

import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.RetryStrategy;
import com.google.android.gms.iid.InstanceIDListenerService;

/**
 * Listens for GCM token refreshes and kicks off a background job to save the token
 */
public class ParseGCMInstanceIDListenerService extends InstanceIDListenerService {

    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();

        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(getApplicationContext()));
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
                .setTag("initialize")        // uniquely identifies the job
                .build();

        dispatcher.mustSchedule(job);
    }
}
