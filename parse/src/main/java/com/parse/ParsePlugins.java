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
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;

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
                // add it as the first interceptor
                clientBuilder
                    .interceptors()
                    .add(
                        0,
                        chain -> {
                            Request request = chain.request();
                            Headers.Builder headersBuilder =
                                request.headers()
                                    .newBuilder()
                                    .set(
                                        ParseRESTCommand.HEADER_APPLICATION_ID,
                                        configuration.applicationId)
                                    .set(
                                        ParseRESTCommand
                                            .HEADER_APP_BUILD_VERSION,
                                        String.valueOf(
                                            ManifestInfo.getVersionCode()))
                                    .set(
                                        ParseRESTCommand
                                            .HEADER_APP_DISPLAY_VERSION,
                                        ManifestInfo.getVersionName())
                                    .set(
                                        ParseRESTCommand.HEADER_OS_VERSION,
                                        Build.VERSION.RELEASE)
                                    .set(ParseRESTCommand.USER_AGENT, userAgent());
                            if (request.header(ParseRESTCommand.HEADER_INSTALLATION_ID)
                                == null) {
                                // We can do this synchronously since the caller is already
                                // on a background thread
                                headersBuilder.set(
                                    ParseRESTCommand.HEADER_INSTALLATION_ID,
                                    installationId().get());
                            }
                            // client key can be null with self-hosted Parse Server
                            if (configuration.clientKey != null) {
                                headersBuilder.set(
                                    ParseRESTCommand.HEADER_CLIENT_KEY,
                                    configuration.clientKey);
                            }
                            request =
                                request.newBuilder()
                                    .headers(headersBuilder.build())
                                    .build();
                            return chain.proceed(request);
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
                installationId =
                    new InstallationId(new File(getFilesDir(), INSTALLATION_ID_LOCATION));
            }
            return installationId;
        }
    }

    File getCacheDir() {
        synchronized (lock) {
            return _getCacheDir();
        }
    }

    File getFilesDir() {
        synchronized (lock) {
            return _getFilesDir();
        }
    }

    private File _getFilesDir() {
        if (filesDir == null) {
            filesDir = new File(applicationContext.getFilesDir(), "com.parse");
        }
        return createFileDir(filesDir);
    }

    private File _getCacheDir() {
        if (cacheDir == null) {
            cacheDir = new File(applicationContext.getCacheDir(), "com.parse");
        }
        return createFileDir(cacheDir);
    }

    /*Migration [START]*/

    /**
     * Get cache, file object for Parse.
     *
     * @param useMigration to migrate old data to new locations before returning cache {@link File} object.
     */
    File getCacheDir(Boolean useMigration) {
        synchronized (lock) {
            if (useMigration) {
                runSilentMigration();
            }
            return _getCacheDir();
        }
    }

    /**
     * Get files, file object for Parse.
     *
     * @param useMigration to migrate old data to new locations before returning cache {@link File} object.
     */
    File getFilesDir(Boolean useMigration) {
        synchronized (lock) {
            if (useMigration) {
                runSilentMigration();
            }
            return _getFilesDir();
        }
    }

    /**
     * Start old data migrations to new respective locations ({@link #getFilesDir()}, {@link #getCacheDir()}).
     */
    public void runSilentMigration() {
        ArrayList<File> filesToBeMigrated = new ArrayList<>();
        ParseFileUtils.getAllNestedFiles(
            getOldParseDir().getAbsolutePath(),
            filesToBeMigrated
        );
        runParseDirFilesMigrationToCacheDir(filesToBeMigrated);
    }

    public static String replaceLast(String text, String regex, String replacement) {
        return text.replaceFirst("(?s)" + regex + "(?!.*?" + regex + ")", replacement);
    }

    /**
     * Since there is two new different APIs {@link #getFilesDir()}, {@link #getCacheDir()} for caching.
     * <p></p>
     * - Config related files, that manages by SDK itself should be migrated to {@link #getFilesDir()}`.
     * <p></p>
     * - Others files, that exists in the {@link #getOldParseDir()} should be migrated to {@link #getCacheDir()}.
     * <p></p>
     * To identify the config files from {@link #getOldParseDir()},
     * we are using a hard coded list which defines the SDK config files names.
     *
     * @param allFiles Files that needed to be migrated into new location.
     */
    private void runParseDirFilesMigrationToCacheDir(ArrayList<File> allFiles) {
        if (allFiles.isEmpty()) {
            return;
        }
        boolean useFilesDir = false;
        //Hard coded config file names list.
        ArrayList<String> configFilesToMigrateToFilesDir = new ArrayList<>();
        configFilesToMigrateToFilesDir.add("installationId");
        configFilesToMigrateToFilesDir.add("currentUser");
        configFilesToMigrateToFilesDir.add("currentConfig");
        configFilesToMigrateToFilesDir.add("currentInstallation");
        configFilesToMigrateToFilesDir.add("LocalId");
        configFilesToMigrateToFilesDir.add("pushState");
        //Start migration for each files in `allFiles`.
        for (File itemToMove : allFiles) {
            try {
                for (String configName : configFilesToMigrateToFilesDir) {
                    if (itemToMove.getAbsolutePath().contains(configName)) {
                        useFilesDir = true;
                        break;
                    } else {
                        useFilesDir = false;
                    }
                }
                //Parse the old sub directory name where the file should be moved (new location) by following the old sub directory name.
                String oldSubDir = replaceLast(itemToMove.getAbsolutePath().replace(getOldParseDir().getAbsolutePath(), "").replace("/" + itemToMove.getName(), ""), itemToMove.getName(), "");
                File fileToSave = new File(
                    (useFilesDir ? applicationContext.getFilesDir() : applicationContext.getCacheDir())
                        + "/com.parse/" +
                        oldSubDir,
                    itemToMove.getName());
                //Perform copy operation if file doesn't exist in the new directory.
                if (!fileToSave.exists()) {
                    ParseFileUtils.copyFile(itemToMove, fileToSave);
                } else {
                    Log.d("ParsePlugins", "runParseDirFilesMigrationToCacheDir: itemToMove (File) already exist in the location. So just deleting it.");
                }
                ParseFileUtils.deleteQuietly(itemToMove);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //Check again, if all files has been resolved or not. If yes, delete the old dir "app_Parse".
        deleteOldDirAfterMigration();
    }

    private void deleteOldDirAfterMigration() {
        File oldFileRef = getOldParseDir();
        ArrayList<File> allFiles = new ArrayList<>();
        ParseFileUtils.getAllNestedFiles(oldFileRef.getAbsolutePath(), allFiles);
        if (allFiles.isEmpty()) {
            try {
                ParseFileUtils.deleteDirectory(oldFileRef);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /* Deprecated API has been introduced newly for the migration purpose only. [START] */
    @Deprecated
    File getOldParseDir() {
        return applicationContext.getDir("Parse", Context.MODE_PRIVATE);
    }

    @Deprecated
    public void deleteOldParseDirSilently() {
        try {
            ParseFileUtils.deleteDirectory(getOldParseDir());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /* Deprecated API has been introduced newly for the migration purpose only. [END] */

    /*Migration [END]*/
}
