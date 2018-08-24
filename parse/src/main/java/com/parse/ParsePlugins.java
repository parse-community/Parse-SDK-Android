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
import android.os.Build;

import java.io.File;
import java.io.IOException;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Public for LiveQuery. You probably don't need access
 */
public class ParsePlugins {

    private static final String INSTALLATION_ID_LOCATION = "installationId";

    private static final Object LOCK = new Object();
    private static ParsePlugins instance;
    final Object lock = new Object();
    private final Parse.Configuration configuration;
    File parseDir;
    File cacheDir;
    File filesDir;
    ParseHttpClient restClient;
    ParseHttpClient fileClient;
    private Context applicationContext;
    private InstallationId installationId;
    private ParsePlugins(Context context, Parse.Configuration configuration) {
        if (context != null) {
            applicationContext = context.getApplicationContext();
        }
        this.configuration = configuration;
    }

    static void initialize(Context context, Parse.Configuration configuration) {
        ParsePlugins.set(new ParsePlugins(context, configuration));
    }

    static void set(ParsePlugins plugins) {
        synchronized (LOCK) {
            if (instance != null) {
                throw new IllegalStateException("ParsePlugins is already initialized");
            }
            instance = plugins;
        }
    }

    public static ParsePlugins get() {
        synchronized (LOCK) {
            return instance;
        }
    }

    static void reset() {
        synchronized (LOCK) {
            instance = null;
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

    public String applicationId() {
        return configuration.applicationId;
    }

    public String clientKey() {
        return configuration.clientKey;
    }

    public String server() {
        return configuration.server;
    }

    Parse.Configuration configuration() {
        return configuration;
    }

    Context applicationContext() {
        return applicationContext;
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

    String userAgent() {
        return "Parse Android SDK API Level " + Build.VERSION.SDK_INT;
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
        synchronized (lock) {
            if (parseDir == null) {
                parseDir = applicationContext.getDir("Parse", Context.MODE_PRIVATE);
            }
            return createFileDir(parseDir);
        }
    }

    File getCacheDir() {
        synchronized (lock) {
            if (cacheDir == null) {
                cacheDir = new File(applicationContext.getCacheDir(), "com.parse");
            }
            return createFileDir(cacheDir);
        }
    }

    File getFilesDir() {
        synchronized (lock) {
            if (filesDir == null) {
                filesDir = new File(applicationContext.getFilesDir(), "com.parse");
            }
            return createFileDir(filesDir);
        }
    }
}
