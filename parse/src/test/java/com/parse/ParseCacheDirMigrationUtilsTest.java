package com.parse;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.ArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ParseCacheDirMigrationUtilsTest {
    ArrayList<File> writtenFiles = new ArrayList<>();
    private ParseCacheDirMigrationUtils utils;

    @Before
    public void setUp() throws Exception {
        utils =
                new ParseCacheDirMigrationUtils(
                        InstrumentationRegistry.getInstrumentation().getContext());
        writtenFiles.clear();
    }

    @After
    public void tearDown() throws Exception {
        writtenFiles.clear();
    }

    @Test
    public void testMigrationOnParseSDKInitialization() {
        prepareForMockFilesWriting();
        writtenFiles.addAll(writeSomeMockFiles(true));
        Parse.Configuration configuration =
                new Parse.Configuration.Builder(
                                InstrumentationRegistry.getInstrumentation().getContext())
                        .applicationId(BuildConfig.LIBRARY_PACKAGE_NAME)
                        .server("https://api.parse.com/1")
                        .enableLocalDataStore()
                        .build();
        Parse.initialize(configuration);
    }

    @Test
    public void testMockMigration() {
        prepareForMockFilesWriting();
        writtenFiles.addAll(writeSomeMockFiles(true));

        // Run migration.
        utils.runMigrations();

        // Check for cache file after migration.
        File cacheDir = InstrumentationRegistry.getInstrumentation().getContext().getCacheDir();
        ArrayList<File> migratedCaches = new ArrayList<>();
        ParseFileUtils.getAllNestedFiles(cacheDir.getAbsolutePath(), migratedCaches);

        // Check for files file after migration.
        File filesDir = InstrumentationRegistry.getInstrumentation().getContext().getFilesDir();
        ArrayList<File> migratedFiles = new ArrayList<>();
        ParseFileUtils.getAllNestedFiles(filesDir.getAbsolutePath(), migratedFiles);

        // To check migrations result
        int sizeAfterMigration = (migratedCaches.size() + migratedFiles.size());
        int sizeBeforeMigrations = writtenFiles.size();

        assert (cacheDir.exists() && !migratedCaches.isEmpty());
        assert (filesDir.exists() && !migratedFiles.isEmpty());
        assert sizeBeforeMigrations == sizeAfterMigration;
    }

    private void prepareForMockFilesWriting() {
        // Delete `"app_Parse"` dir including nested dir and files.
        try {
            ParseFileUtils.deleteDirectory(
                    InstrumentationRegistry.getInstrumentation()
                            .getContext()
                            .getDir("Parse", Context.MODE_PRIVATE));
        } catch (Exception e) {
            e.printStackTrace();
        }
        writtenFiles.clear();
        // Create new `"app_Parse"` dir to write some files.
        createFileDir(InstrumentationRegistry.getInstrumentation().getContext().getCacheDir());
    }

    private ArrayList<File> writeSomeMockFiles(Boolean checkForExistingFile) {
        ArrayList<File> fileToReturn = new ArrayList<>();
        File oldRef =
                InstrumentationRegistry.getInstrumentation()
                        .getContext()
                        .getDir("Parse", Context.MODE_PRIVATE);

        // Writing some config & random files for migration process.
        File config = new File(oldRef + "/config/", "config");
        fileToReturn.add(config);
        File installationId = new File(oldRef + "/CommandCache/", "installationId");
        fileToReturn.add(installationId);
        File currentConfig = new File(oldRef + "/", "currentConfig");
        fileToReturn.add(currentConfig);
        File currentInstallation = new File(oldRef + "/", "currentInstallation");
        fileToReturn.add(currentInstallation);
        File pushState = new File(oldRef + "/push/", "pushState");
        fileToReturn.add(pushState);
        File localId = new File(oldRef + "/LocalId/", "LocalId");
        fileToReturn.add(localId);
        File cache = new File(oldRef + "/testcache/", "cache");
        fileToReturn.add(cache);
        File cache1 = new File(oldRef + "/testcache/", "cache1");
        fileToReturn.add(cache1);
        File cache2 = new File(oldRef + "/testcache/another/", "cache4");
        fileToReturn.add(cache2);
        File user = new File(oldRef + "/user/", "user_config");
        fileToReturn.add(user);

        // Write all listed files to the app cache ("app_Parse") directory.
        for (File item : fileToReturn) {
            try {
                ParseFileUtils.writeStringToFile(item, "gger", "UTF-8");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // To create a file conflict scenario during migration by creating an existing file to the
        // new files dir ("*/files/com.parse/*").
        if (checkForExistingFile) {
            try {
                ParseFileUtils.writeStringToFile(
                        new File(
                                InstrumentationRegistry.getInstrumentation()
                                                .getContext()
                                                .getFilesDir()
                                        + "/com.parse/CommandCache/",
                                "installationId"),
                        "gger",
                        "UTF-8");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return fileToReturn;
    }

    private File createFileDir(File file) {
        if (!file.exists()) {
            if (!file.mkdirs()) {
                return file;
            }
        }
        return file;
    }
}
