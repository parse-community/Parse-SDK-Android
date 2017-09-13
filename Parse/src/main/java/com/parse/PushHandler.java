/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import org.json.JSONObject;

import java.util.List;

import bolts.Task;

/**
 * An interface for for handling push payloads (or any similar events, e.g. registration)
 * that woke up the {@link com.parse.PushService}.
 * Each subclass represent a certain {@link com.parse.PushType}.
 *
 * These classes are short-lived, instantiated at the moment of handling a push payload
 * or initializing. They should not be 'stateful' in this sense.
 */
/** package */ interface PushHandler {

  // TODO: let someone extend this somehow if we want to publicize handlers
  class Factory {
    static PushHandler create(PushType type) {
      switch (type) {
        case GCM: return new GcmPushHandler();
        case NONE: return new FallbackHandler();
      }
      return null;
    }
  }

  enum SupportLevel {
    /*
     * Manifest has all required and optional declarations necessary to support this push service.
     */
    SUPPORTED,

    /*
     * Manifest has all required declarations to support this push service, but is missing some
     * optional declarations.
     */
    MISSING_OPTIONAL_DECLARATIONS,

    /*
     * Manifest doesn't have enough required declarations to support this push service.
     */
    MISSING_REQUIRED_DECLARATIONS
  }


  /**
   * Whether this push handler is supported by the current device and manifest configuration.
   * Implementors can parse the manifest file using utilities in {@link ManifestInfo}.
   * @return true if supported
   */
  @NonNull
  SupportLevel isSupported();

  /**
   * Returns a warning message to be shown in logs, depending on the support level returned
   * by {@link #isSupported()}. Called on the same instance.
   */
  @Nullable
  String getWarningMessage(SupportLevel level);

  /**
   * If this handler is the default handler for this device,
   * initialize is called to let it set up things, launch registrations intents,
   * or whatever else is needed.
   *
   * This method is also responsible to update the current {@link ParseInstallation}
   * fields to let parse server work (e.g. device token, push type).
   *
   * @return a task completing when init completed
   */
  Task<Void> initialize();

  /**
   * Handle a raw intent.
   * This is called in a background thread so can be synchronous.
   * Handlers can do checks over the intent and then dispatch the push notification to
   * {@link ParsePushBroadcastReceiver}, by calling
   * {@link PushRouter#handlePush(String, String, String, JSONObject)}.
   */
  @WorkerThread
  void handlePush(Intent intent);


  class FallbackHandler implements PushHandler {

    private FallbackHandler() {};

    @Nullable
    @Override
    public String getWarningMessage(SupportLevel level) {
      return null;
    }

    @NonNull
    @Override
    public SupportLevel isSupported() {
      return SupportLevel.SUPPORTED;
    }

    @Override
    public Task<Void> initialize() {
      return Task.forResult(null);
    }

    @Override
    public void handlePush(Intent intent) {}
  }
}
