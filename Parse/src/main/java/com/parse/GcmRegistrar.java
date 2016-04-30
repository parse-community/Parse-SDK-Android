/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.content.Context;
import android.os.Bundle;

import com.google.android.gms.gcm.GcmPubSub;
import com.google.android.gms.iid.InstanceID;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import bolts.Continuation;
import bolts.Task;

/**
 * A class that manages registering for GCM and updating the registration if it is out of date.
 */
/** package */ class GcmRegistrar {
  private static final String TAG = "com.parse.GcmRegistrar";
  private static final String ERROR_EXTRA = "error";

  // Client-side key for parseplatform@gmail.com. See parse/config/gcm.yml for server-side key.
  private static final String PARSE_SENDER_ID = "1076345567071";
  private static final String SENDER_ID_EXTRA = "com.parse.push.gcm_sender_id";

  private static final String FILENAME_DEVICE_TOKEN_LAST_MODIFIED = "deviceTokenLastModified";
  private long localDeviceTokenLastModified;
  private final Object localDeviceTokenLastModifiedMutex = new Object();

  public static GcmRegistrar getInstance() {
    return Singleton.INSTANCE;
  }

  private static class Singleton {
    public static final GcmRegistrar INSTANCE = new GcmRegistrar(Parse.getApplicationContext());
  }

  private static String actualSenderIDFromExtra(Object senderIDExtra) {
    if (!(senderIDExtra instanceof String)) {
      return null;
    }

    String senderID = (String)senderIDExtra;
    if (!senderID.startsWith("id:")) {
      return null;
    }

    String[] splitIds = senderID.split(",");
    splitIds[0] = splitIds[0].substring(3);
    if (splitIds.length > 1) {
      PLog.w(TAG, "Received registration for multiple sender IDs, which is no longer supported. " +
      "Will only use the first sender ID (" + splitIds[0] + ")");
    }

    return splitIds[0];
  }

  private final Object lock = new Object();
  private Context context = null;

  // This a package-level constructor for unit testing only. Otherwise, use getInstance().
  /* package */ GcmRegistrar(Context context) {
    this.context = context;
  }

  /**
   * Does nothing if the client already has a valid GCM registration id. Otherwise, sends out a
   * GCM registration request and saves the resulting registration id to the server via
   * ParseInstallation.
   */
  public Task<Void> registerAsync() {
    if (ManifestInfo.getPushType() != PushType.GCM) {
      return Task.forResult(null);
    }
    synchronized (lock) {
      /*
       * If we don't yet have a device token, mark this installation as wanting to use GCM by
       * setting its pushType to GCM. If the registration does not succeed (because the device
       * is offline, for instance), then update() will re-register for a GCM device token at
       * next app initialize time.
       */
      final ParseInstallation installation = ParseInstallation.getCurrentInstallation();
      // Check whether we need to send registration request, if installation does not
      // have device token or local device token is stale, we need to send request.
      Task<Boolean> checkTask = installation.getDeviceToken() == null
          ? Task.forResult(true)
          : isLocalDeviceTokenStaleAsync();
      return checkTask.onSuccessTask(new Continuation<Boolean, Task<Void>>() {
        @Override
        public Task<Void> then(Task<Boolean> task) throws Exception {
          if (!task.getResult()) {
            return Task.forResult(null);
          }
          if (installation.getPushType() != PushType.GCM) {
            installation.setPushType(PushType.GCM);
          }
          // We do not need to wait sendRegistrationRequestAsync, since this task will finish
          // after we get the response from GCM, if we wait for this task, it will block our test.
          sendRegistrationRequestAsync();
          return Task.forResult(null);
        }
      });
    }
  }

  private String getSenderID() {
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
    if (metaData != null) {
      Object senderIDExtra = metaData.get(SENDER_ID_EXTRA);

      if (senderIDExtra != null) {
        String senderID = actualSenderIDFromExtra(senderIDExtra);

        if (senderID != null) {
          return senderID;
        } else {
          PLog.e(TAG, "Found " + SENDER_ID_EXTRA + " <meta-data> element with value \"" +
                  senderIDExtra.toString() + "\", but the value is missing the expected \"id:\" " +
                  "prefix.");
        }
      }
    }

    return PARSE_SENDER_ID;
  }

  boolean requesting = false;
  /** package protected so the GCMService can force a refresh when it gets a notice that the
   * InstanceID was invalidated
   */
  Task<Void> sendRegistrationRequestAsync() {
    synchronized (lock) {
      if (requesting) {
        return Task.forResult(null);
      }
      requesting = true;

      return Task.callInBackground(new Callable<String>() {
        @Override
        public String call() throws Exception {
          // The InstanceID library already handles backoffs and retries, but I've seen the overall
          // process throw a timeout exception. This is just a minor defense in depth against
          // transient issues.
          Exception lastException = null;
          String senderID = getSenderID();
          for (int attempt = 0; attempt < 3; attempt++) {
            try {
              return InstanceID.getInstance(context).getToken(senderID, "GCM");
            } catch (Exception e) {
              lastException = e;
            }
          }
          throw lastException;
        }
      }).continueWith(new Continuation<String, Void>() {
        @Override
        public Void then(Task<String> task) {
          Exception e = task.getError();
          if (e != null) {
            PLog.e(TAG, "Got error when trying to register for GCM push", e);
          } else {
            setGCMRegistrationId(task.getResult());
          }

          synchronized (lock) {
            requesting = false;
          }

          return null;
        }
      });
    }
  }

  /**
   * On older versions of Android that only support GCM v3, the GCM libraries will require the
   * com.google.android.c2dm.intent.REGISTRATION to be handled by a service, which gets trampolined
   * back here. On newer versions of Android which support GCM v3, the device ID is generated by
   * the InstanceID. The InstanceID is easy to fetch and is relatively stable but can be changed.
   * Developers are advised to handle the com.google.android.gms.iid.InstanceID intent and re-fetch
   * the InstanceID and tokens.
   */
  public Task<Void> setGCMRegistrationId(final String registrationId) {
    List<Task<Void>> tasks = new ArrayList<>();

    if (registrationId != null && registrationId.length() > 0) {
      PLog.v(TAG, "Received deviceToken <" + registrationId + "> from GCM.");

      final ParseInstallation installation = ParseInstallation.getCurrentInstallation();
      // Compare the new deviceToken with the old deviceToken, we only update the
      // deviceToken if the new one is different from the old one. This does not follow google
      // guide strictly. But we find most of the time if user just update the app, the
      // registrationId does not change so there is no need to save it again.
      if (!registrationId.equals(installation.getDeviceToken())) {
        boolean wasAlreadyV4 = installation.isUsingGCMv4();

        installation.setPushType(PushType.GCM);
        installation.setDeviceToken(registrationId);

        // GCM v4 has a built-in pubsub concept that is redundant with push to channels. It's
        // cheaper and dramatically faster to use push to a GCM topic than to use the standard
        // mongo pipeline, so we backfill current subscriptions.
        if (installation.isUsingGCMv4() && !wasAlreadyV4) {
          List<String> channels = installation.getList(ParseInstallation.KEY_CHANNELS);
          tasks.add(subscribeToGCMTopics(channels));
        }

        // Old versions of the SDK would mint tokens that were reachable from the Parse Push servers
        // but used the Parse Sender ID. In the transition to GCM v4 we choose to submit a token
        // works with the Parse sender ID _or_ the developer sender ID, but not both (because these
        // become two tokens in GCM v4 and the server would need breaking changes to handle this).
        // To keep Parse.com push working correctly we need to pin this token to the developer's
        // sender ID.
        String senderID = getSenderID();
        if (senderID != null && senderID != PARSE_SENDER_ID) {
          installation.put("GCMSenderId", senderID);
        }

        if (installation.getPushType() != PushType.GCM) {
          installation.setPushType(PushType.GCM);
        }
        tasks.add(installation.saveInBackground());
      }
      // We need to update the last modified even the deviceToken is the same. Otherwise when the
      // app is opened again, isDeviceTokenStale() will always return false so we will send
      // request to GCM every time.
      tasks.add(updateLocalDeviceTokenLastModifiedAsync());

    }
    return Task.whenAll(tasks);
  }

  /** package for tests */ Task<Boolean> isLocalDeviceTokenStaleAsync() {
    return getLocalDeviceTokenLastModifiedAsync().onSuccessTask(new Continuation<Long, Task<Boolean>>() {
      @Override
      public Task<Boolean> then(Task<Long> task) throws Exception {
        long localDeviceTokenLastModified = task.getResult();
        return Task.forResult(localDeviceTokenLastModified != ManifestInfo.getLastModified());
      }
    });
  }

  private static final String GCM_TOPIC_PATTERN = "^[0-9a-zA-Z-_.~%]{1,900}$";
  private static final String GCM_TOPIC_PREFIX = "/topics/";

  /* Used during channel subscription to keep GCM topics in sync. When a device first upgrades to
   * GCM v4 it sends all prior channels to backfill GCM.
   */
  Task<Void> subscribeToGCMTopics(final List<String> channels) {
    final ParseInstallation installation = ParseInstallation.getCurrentInstallation();
    if (!installation.isUsingGCMv4()) {
      return Task.forResult(null);
    }

    return Task.callInBackground(new Callable<GcmPubSub>() {
      @Override
      public GcmPubSub call() throws Exception {
        return GcmPubSub.getInstance(context);
      }
    }).continueWithTask(new Continuation<GcmPubSub, Task<Void>>() {
      public Task<Void> then(Task<GcmPubSub> task) {
        final GcmPubSub pubSub = task.getResult();
        final String registrationId = installation.getDeviceToken();
        List<String> channels = installation.getList(ParseInstallation.KEY_CHANNELS);
        List<Task<Void>> registrations = new ArrayList<Task<Void>>();

        for (String channel: channels) {
          if (!channel.matches(GCM_TOPIC_PATTERN)) {
            PLog.w(TAG, "Cannot subscribe channel " + channel + " as a GCM topic because it is an" +
                    "invalid GCM topic name");
            continue;
          }

          final String topic = GCM_TOPIC_PREFIX + channel;
          Task registration = Task.callInBackground(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
              pubSub.subscribe(registrationId, topic, null /* extras */);
              return null;
            }
          });
          registrations.add(registration);
        }

        return Task.whenAll(registrations);
      }
    });
  }

  Task<Void> unsubscribeFromGCMTopic(final String channel) {
    final ParseInstallation installation = ParseInstallation.getCurrentInstallation();
    if (!installation.isUsingGCMv4() || !channel.matches(GCM_TOPIC_PATTERN)) {
      return Task.forResult(null);
    }

    return Task.callInBackground(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        GcmPubSub pubsub = GcmPubSub.getInstance(context);
        String topic = GCM_TOPIC_PREFIX + channel;
        pubsub.unsubscribe(installation.getDeviceToken(), topic);
        return null;
      }
    });
  }

  /** package for tests */ Task<Void> updateLocalDeviceTokenLastModifiedAsync() {
    return Task.call(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        synchronized (localDeviceTokenLastModifiedMutex) {
          localDeviceTokenLastModified = ManifestInfo.getLastModified();
          final String localDeviceTokenLastModifiedStr =
              String.valueOf(localDeviceTokenLastModified);
          try {
            ParseFileUtils.writeStringToFile(getLocalDeviceTokenLastModifiedFile(),
                localDeviceTokenLastModifiedStr, "UTF-8");
          } catch (IOException e) {
            // do nothing
          }
        }
        return null;
      }
    }, Task.BACKGROUND_EXECUTOR);
  }

  private Task<Long> getLocalDeviceTokenLastModifiedAsync() {
    return Task.call(new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        synchronized (localDeviceTokenLastModifiedMutex) {
          if (localDeviceTokenLastModified == 0) {
            try {
              String localDeviceTokenLastModifiedStr = ParseFileUtils.readFileToString(
                  getLocalDeviceTokenLastModifiedFile(), "UTF-8");
              localDeviceTokenLastModified = Long.valueOf(localDeviceTokenLastModifiedStr);
            } catch (IOException e) {
              localDeviceTokenLastModified = 0;
            }
          }
          return localDeviceTokenLastModified;
        }
      }
    }, Task.BACKGROUND_EXECUTOR);
  }

  /** package for tests */ static File getLocalDeviceTokenLastModifiedFile() {
    File dir = Parse.getParseCacheDir("GCMRegistrar");
    return new File(dir, FILENAME_DEVICE_TOKEN_LAST_MODIFIED);
  }

  /** package for tests */ static void deleteLocalDeviceTokenLastModifiedFile() {
    ParseFileUtils.deleteQuietly(getLocalDeviceTokenLastModifiedFile());
  }
}
