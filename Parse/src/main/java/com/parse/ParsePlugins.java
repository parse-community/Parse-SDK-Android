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
import android.net.SSLSessionCache;
import android.os.Build;

import com.parse.http.ParseHttpRequest;
import com.parse.http.ParseHttpResponse;
import com.parse.http.ParseNetworkInterceptor;

import java.io.File;
import java.io.IOException;

/** package */ class ParsePlugins {

  private static final String INSTALLATION_ID_LOCATION = "installationId";

  private static final Object LOCK = new Object();
  private static ParsePlugins instance;

  // TODO(grantland): Move towards a Config/Builder parameter pattern to allow other configurations
  // such as path (disabled for Android), etc.
  /* package */ static void initialize(String applicationId, String clientKey) {
    ParsePlugins.set(new ParsePlugins(applicationId, clientKey));
  }

  /* package for tests */ static void set(ParsePlugins plugins) {
    synchronized (LOCK) {
      if (instance != null) {
        throw new IllegalStateException("ParsePlugins is already initialized");
      }
      instance = plugins;
    }
  }

  /* package */ static ParsePlugins get() {
    synchronized (LOCK) {
      return instance;
    }
  }

  /* package */ static void reset() {
    synchronized (LOCK) {
      instance = null;
    }
  }

  /* package */ final Object lock = new Object();
  private final String applicationId;
  private final String clientKey;

  private ParseHttpClient restClient;
  private InstallationId installationId;

  /* package */ File parseDir;
  /* package */ File cacheDir;
  /* package */ File filesDir;

  private ParsePlugins(String applicationId, String clientKey) {
    this.applicationId = applicationId;
    this.clientKey = clientKey;
  }

  /* package */ String applicationId() {
    return applicationId;
  }

  /* package */ String clientKey() {
    return clientKey;
  }

  /* package */ ParseHttpClient newHttpClient() {
    int socketOperationTimeout = 10 * 1000; // 10 seconds
    return ParseHttpClient.createClient(
        socketOperationTimeout,
        null);
  }

  /* package */ ParseHttpClient restClient() {
    synchronized (lock) {
      if (restClient == null) {
        restClient = newHttpClient();
        restClient.addInternalInterceptor(new ParseNetworkInterceptor() {
          @Override
          public ParseHttpResponse intercept(Chain chain) throws IOException {
            ParseHttpRequest request = chain.getRequest();
            ParseHttpRequest.Builder builder = new ParseHttpRequest.Builder(request)
                .addHeader(ParseRESTCommand.HEADER_APPLICATION_ID, applicationId)
                .addHeader(ParseRESTCommand.HEADER_CLIENT_KEY, clientKey)
                .addHeader(ParseRESTCommand.HEADER_CLIENT_VERSION, Parse.externalVersionName())
                .addHeader(
                    ParseRESTCommand.HEADER_APP_BUILD_VERSION,
                    String.valueOf(ManifestInfo.getVersionCode()))
                .addHeader(
                    ParseRESTCommand.HEADER_APP_DISPLAY_VERSION,
                    ManifestInfo.getVersionName())
                .addHeader(ParseRESTCommand.HEADER_OS_VERSION, Build.VERSION.RELEASE)
                .addHeader(ParseRESTCommand.USER_AGENT, userAgent());

            // Only add the installationId if not already set
            if (request.getHeader(ParseRESTCommand.HEADER_INSTALLATION_ID) == null) {
              // We can do this synchronously since the caller is already in a Task on the
              // NETWORK_EXECUTOR
              builder.addHeader(ParseRESTCommand.HEADER_INSTALLATION_ID, installationId().get());
            }
            return chain.proceed(builder.build());
          }
        });
      }
      return restClient;
    }
  }

  // TODO(grantland): Pass through some system values.
  /* package */ String userAgent() {
    return "Parse Java SDK";
  }

  /* package */ InstallationId installationId() {
    synchronized (lock) {
      if (installationId == null) {
        //noinspection deprecation
        installationId = new InstallationId(new File(getParseDir(), INSTALLATION_ID_LOCATION));
      }
      return installationId;
    }
  }

  @Deprecated
  /* package */ File getParseDir() {
    throw new IllegalStateException("Stub");
  }

  /* package */ File getCacheDir() {
    throw new IllegalStateException("Stub");
  }

  /* package */ File getFilesDir() {
    throw new IllegalStateException("Stub");
  }

  /* package */ static class Android extends ParsePlugins {
    /* package */ static void initialize(Context context, String applicationId, String clientKey) {
      ParsePlugins.set(new Android(context, applicationId, clientKey));
    }

    /* package */ static ParsePlugins.Android get() {
      return (ParsePlugins.Android) ParsePlugins.get();
    }

    private final Context applicationContext;

    private Android(Context context, String applicationId, String clientKey) {
      super(applicationId, clientKey);
      applicationContext = context.getApplicationContext();
    }

    /* package */ Context applicationContext() {
      return applicationContext;
    }

    @Override
    public ParseHttpClient newHttpClient() {
      SSLSessionCache sslSessionCache = new SSLSessionCache(applicationContext);
      int socketOperationTimeout = 10 * 1000; // 10 seconds
      return ParseHttpClient.createClient(
          socketOperationTimeout,
          sslSessionCache);
    }

    @Override
    /* package */ String userAgent() {
      String packageVersion = "unknown";
      try {
        String packageName = applicationContext.getPackageName();
        int versionCode = applicationContext
            .getPackageManager()
            .getPackageInfo(packageName, 0)
            .versionCode;
        packageVersion = packageName + "/" + versionCode;
      } catch (PackageManager.NameNotFoundException e) {
        // Should never happen.
      }
      return "Parse Android SDK " + ParseObject.VERSION_NAME + " (" + packageVersion +
          ") API Level " + Build.VERSION.SDK_INT;
    }

    @Override @SuppressWarnings("deprecation")
    /* package */ File getParseDir() {
      synchronized (lock) {
        if (parseDir == null) {
          parseDir = applicationContext.getDir("Parse", Context.MODE_PRIVATE);
        }
        return createFileDir(parseDir);
      }
    }

    @Override
    /* package */ File getCacheDir() {
      synchronized (lock) {
        if (cacheDir == null) {
          cacheDir = new File(applicationContext.getCacheDir(), "com.parse");
        }
        return createFileDir(cacheDir);
      }
    }

    @Override
    /* package */ File getFilesDir() {
      synchronized (lock) {
        if (filesDir == null) {
          filesDir = new File(applicationContext.getFilesDir(), "com.parse");
        }
        return createFileDir(filesDir);
      }
    }
  }

  private static File createFileDir(File file) {
    if (!file.exists()) {
      if (!file.mkdirs()) {
        return file;
      }
    }
    return file;
  }
}
