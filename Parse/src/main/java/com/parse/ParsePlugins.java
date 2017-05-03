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
import android.os.Build;

import java.io.File;
import java.io.IOException;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

class ParsePlugins {

    private static final String INSTALLATION_ID_LOCATION = "installationId";

    private static final Object LOCK = new Object();
    private static ParsePlugins instance;

    // TODO(grantland): Move towards a Config/Builder parameter pattern to allow other configurations
    // such as path (disabled for Android), etc.
    static void initialize(Parse.Configuration configuration) {
        ParsePlugins.set(new ParsePlugins(configuration));
    }

    static void set(ParsePlugins plugins) {
        synchronized (LOCK) {
            if (instance != null) {
                throw new IllegalStateException("ParsePlugins is already initialized");
            }
            instance = plugins;
        }
    }

    static ParsePlugins get() {
        synchronized (LOCK) {
            return instance;
        }
    }

    static void reset() {
        synchronized (LOCK) {
            instance = null;
        }
    }

    final Object lock = new Object();
    private final Parse.Configuration configuration;

    private InstallationId installationId;

    File parseDir;
    File cacheDir;
    File filesDir;

    ParseHttpClient restClient;
    ParseHttpClient fileClient;

    private ParsePlugins(Parse.Configuration configuration) {
        this.configuration = configuration;
    }

    String applicationId() {
        return configuration.applicationId;
    }

    String clientKey() {
        return configuration.clientKey;
    }

    ParseHttpClient fileClient() {
        synchronized (lock) {
            if (fileClient == null) {
                fileClient = ParseHttpClient.createClient(configuration.clientBuilder);
            }
            return fileClient;
        }
    }

    ParseHttpClient restClient() {
        synchronized (lock) {
            if (restClient == null) {
                OkHttpClient.Builder clientBuilder = configuration.clientBuilder;
                if (clientBuilder == null) {
                    clientBuilder = new OkHttpClient.Builder();
                }
                //add it as the first interceptor
                clientBuilder.interceptors().add(0, new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        Headers.Builder headersBuilder = request.headers().newBuilder()
                                .set(ParseRESTCommand.HEADER_APPLICATION_ID, configuration.applicationId)
                                .set(ParseRESTCommand.HEADER_CLIENT_VERSION, Parse.externalVersionName())
                                .set(ParseRESTCommand.HEADER_APP_BUILD_VERSION,
                                        String.valueOf(ManifestInfo.getVersionCode()))
                                .set(ParseRESTCommand.HEADER_APP_DISPLAY_VERSION,
                                        ManifestInfo.getVersionName())
                                .set(ParseRESTCommand.HEADER_OS_VERSION, Build.VERSION.RELEASE)
                                .set(ParseRESTCommand.USER_AGENT, userAgent());
                        if (request.header(ParseRESTCommand.HEADER_INSTALLATION_ID) == null) {
                            // We can do this synchronously since the caller is already on a background thread
                            headersBuilder.set(ParseRESTCommand.HEADER_INSTALLATION_ID, installationId().get());
                        }
                        // client key can be null with self-hosted Parse Server
                        if (configuration.clientKey != null) {
                            headersBuilder.set(ParseRESTCommand.HEADER_CLIENT_KEY, configuration.clientKey);
                        }
                        request = request.newBuilder()
                                .headers(headersBuilder.build())
                                .build();
                        return chain.proceed(request);
                    }
                });
                restClient = ParseHttpClient.createClient(clientBuilder);
            }
            return restClient;
        }
    }

    // TODO(grantland): Pass through some system values.
    String userAgent() {
        return "Parse Java SDK";
    }

    InstallationId installationId() {
        synchronized (lock) {
            if (installationId == null) {
                //noinspection deprecation
                installationId = new InstallationId(new File(getParseDir(), INSTALLATION_ID_LOCATION));
            }
            return installationId;
        }
    }

    @Deprecated
    File getParseDir() {
        throw new IllegalStateException("Stub");
    }

    File getCacheDir() {
        throw new IllegalStateException("Stub");
    }

    File getFilesDir() {
        throw new IllegalStateException("Stub");
    }

    static class Android extends ParsePlugins {

        static void initialize(Context context, Parse.Configuration configuration) {
            ParsePlugins.set(new Android(context, configuration));
        }

        static ParsePlugins.Android get() {
            return (ParsePlugins.Android) ParsePlugins.get();
        }

        private final Context applicationContext;

        private Android(Context context, Parse.Configuration configuration) {
            super(configuration);
            applicationContext = context.getApplicationContext();
        }

        Context applicationContext() {
            return applicationContext;
        }

        @Override
        String userAgent() {
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

        @Override
        @SuppressWarnings("deprecation")
        File getParseDir() {
            synchronized (lock) {
                if (parseDir == null) {
                    parseDir = applicationContext.getDir("Parse", Context.MODE_PRIVATE);
                }
                return createFileDir(parseDir);
            }
        }

        @Override
        File getCacheDir() {
            synchronized (lock) {
                if (cacheDir == null) {
                    cacheDir = new File(applicationContext.getCacheDir(), "com.parse");
                }
                return createFileDir(cacheDir);
            }
        }

        @Override
        File getFilesDir() {
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
