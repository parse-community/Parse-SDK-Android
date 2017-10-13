/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.annotation.TargetApi;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A JobService that is triggered by push notifications on Oreo+.
 * Read {@link PushServiceUtils} and {@link PushService} for info and docs.
 * This is already set-up in our own manifest.
 */
@TargetApi(Build.VERSION_CODES.O)
public final class PushServiceApi26 extends JobService {
  private static final String TAG = PushServiceApi26.class.getSimpleName();
  private static final String INTENT_KEY = "intent";
  private static final int JOB_SERVICE_ID = 999;

  static boolean run(Context context, Intent intent) {
    JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    // Execute in the next second.
    Bundle extra = new Bundle(1);
    extra.putParcelable(INTENT_KEY, intent);
    ComponentName component = new ComponentName(context, PushServiceApi26.class);
    int did = scheduler.schedule(new JobInfo.Builder(JOB_SERVICE_ID, component)
        .setMinimumLatency(1L)
        .setOverrideDeadline(1000L)
        .setRequiresCharging(false)
        .setRequiresBatteryNotLow(false)
        .setRequiresStorageNotLow(false)
        .setTransientExtras(extra)
        .build());
    return did == JobScheduler.RESULT_SUCCESS;
  }

  // We delegate the intent to a PushHandler running in a streamlined executor.
  private ExecutorService executor;
  private PushHandler handler;
  private int jobsCount;

  // Our manifest file is OK.
  static boolean isSupported() {
    return true;
  }

  @Override
  public boolean onStartJob(final JobParameters jobParameters) {
    if (ParsePlugins.Android.get() == null) {
      PLog.e(TAG, "The Parse push service cannot start because Parse.initialize "
          + "has not yet been called. If you call Parse.initialize from "
          + "an Activity's onCreate, that call should instead be in the "
          + "Application.onCreate. Be sure your Application class is registered "
          + "in your AndroidManifest.xml with the android:name property of your "
          + "<application> tag.");
      return false;
    }

    final Bundle params = jobParameters.getTransientExtras();
    final Intent intent = params.getParcelable(INTENT_KEY);
    jobsCount++;
    getExecutor().execute(new Runnable() {
      @Override
      public void run() {
        try {
          getHandler().handlePush(intent);
        } finally {
          jobFinished(jobParameters, false);
          jobsCount--;
          if (jobsCount == 0) {
            tearDown();
          }
        }
      }
    });
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters jobParameters) {
    // Something went wrong before jobFinished(). Try rescheduling.
    return true;
  }

  private Executor getExecutor() {
    if (executor == null) executor = Executors.newSingleThreadExecutor();
    return executor;
  }

  private PushHandler getHandler() {
    if (handler == null) handler = PushServiceUtils.createPushHandler();
    return handler;
  }

  private void tearDown() {
    if (executor != null) executor.shutdown();
    executor = null;
    handler = null;
  }
}
