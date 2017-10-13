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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import bolts.Continuation;
import bolts.Task;
import okhttp3.OkHttpClient;

/**
 * The {@code Parse} class contains static functions that handle global configuration for the Parse
 * library.
 */
public class Parse {
  private static final String TAG = "com.parse.Parse";

  /**
   * Represents an opaque configuration for the {@code Parse} SDK configuration.
   */
  public static final class Configuration {
    /**
     * Allows for simple constructing of a {@code Configuration} object.
     */
    public static final class Builder {
      private Context context;
      private String applicationId;
      private String clientKey;
      private String server;
      private boolean localDataStoreEnabled;
      private OkHttpClient.Builder clientBuilder;

      /**
       * Initialize a bulider with a given context.
       * <p>
       * This context will then be passed through to the rest of the Parse SDK for use during
       * initialization.
       * <p>
       * <p/>
       * You may define {@code com.parse.SERVER_URL}, {@code com.parse.APPLICATION_ID} and (optional) {@code com.parse.CLIENT_KEY}
       * {@code meta-data} in your {@code AndroidManifest.xml}:
       * <pre>
       * &lt;manifest ...&gt;
       *
       * ...
       *
       *   &lt;application ...&gt;
       *     &lt;meta-data
       *       android:name="com.parse.SERVER_URL"
       *       android:value="@string/parse_server_url" /&gt;
       *     &lt;meta-data
       *       android:name="com.parse.APPLICATION_ID"
       *       android:value="@string/parse_app_id" /&gt;
       *     &lt;meta-data
       *       android:name="com.parse.CLIENT_KEY"
       *       android:value="@string/parse_client_key" /&gt;
       *
       *       ...
       *
       *   &lt;/application&gt;
       * &lt;/manifest&gt;
       * </pre>
       * <p/>
       * <p>
       * This will cause the values for {@code server}, {@code applicationId} and {@code clientKey} to be set to
       * those defined in your manifest.
       *
       * @param context The active {@link Context} for your application. Cannot be null.
       */
      public Builder(Context context) {
        this.context = context;

        // Yes, our public API states we cannot be null. But for unit tests, it's easier just to
        // support null here.
        if (context != null) {
          Context applicationContext = context.getApplicationContext();
          Bundle metaData = ManifestInfo.getApplicationMetadata(applicationContext);
          if (metaData != null) {
            server(metaData.getString(PARSE_SERVER_URL));
            applicationId = metaData.getString(PARSE_APPLICATION_ID);
            clientKey = metaData.getString(PARSE_CLIENT_KEY);
          }
        }
      }

      /**
       * Set the application id to be used by Parse.
       * <p>
       * This method is only required if you intend to use a different {@code applicationId} than
       * is defined by {@code com.parse.APPLICATION_ID} in your {@code AndroidManifest.xml}.
       *
       * @param applicationId The application id to set.
       * @return The same builder, for easy chaining.
       */
      public Builder applicationId(String applicationId) {
        this.applicationId = applicationId;
        return this;
      }

      /**
       * Set the client key to be used by Parse.
       * <p>
       * This method is only required if you intend to use a different {@code clientKey} than
       * is defined by {@code com.parse.CLIENT_KEY} in your {@code AndroidManifest.xml}.
       *
       * @param clientKey The client key to set.
       * @return The same builder, for easy chaining.
       */
      public Builder clientKey(String clientKey) {
        this.clientKey = clientKey;
        return this;
      }

      /**
       * Set the server URL to be used by Parse.
       *
       * @param server The server URL to set.
       * @return The same builder, for easy chaining.
       */
      public Builder server(String server) {

        // Add an extra trailing slash so that Parse REST commands include
        // the path as part of the server URL (i.e. http://api.myhost.com/parse)
        if (server != null && !server.endsWith("/")) {
          server = server + "/";
        }

        this.server = server;
        return this;
      }

      /**
       * Enable pinning in your application. This must be called before your application can use
       * pinning.
       *
       * @return The same builder, for easy chaining.
       */
      public Builder enableLocalDataStore() {
        localDataStoreEnabled = true;
        return this;
      }

      private Builder setLocalDatastoreEnabled(boolean enabled) {
        localDataStoreEnabled = enabled;
        return this;
      }

      /**
       * Set the {@link okhttp3.OkHttpClient.Builder} to use when communicating with the Parse
       * REST API
       * <p>
       *
       * @param builder The client builder, which will be modified for compatibility
       * @return The same builder, for easy chaining.
       */
      public Builder clientBuilder(OkHttpClient.Builder builder) {
        clientBuilder = builder;
        return this;
      }

      /**
       * Construct this builder into a concrete {@code Configuration} instance.
       *
       * @return A constructed {@code Configuration} object.
       */
      public Configuration build() {
        return new Configuration(this);
      }
    }

    final Context context;
    final String applicationId;
    final String clientKey;
    final String server;
    final boolean localDataStoreEnabled;
    final OkHttpClient.Builder clientBuilder;


    private Configuration(Builder builder) {
      this.context = builder.context;
      this.applicationId = builder.applicationId;
      this.clientKey = builder.clientKey;
      this.server = builder.server;
      this.localDataStoreEnabled = builder.localDataStoreEnabled;
      this.clientBuilder = builder.clientBuilder;
    }
  }

  private static final String PARSE_SERVER_URL = "com.parse.SERVER_URL";
  private static final String PARSE_APPLICATION_ID = "com.parse.APPLICATION_ID";
  private static final String PARSE_CLIENT_KEY = "com.parse.CLIENT_KEY";

  private static final Object MUTEX = new Object();
  static ParseEventuallyQueue eventuallyQueue = null;

  //region LDS

  private static boolean isLocalDatastoreEnabled;
  private static OfflineStore offlineStore;

  /**
   * Enable pinning in your application. This must be called before your application can use
   * pinning. You must invoke {@code enableLocalDatastore(Context)} before
   * {@link #initialize(Context)} :
   * <p/>
   * <pre>
   * public class MyApplication extends Application {
   *   public void onCreate() {
   *     Parse.enableLocalDatastore(this);
   *     Parse.initialize(this);
   *   }
   * }
   * </pre>
   *
   * @param context The active {@link Context} for your application.
   */
  public static void enableLocalDatastore(Context context) {
    if (isInitialized()) {
      throw new IllegalStateException("`Parse#enableLocalDatastore(Context)` must be invoked " +
              "before `Parse#initialize(Context)`");
    }
    isLocalDatastoreEnabled = true;
  }

  static void disableLocalDatastore() {
    setLocalDatastore(null);
    // We need to re-register ParseCurrentInstallationController otherwise it is still offline
    // controller
    ParseCorePlugins.getInstance().reset();
  }

  static OfflineStore getLocalDatastore() {
    return offlineStore;
  }

  static void setLocalDatastore(OfflineStore offlineStore) {
    Parse.isLocalDatastoreEnabled = offlineStore != null;
    Parse.offlineStore = offlineStore;
  }

  public static boolean isLocalDatastoreEnabled() {
    return isLocalDatastoreEnabled;
  }

  //endregion

  /**
   * Authenticates this client as belonging to your application.
   * <p/>
   * You may define {@code com.parse.SERVER_URL}, {@code com.parse.APPLICATION_ID} and (optional) {@code com.parse.CLIENT_KEY}
   * {@code meta-data} in your {@code AndroidManifest.xml}:
   * <pre>
   * &lt;manifest ...&gt;
   *
   * ...
   *
   *   &lt;application ...&gt;
   *     &lt;meta-data
   *       android:name="com.parse.SERVER_URL"
   *       android:value="@string/parse_server_url" /&gt;
   *     &lt;meta-data
   *       android:name="com.parse.APPLICATION_ID"
   *       android:value="@string/parse_app_id" /&gt;
   *     &lt;meta-data
   *       android:name="com.parse.CLIENT_KEY"
   *       android:value="@string/parse_client_key" /&gt;
   *
   *       ...
   *
   *   &lt;/application&gt;
   * &lt;/manifest&gt;
   * </pre>
   * <p/>
   * This must be called before your application can use the Parse library.
   * The recommended way is to put a call to {@code Parse.initialize}
   * in your {@code Application}'s {@code onCreate} method:
   * <p/>
   * <pre>
   * public class MyApplication extends Application {
   *   public void onCreate() {
   *     Parse.initialize(this);
   *   }
   * }
   * </pre>
   *
   * @param context The active {@link Context} for your application.
   */
  public static void initialize(Context context) {
    Configuration.Builder builder = new Configuration.Builder(context);
    if (builder.server == null) {
      throw new RuntimeException("ServerUrl not defined. " +
              "You must provide ServerUrl in AndroidManifest.xml.\n" +
              "<meta-data\n" +
              "    android:name=\"com.parse.SERVER_URL\"\n" +
              "    android:value=\"<Your Server Url>\" />");
    }
    if (builder.applicationId == null) {
      throw new RuntimeException("ApplicationId not defined. " +
              "You must provide ApplicationId in AndroidManifest.xml.\n" +
              "<meta-data\n" +
              "    android:name=\"com.parse.APPLICATION_ID\"\n" +
              "    android:value=\"<Your Application Id>\" />");
    }
    initialize(builder
            .setLocalDatastoreEnabled(isLocalDatastoreEnabled)
            .build()
    );
  }

  /**
   * Authenticates this client as belonging to your application.
   * <p/>
   * This method is only required if you intend to use a different {@code applicationId} or
   * {@code clientKey} than is defined by {@code com.parse.APPLICATION_ID} or
   * {@code com.parse.CLIENT_KEY} in your {@code AndroidManifest.xml}.
   * <p/>
   * This must be called before your
   * application can use the Parse library. The recommended way is to put a call to
   * {@code Parse.initialize} in your {@code Application}'s {@code onCreate} method:
   * <p/>
   * <pre>
   * public class MyApplication extends Application {
   *   public void onCreate() {
   *     Parse.initialize(this, &quot;your application id&quot;, &quot;your client key&quot;);
   *   }
   * }
   * </pre>
   *
   * @param context       The active {@link Context} for your application.
   * @param applicationId The application id provided in the Parse dashboard.
   * @param clientKey     The client key provided in the Parse dashboard.
   */
  public static void initialize(Context context, String applicationId, String clientKey) {
    initialize(new Configuration.Builder(context)
            .applicationId(applicationId)
            .clientKey(clientKey)
            .setLocalDatastoreEnabled(isLocalDatastoreEnabled)
            .build()
    );
  }

  public static void initialize(Configuration configuration) {
    if (isInitialized()) {
      PLog.w(TAG, "Parse is already initialized");
      return;
    }
    // NOTE (richardross): We will need this here, as ParsePlugins uses the return value of
    // isLocalDataStoreEnabled() to perform additional behavior.
    isLocalDatastoreEnabled = configuration.localDataStoreEnabled;

    ParsePlugins.Android.initialize(configuration.context, configuration);

    try {
      ParseRESTCommand.server = new URL(configuration.server);
    } catch (MalformedURLException ex) {
      throw new RuntimeException(ex);
    }

    Context applicationContext = configuration.context.getApplicationContext();

    ParseHttpClient.setKeepAlive(true);
    ParseHttpClient.setMaxConnections(20);

    ParseObject.registerParseSubclasses();

    if (configuration.localDataStoreEnabled) {
      offlineStore = new OfflineStore(configuration.context);
    } else {
      ParseKeyValueCache.initialize(configuration.context);
    }

    // Make sure the data on disk for Parse is for the current
    // application.
    checkCacheApplicationId();
    final Context context = configuration.context;
    Task.callInBackground(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        getEventuallyQueue(context);
        return null;
      }
    });

    ParseFieldOperations.registerDefaultDecoders();

    if (!allParsePushIntentReceiversInternal()) {
      throw new SecurityException("To prevent external tampering to your app's notifications, " +
              "all receivers registered to handle the following actions must have " +
              "their exported attributes set to false: com.parse.push.intent.RECEIVE, " +
              "com.parse.push.intent.OPEN, com.parse.push.intent.DELETE");
    }

    // May need to update GCM registration ID if app version has changed.
    // This also primes current installation.
    PushServiceUtils.initialize().continueWithTask(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> task) throws Exception {
        // Prime current user in the background
        return ParseUser.getCurrentUserAsync().makeVoid();
      }
    }).continueWith(new Continuation<Void, Void>() {
      @Override
      public Void then(Task<Void> task) throws Exception {
        // Prime config in the background
        ParseConfig.getCurrentConfig();
        return null;
      }
    }, Task.BACKGROUND_EXECUTOR);

    dispatchOnParseInitialized();

    // FYI we probably don't want to do this if we ever add other callbacks.
    synchronized (MUTEX_CALLBACKS) {
      Parse.callbacks = null;
    }
  }

  static void destroy() {
    ParseEventuallyQueue queue;
    synchronized (MUTEX) {
      queue = eventuallyQueue;
      eventuallyQueue = null;
    }
    if (queue != null) {
      queue.onDestroy();
    }

    ParseCorePlugins.getInstance().reset();
    ParsePlugins.reset();
  }

  /**
   * @return {@code True} if {@link #initialize} has been called, otherwise {@code false}.
   */
  static boolean isInitialized() {
    return ParsePlugins.get() != null;
  }

  static Context getApplicationContext() {
    checkContext();
    return ParsePlugins.Android.get().applicationContext();
  }

  /**
   * Checks that each of the receivers associated with the three actions defined in
   * ParsePushBroadcastReceiver (ACTION_PUSH_RECEIVE, ACTION_PUSH_OPEN, ACTION_PUSH_DELETE) has
   * their exported attributes set to false. If this is the case for each of the receivers
   * registered in the AndroidManifest.xml or if no receivers are registered (because we will be registering
   * the default implementation of ParsePushBroadcastReceiver in PushService) then true is returned.
   * Note: the reason for iterating through lists, is because you can define different receivers
   * in the manifest that respond to the same intents and both all of the receivers will be triggered.
   * So we want to make sure all them have the exported attribute set to false.
   */
  private static boolean allParsePushIntentReceiversInternal() {
    List<ResolveInfo> intentReceivers = ManifestInfo.getIntentReceivers(
            ParsePushBroadcastReceiver.ACTION_PUSH_RECEIVE,
            ParsePushBroadcastReceiver.ACTION_PUSH_DELETE,
            ParsePushBroadcastReceiver.ACTION_PUSH_OPEN);

    for (ResolveInfo resolveInfo : intentReceivers) {
      if (resolveInfo.activityInfo.exported) {
        return false;
      }
    }
    return true;
  }

  /**
   * @deprecated Please use {@link #getParseCacheDir(String)} or {@link #getParseFilesDir(String)}
   * instead.
   */
  @Deprecated
  static File getParseDir() {
    return ParsePlugins.get().getParseDir();
  }

  static File getParseCacheDir() {
    return ParsePlugins.get().getCacheDir();
  }

  static File getParseCacheDir(String subDir) {
    synchronized (MUTEX) {
      File dir = new File(getParseCacheDir(), subDir);
      if (!dir.exists()) {
        dir.mkdirs();
      }
      return dir;
    }
  }

  static File getParseFilesDir() {
    return ParsePlugins.get().getFilesDir();
  }

  static File getParseFilesDir(String subDir) {
    synchronized (MUTEX) {
      File dir = new File(getParseFilesDir(), subDir);
      if (!dir.exists()) {
        dir.mkdirs();
      }
      return dir;
    }
  }

  /**
   * Verifies that the data stored on disk for Parse was generated using the same application that
   * is running now.
   */
  static void checkCacheApplicationId() {
    synchronized (MUTEX) {
      String applicationId = ParsePlugins.get().applicationId();
      if (applicationId != null) {
        File dir = Parse.getParseCacheDir();

        // Make sure the current version of the cache is for this application id.
        File applicationIdFile = new File(dir, "applicationId");
        if (applicationIdFile.exists()) {
          // Read the file
          boolean matches = false;
          try {
            RandomAccessFile f = new RandomAccessFile(applicationIdFile, "r");
            byte[] bytes = new byte[(int) f.length()];
            f.readFully(bytes);
            f.close();
            String diskApplicationId = new String(bytes, "UTF-8");
            matches = diskApplicationId.equals(applicationId);
          } catch (IOException e) {
            // Hmm, the applicationId file was malformed or something. Assume it
            // doesn't match.
          }

          // The application id has changed, so everything on disk is invalid.
          if (!matches) {
            try {
              ParseFileUtils.deleteDirectory(dir);
            } catch (IOException e) {
              // We're unable to delete the directy...
            }
          }
        }

        // Create the version file if needed.
        applicationIdFile = new File(dir, "applicationId");
        try {
          FileOutputStream out = new FileOutputStream(applicationIdFile);
          out.write(applicationId.getBytes("UTF-8"));
          out.close();
        } catch (IOException e) {
          // Nothing we can really do about it.
        }
      }
    }
  }

  /**
   * Gets the shared command cache object for all ParseObjects. This command cache is used to
   * locally store save commands created by the ParseObject.saveEventually(). When a new
   * ParseCommandCache is instantiated, it will begin running its run loop, which will start by
   * processing any commands already stored in the on-disk queue.
   */
  static ParseEventuallyQueue getEventuallyQueue() {
    Context context = ParsePlugins.Android.get().applicationContext();
    return getEventuallyQueue(context);
  }

  private static ParseEventuallyQueue getEventuallyQueue(Context context) {
    synchronized (MUTEX) {
      boolean isLocalDatastoreEnabled = Parse.isLocalDatastoreEnabled();
      if (eventuallyQueue == null
              || (isLocalDatastoreEnabled && eventuallyQueue instanceof ParseCommandCache)
              || (!isLocalDatastoreEnabled && eventuallyQueue instanceof ParsePinningEventuallyQueue)) {
        checkContext();
        ParseHttpClient httpClient = ParsePlugins.get().restClient();
        eventuallyQueue = isLocalDatastoreEnabled
                ? new ParsePinningEventuallyQueue(context, httpClient)
                : new ParseCommandCache(context, httpClient);

        // We still need to clear out the old command cache even if we're using Pinning in case
        // anything is left over when the user upgraded. Checking number of pending and then
        // initializing should be enough.
        if (isLocalDatastoreEnabled && ParseCommandCache.getPendingCount() > 0) {
          new ParseCommandCache(context, httpClient);
        }
      }
      return eventuallyQueue;
    }
  }

  static void checkInit() {
    if (ParsePlugins.get() == null) {
      throw new RuntimeException("You must call Parse.initialize(Context)"
              + " before using the Parse library.");
    }

    if (ParsePlugins.get().applicationId() == null) {
      throw new RuntimeException("applicationId is null. "
              + "You must call Parse.initialize(Context)"
              + " before using the Parse library.");
    }
  }

  static void checkContext() {
    if (ParsePlugins.Android.get().applicationContext() == null) {
      throw new RuntimeException("applicationContext is null. "
              + "You must call Parse.initialize(Context)"
              + " before using the Parse library.");
    }
  }

  static boolean hasPermission(String permission) {
    return (getApplicationContext().checkCallingOrSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED);
  }

  static void requirePermission(String permission) {
    if (!hasPermission(permission)) {
      throw new IllegalStateException(
              "To use this functionality, add this to your AndroidManifest.xml:\n"
                      + "<uses-permission android:name=\"" + permission + "\" />");
    }
  }

  //region ParseCallbacks
  private static final Object MUTEX_CALLBACKS = new Object();
  private static Set<ParseCallbacks> callbacks = new HashSet<>();

  /**
   * Registers a listener to be called at the completion of {@link #initialize}.
   * <p>
   * Throws {@link java.lang.IllegalStateException} if called after {@link #initialize}.
   *
   * @param listener the listener to register
   */
  static void registerParseCallbacks(ParseCallbacks listener) {
    if (isInitialized()) {
      throw new IllegalStateException(
              "You must register callbacks before Parse.initialize(Context)");
    }

    synchronized (MUTEX_CALLBACKS) {
      if (callbacks == null) {
        return;
      }
      callbacks.add(listener);
    }
  }

  /**
   * Unregisters a listener previously registered with {@link #registerParseCallbacks}.
   *
   * @param listener the listener to register
   */
  static void unregisterParseCallbacks(ParseCallbacks listener) {
    synchronized (MUTEX_CALLBACKS) {
      if (callbacks == null) {
        return;
      }
      callbacks.remove(listener);
    }
  }

  private static void dispatchOnParseInitialized() {
    ParseCallbacks[] callbacks = collectParseCallbacks();
    if (callbacks != null) {
      for (ParseCallbacks callback : callbacks) {
        callback.onParseInitialized();
      }
    }
  }

  private static ParseCallbacks[] collectParseCallbacks() {
    ParseCallbacks[] callbacks;
    synchronized (MUTEX_CALLBACKS) {
      if (Parse.callbacks == null) {
        return null;
      }
      callbacks = new ParseCallbacks[Parse.callbacks.size()];
      if (Parse.callbacks.size() > 0) {
        callbacks = Parse.callbacks.toArray(callbacks);
      }
    }
    return callbacks;
  }

  interface ParseCallbacks {
    void onParseInitialized();
  }

  //endregion

  //region Logging

  public static final int LOG_LEVEL_VERBOSE = Log.VERBOSE;
  public static final int LOG_LEVEL_DEBUG = Log.DEBUG;
  public static final int LOG_LEVEL_INFO = Log.INFO;
  public static final int LOG_LEVEL_WARNING = Log.WARN;
  public static final int LOG_LEVEL_ERROR = Log.ERROR;
  public static final int LOG_LEVEL_NONE = Integer.MAX_VALUE;

  /**
   * Sets the level of logging to display, where each level includes all those below it. The default
   * level is {@link #LOG_LEVEL_NONE}. Please ensure this is set to {@link #LOG_LEVEL_ERROR}
   * or {@link #LOG_LEVEL_NONE} before deploying your app to ensure no sensitive information is
   * logged. The levels are:
   * <ul>
   * <li>{@link #LOG_LEVEL_VERBOSE}</li>
   * <li>{@link #LOG_LEVEL_DEBUG}</li>
   * <li>{@link #LOG_LEVEL_INFO}</li>
   * <li>{@link #LOG_LEVEL_WARNING}</li>
   * <li>{@link #LOG_LEVEL_ERROR}</li>
   * <li>{@link #LOG_LEVEL_NONE}</li>
   * </ul>
   *
   * @param logLevel The level of logcat logging that Parse should do.
   */
  public static void setLogLevel(int logLevel) {
    PLog.setLogLevel(logLevel);
  }

  /**
   * Returns the level of logging that will be displayed.
   */
  public static int getLogLevel() {
    return PLog.getLogLevel();
  }

  //endregion

  // Suppress constructor to prevent subclassing
  private Parse() {
    throw new AssertionError();
  }

  static String externalVersionName() {
    return "a" + ParseObject.VERSION_NAME;
  }
}
