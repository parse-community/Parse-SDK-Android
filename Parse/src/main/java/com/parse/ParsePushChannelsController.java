/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.Collections;
import java.util.List;

import bolts.Continuation;
import bolts.Task;

/** package */ class ParsePushChannelsController {
  private static final String TAG = "com.parse.ParsePushChannelsController";

  private static ParseCurrentInstallationController getCurrentInstallationController() {
    return ParseCorePlugins.getInstance().getCurrentInstallationController();
  }

  public Task<Void> subscribeInBackground(final String channel) {
    checkManifestAndLogErrorIfNecessary();
    if (channel == null) {
      throw new IllegalArgumentException("Can't subscribe to null channel.");
    }
    return getCurrentInstallationController().getAsync().onSuccessTask(new Continuation<ParseInstallation, Task<Void>>() {
      @Override
      public Task<Void> then(Task<ParseInstallation> task) throws Exception {
        ParseInstallation installation = task.getResult();
        List<String> channels = installation.getList(ParseInstallation.KEY_CHANNELS);
        if (channels == null
            || installation.isDirty(ParseInstallation.KEY_CHANNELS)
            || !channels.contains(channel)) {
          installation.addUnique(ParseInstallation.KEY_CHANNELS, channel);
          return installation.saveInBackground();
        } else {
          return Task.forResult(null);
        }
      }
    });
  }

  public Task<Void> unsubscribeInBackground(final String channel) {
    checkManifestAndLogErrorIfNecessary();
    if (channel == null) {
      throw new IllegalArgumentException("Can't unsubscribe from null channel.");
    }
    return getCurrentInstallationController().getAsync().onSuccessTask(new Continuation<ParseInstallation, Task<Void>>() {
      @Override
      public Task<Void> then(Task<ParseInstallation> task) throws Exception {
        ParseInstallation installation = task.getResult();
        List<String> channels = installation.getList(ParseInstallation.KEY_CHANNELS);
        if (channels != null && channels.contains(channel)) {
          installation.removeAll(
              ParseInstallation.KEY_CHANNELS, Collections.singletonList(channel));
          return installation.saveInBackground();
        } else {
          return Task.forResult(null);
        }
      }
    });
  }

  private static boolean loggedManifestError = false;
  private static void checkManifestAndLogErrorIfNecessary() {
    if (!loggedManifestError && ManifestInfo.getPushType() == PushType.NONE) {
      loggedManifestError = true;
      PLog.e(TAG, "Tried to subscribe or unsubscribe from a channel, but push is not enabled " +
          "correctly. " + ManifestInfo.getPushDisabledMessage());
    }
  }
}
