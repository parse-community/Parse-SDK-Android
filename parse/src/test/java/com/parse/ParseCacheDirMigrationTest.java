package com.parse;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.io.File;
import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
public class ParseCacheDirMigrationTest {
    ArrayList<File> writtenFiles = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        ParsePlugins.reset();
        Parse.Configuration configuration =
            new Parse.Configuration.Builder(context).applicationId("1234").build();
        ParsePlugins.initialize(context, configuration);
        writtenFiles.clear();
    }

    @After
    public void tearDown() throws Exception {
        ParsePlugins.reset();
        writtenFiles.clear();
    }

    @Test
    public void manualMigrationBeforeAccessNewCacheAPIs() {
        prepareForMockFilesWriting();
        writtenFiles.addAll(writeSomeMockFiles(false));

        //Run migration manually.
        ParsePlugins.get().runSilentMigration();

        //Check for cache file after migration.
        File cacheDir = ParsePlugins.get().getCacheDir();
        ArrayList<File> migratedCaches = new ArrayList<>();
        ParseFileUtils.getAllNestedFiles(cacheDir.getAbsolutePath(), migratedCaches);

        //Check for files file after migration.
        File filesDir = ParsePlugins.get().getFilesDir();
        ArrayList<File> migratedFiles = new ArrayList<>();
        ParseFileUtils.getAllNestedFiles(filesDir.getAbsolutePath(), migratedFiles);

        //To check migrations result
        int sizeAfterMigration = (migratedCaches.size() + migratedFiles.size());
        int sizeBeforeMigrations = writtenFiles.size();

        assert (cacheDir.exists() && !migratedCaches.isEmpty());
        assert (filesDir.exists() && !migratedFiles.isEmpty());
        assert sizeBeforeMigrations == sizeAfterMigration;
    }

    @Test
    public void autoMigrationBeforeAccessFilesDir() {
        prepareForMockFilesWriting();
        writtenFiles.addAll(writeSomeMockFiles(false));
        ArrayList<File> migratedFiles = new ArrayList<>();
        //Auto migration
        File filesDir = ParsePlugins.get().getFilesDir(true);
        ParseFileUtils.getAllNestedFiles(filesDir.getAbsolutePath(), migratedFiles);
        assert !migratedFiles.isEmpty();
    }

    @Test
    public void autoMigrationBeforeAccessCacheDir() {
        prepareForMockFilesWriting();
        writtenFiles.addAll(writeSomeMockFiles(false));
        ArrayList<File> migratedCaches = new ArrayList<>();
        //Auto migration
        File cacheDir = ParsePlugins.get().getCacheDir(true);
        ParseFileUtils.getAllNestedFiles(cacheDir.getAbsolutePath(), migratedCaches);
        assert !migratedCaches.isEmpty();
    }

    @Test
    public void autoMigrationBeforeAccessCacheOrFilesDirBySolvingFileConflicts() {
        prepareForMockFilesWriting();
        //Set some existing files in new location so that there could be file conflict.
        writtenFiles.addAll(writeSomeMockFiles(true));

        //Auto migration for files
        ArrayList<File> migratedFiles = new ArrayList<>();
        File filesDir = ParsePlugins.get().getFilesDir(true);
        ParseFileUtils.getAllNestedFiles(filesDir.getAbsolutePath(), migratedFiles);

        /*
        Auto migration for caches.
        Although migration already completed when accessed `ParsePlugins.get().getFilesDir(true)` or `ParsePlugins.get().getCacheDir(true)` API.
         */
        ArrayList<File> migratedCaches = new ArrayList<>();
        File cacheDir = ParsePlugins.get().getCacheDir(true);
        ParseFileUtils.getAllNestedFiles(cacheDir.getAbsolutePath(), migratedCaches);

        assert !migratedFiles.isEmpty();
        assert !migratedCaches.isEmpty();
    }

    private void prepareForMockFilesWriting() {
        //Delete `"app_Parse"` dir including nested dir and files.
        ParsePlugins.get().deleteOldParseDirSilently();
        writtenFiles.clear();
        //Create new `"app_Parse"` dir to write some files.
        createFileDir(ParsePlugins.get().getCacheDir());
    }

    private ArrayList<File> writeSomeMockFiles(Boolean checkForExistingFile) {
        ArrayList<File> fileToReturn = new ArrayList<>();
        File oldRef = ParsePlugins.get().getOldParseDir();

        //Writing some config & random files for migration process.
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

        //Write all listed files to the app cache ("app_Parse") directory.
        for (File item : fileToReturn) {
            try {
                ParseFileUtils.writeStringToFile(item, "gger", "UTF-8");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //To create a file conflict scenario during migration by creating an existing file to the new files dir ("*/files/com.parse/*").
        if (checkForExistingFile) {
            try {
                ParseFileUtils.writeStringToFile(new File(ParsePlugins.get().getFilesDir() + "/CommandCache/", "installationId"), "gger", "UTF-8");
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
