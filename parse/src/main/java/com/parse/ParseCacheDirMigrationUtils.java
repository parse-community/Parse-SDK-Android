package com.parse;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;

/**
 * The {@code ParseMigrationUtils} class perform caching dir migration operation for {@code Parse} SDK.
 */
public class ParseCacheDirMigrationUtils {
    private final String TAG = this.getClass().getName();
    private final Object lock = new Object();
    private final Context context;

    protected ParseCacheDirMigrationUtils(Context context) {
        this.context = context;
    }

    /*Start old data migrations to new respective locations ("/files/com.parse/", "/cache/com.parse/")*/
    protected void runMigrations() {
        synchronized (lock) {
            runSilentMigration(context);
        }
    }

    private void runSilentMigration(Context context) {
        ArrayList<File> filesToBeMigrated = new ArrayList<>();
        ParseFileUtils.getAllNestedFiles(
            getOldParseDir(context).getAbsolutePath(),
            filesToBeMigrated
        );
        if (filesToBeMigrated.isEmpty()) {
            return;
        }
        boolean useFilesDir = false;
        //Hard coded config file names list.
        String[] configNamesList = {"installationId", "currentUser", "currentConfig", "currentInstallation", "LocalId", "pushState"};
        //Start migration for each files in `allFiles`.
        for (File itemToMove : filesToBeMigrated) {
            try {
                for (String configName : configNamesList) {
                    if (itemToMove.getAbsolutePath().contains(configName)) {
                        useFilesDir = true;
                        break;
                    } else {
                        useFilesDir = false;
                    }
                }
                File fileToSave = new File(
                    (useFilesDir ? context.getFilesDir() : context.getCacheDir())
                        + "/com.parse/" +
                        getFileOldDir(context, itemToMove),
                    itemToMove.getName());
                //Perform copy operation if file doesn't exist in the new directory.
                if (!fileToSave.exists()) {
                    ParseFileUtils.copyFile(itemToMove, fileToSave);
                    logMigrationStatus(itemToMove.getName(), itemToMove.getPath(), fileToSave.getAbsolutePath(), "Successful.");
                } else {
                    logMigrationStatus(itemToMove.getName(), itemToMove.getPath(), fileToSave.getAbsolutePath(), "Already exist in new location.");
                }
                ParseFileUtils.deleteQuietly(itemToMove);
                PLog.v(TAG, "File deleted: " + "{" + itemToMove.getName() + "}" + " successfully");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //Check again, if all files has been resolved or not. If yes, delete the old dir "app_Parse".
        filesToBeMigrated.clear();
        ParseFileUtils.getAllNestedFiles(getOldParseDir(context).getAbsolutePath(), filesToBeMigrated);
        if (filesToBeMigrated.isEmpty()) {
            try {
                ParseFileUtils.deleteDirectory(getOldParseDir(context));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        PLog.v(TAG, "Migration completed.");
    }

    private String getFileOldDir(Context context, File file) {
        //Parse the old sub directory name where the file should be moved (new location) by following the old sub directory name.
        String temp = file
            .getAbsolutePath()
            .replace(
                getOldParseDir(context).getAbsolutePath(), "")
            .replace("/" + file.getName(), "");
        //Before returning the path, replace file name from the last, eg. dir name & file name could be same, as we want to get only dir name.
        return replaceLast(temp, file.getName());
    }

    private void logMigrationStatus(String fileName, String oldPath, String newPath, String status) {
        PLog.v(TAG, "Migration for file: " + "{" + fileName + "}" + " from {" + oldPath + "} to {" + newPath + "}, Status: " + status);
    }

    /*Replace a given string from the last*/
    private String replaceLast(String text, String regex) {
        return text.replaceFirst("(?s)" + regex + "(?!.*?" + regex + ")", "");
    }

    private File getOldParseDir(Context context) {
        return context.getDir("Parse", Context.MODE_PRIVATE);
    }


}
