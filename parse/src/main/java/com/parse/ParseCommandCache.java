/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import bolts.Capture;
import bolts.Continuation;
import bolts.Task;
import bolts.TaskCompletionSource;

/**
 * ParseCommandCache manages an on-disk cache of commands to be executed, and a thread with a
 * standard run loop that executes the commands. There should only ever be one instance of this
 * class, because multiple instances would be running separate threads trying to read and execute
 * the same commands.
 */
/** package */ class ParseCommandCache extends ParseEventuallyQueue {
  private static final String TAG = "com.parse.ParseCommandCache";

  private static int filenameCounter = 0; // Appended to temp file names so we know their creation
                                          // order.

  // Lock guards access to the file system and all of the instance variables above. It is static so
  // that if somehow there are two instances of ParseCommandCache, they won't step on each others'
  // toes while using the file system. A thread with lock should *not* try to get runningLock, under
  // penalty of deadlock. Only the run loop (runLoop) thread should ever wait on this lock. Other
  // threads should notify on this lock whenever the run loop should wake up and try to execute more
  // commands.
  private static final Object lock = new Object();

  private static File getCacheDir() {
    // Construct the path to the cache directory.
    File cacheDir = new File(Parse.getParseDir(), "CommandCache");
    cacheDir.mkdirs();

    return cacheDir;
  }

  public static int getPendingCount() {
    synchronized (lock) {
      String[] files = getCacheDir().list();
      return files == null ? 0 : files.length;
    }
  }

  private File cachePath; // Where the cache is stored on disk.
  private int timeoutMaxRetries = 5; // Don't retry more than 5 times before assuming disconnection.
  private double timeoutRetryWaitSeconds = 600.0f; // Wait 10 minutes before retrying after network
                                                   // timeout.
  private int maxCacheSizeBytes = 10 * 1024 * 1024; // Don't consume more than N bytes of storage.

  private boolean shouldStop; // Should the run loop thread processing the disk cache continue?
  private boolean unprocessedCommandsExist; // Has a command been added which hasn't yet been
                                            // processed by the run loop?

  // Map of filename to TaskCompletionSource, for all commands that are in the queue from this run
  // of the program. This is necessary so that the original objects can be notified after their
  // saves complete.
  private HashMap<File, TaskCompletionSource<JSONObject>> pendingTasks = new HashMap<>();

  private boolean running; // Is the run loop executing commands from the disk cache running?

  // Guards access to running. Gets a broadcast whenever running changes. A thread should only wait
  // on runningLock if it's sure the value of running is going to change. Only the run loop
  // (runLoop) thread should ever notify on runningLock. It's perfectly fine for a thread that has
  // runningLock to then also try to acquire the other lock.
  private final Object runningLock;

  private Logger log; // Why is there a custom logger? To prevent Mockito deadlock!

  private final ParseHttpClient httpClient;

  ConnectivityNotifier notifier;
  ConnectivityNotifier.ConnectivityListener listener = new ConnectivityNotifier.ConnectivityListener() {
    @Override
    public void networkConnectivityStatusChanged(Context context, Intent intent) {
      final boolean connectionLost =
          intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
      final boolean isConnected = ConnectivityNotifier.isConnected(context);

      /*
       Hack to avoid blocking the UI thread with disk I/O

       setConnected uses the same lock we use for synchronizing disk I/O, so there's a possibility
       that we can block the UI thread on disk I/O, so we're going to bump the lock usage to a
       different thread.

       TODO(grantland): Convert to TaskQueue, similar to ParsePinningEventuallyQueue
        */
      Task.call(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          if (connectionLost) {
            setConnected(false);
          } else {
            setConnected(isConnected);
          }
          return null;
        }
      }, ParseExecutors.io());
    }
  };

  public ParseCommandCache(Context context, ParseHttpClient client) {
    setConnected(false);

    shouldStop = false;
    running = false;

    runningLock = new Object();
    httpClient = client;

    log = Logger.getLogger(TAG);

    cachePath = getCacheDir();

    if (!Parse.hasPermission(Manifest.permission.ACCESS_NETWORK_STATE)) {
      // The command cache only works if the user has granted us permission to monitor the network.
      return;
    }

    setConnected(ConnectivityNotifier.isConnected(context));
    notifier = ConnectivityNotifier.getNotifier(context);
    notifier.addListener(listener);
    
    resume();
  }

  @Override
  public void onDestroy() {
    //TODO (grantland): pause #6484855

    notifier.removeListener(listener);
  }

  // Set the maximum number of times to retry before assuming disconnection.
  @SuppressWarnings("unused")
  public void setTimeoutMaxRetries(int tries) {
    synchronized (lock) {
      timeoutMaxRetries = tries;
    }
  }

  // Sets the amount of time to wait before retrying after network timeout.
  public void setTimeoutRetryWaitSeconds(double seconds) {
    synchronized (lock) {
      timeoutRetryWaitSeconds = seconds;
    }
  }

  // Sets the maximum amount of storage space this cache can consume.
  public void setMaxCacheSizeBytes(int bytes) {
    synchronized (lock) {
      maxCacheSizeBytes = bytes;
    }
  }

  // Starts the run loop thread running.
  public void resume() {
    synchronized (runningLock) {
      if (!running) {
        new Thread("ParseCommandCache.runLoop()") {
          @Override
          public void run() {
            runLoop();
          }
        }.start();
        try {
          runningLock.wait();
        } catch (InterruptedException e) {
          // Someone told this thread to stop.
          synchronized (lock) {
            shouldStop = true;
            lock.notifyAll();
          }
        }
      }
    }
  }

  // Stops the run loop thread from processing commands until resume is called.
  // When this function returns, the run loop has stopped.
  public void pause() {
    synchronized (runningLock) {
      if (running) {
        synchronized (lock) {
          shouldStop = true;
          lock.notifyAll();
        }
      }
      while (running) {
        try {
          runningLock.wait();
        } catch (InterruptedException e) {
          // Someone told this thread to stop while it was already waiting to
          // finish...
          // Ignore them and continue waiting.
        }
      }
    }
  }

  /**
   * Removes a file from the file system and any internal caches.
   */
  private void removeFile(File file) {
    synchronized (lock) {
      // Remove the data in memory for this command.
      pendingTasks.remove(file);

      // Release all the localIds referenced by the command.
      // Read one command from the cache.
      JSONObject json;
      try {
        json = ParseFileUtils.readFileToJSONObject(file);

        ParseRESTCommand command = commandFromJSON(json);
        command.releaseLocalIds();
      } catch (Exception e) {
        // Well, we did our best. We'll just have to leak a localId.
      }

      // Delete the command file itself.
      ParseFileUtils.deleteQuietly(file);
    }
  }

  /**
   * Makes this command cache forget all the state it keeps during a single run of the app. This is
   * only for testing purposes.
   */
  void simulateReboot() {
    synchronized (lock) {
      pendingTasks.clear();
    }
  }

  /**
   * Fakes an object update notification for use in tests. This is used by saveEventually to make it
   * look like test code has updated an object through the command cache even if it actually
   * avoided executing update by determining the object wasn't dirty.
   */
  void fakeObjectUpdate() {
    notifyTestHelper(TestHelper.COMMAND_ENQUEUED);
    notifyTestHelper(TestHelper.COMMAND_SUCCESSFUL);
    notifyTestHelper(TestHelper.OBJECT_UPDATED);
  }

  @Override
  public Task<JSONObject> enqueueEventuallyAsync(ParseRESTCommand command,
      ParseObject object) {
    return enqueueEventuallyAsync(command, false, object);
  }

  /**
   * Attempts to run the given command and any pending commands. Adds the command to the pending set
   * if it can't be run yet.
   *
   * @param command
   *          - The command to run.
   * @param preferOldest
   *          - When the disk is full, if preferOldest, drop new commands. Otherwise, the oldest
   *          commands will be deleted to make room.
   * @param object
   *          - See runEventually.
   */
  private Task<JSONObject> enqueueEventuallyAsync(ParseRESTCommand command, boolean preferOldest,
      ParseObject object) {
    Parse.requirePermission(Manifest.permission.ACCESS_NETWORK_STATE);
    TaskCompletionSource<JSONObject> tcs = new TaskCompletionSource<>();
    byte[] json;
    try {
      // If this object doesn't have an objectId yet, store the localId so we can remap it to the
      // objectId after the save completes.
      if (object != null && object.getObjectId() == null) {
        command.setLocalId(object.getOrCreateLocalId());
      }
      JSONObject jsonObject = command.toJSONObject();
      json = jsonObject.toString().getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      if (Parse.LOG_LEVEL_WARNING >= Parse.getLogLevel()) {
        log.log(Level.WARNING, "UTF-8 isn't supported.  This shouldn't happen.", e);
      }
      notifyTestHelper(TestHelper.COMMAND_NOT_ENQUEUED);
      return Task.forResult(null);
    }

    // If this object by itself is larger than the full disk cache, then don't
    // even bother trying.
    if (json.length > maxCacheSizeBytes) {
      if (Parse.LOG_LEVEL_WARNING >= Parse.getLogLevel()) {
        log.warning("Unable to save command for later because it's too big.");
      }
      notifyTestHelper(TestHelper.COMMAND_NOT_ENQUEUED);
      return Task.forResult(null);
    }

    synchronized (lock) {
      try {
        // Is there enough free storage space?
        String[] fileNames = cachePath.list();
        if (fileNames != null) {
          Arrays.sort(fileNames);
          int size = 0;
          for (String fileName : fileNames) {
            File file = new File(cachePath, fileName);
            // Should be safe to convert long to int, because we don't allow
            // files larger than 2GB.
            size += (int) file.length();
          }
          size += json.length;
          if (size > maxCacheSizeBytes) {
            if (preferOldest) {
              if (Parse.LOG_LEVEL_WARNING >= Parse.getLogLevel()) {
                log.warning("Unable to save command for later because storage is full.");
              }
              return Task.forResult(null);
            } else {
              if (Parse.LOG_LEVEL_WARNING >= Parse.getLogLevel()) {
                log.warning("Deleting old commands to make room in command cache.");
              }
              int indexToDelete = 0;
              while (size > maxCacheSizeBytes && indexToDelete < fileNames.length) {
                File file = new File(cachePath, fileNames[indexToDelete++]);
                size -= (int) file.length();
                removeFile(file);
              }
            }
          }
        }

        // Get the current time to store in the filename, so that we process them in order.
        String prefix1 = Long.toHexString(System.currentTimeMillis());
        if (prefix1.length() < 16) {
          char[] zeroes = new char[16 - prefix1.length()];
          Arrays.fill(zeroes, '0');
          prefix1 = new String(zeroes) + prefix1;
        }

        // Then add another incrementing number in case we enqueue items faster than the system's
        // time granularity.
        String prefix2 = Integer.toHexString(filenameCounter++);
        if (prefix2.length() < 8) {
          char[] zeroes = new char[8 - prefix2.length()];
          Arrays.fill(zeroes, '0');
          prefix2 = new String(zeroes) + prefix2;
        }

        String prefix = "CachedCommand_" + prefix1 + "_" + prefix2 + "_";

        // Get a unique filename to store this command in.
        File path = File.createTempFile(prefix, "", cachePath);

        // Write the command to that file.
        pendingTasks.put(path, tcs);
        command.retainLocalIds();
        ParseFileUtils.writeByteArrayToFile(path, json);

        notifyTestHelper(TestHelper.COMMAND_ENQUEUED);

        unprocessedCommandsExist = true;
      } catch (IOException e) {
        if (Parse.LOG_LEVEL_WARNING >= Parse.getLogLevel()) {
          log.log(Level.WARNING, "Unable to save command for later.", e);
        }
      } finally {
        lock.notifyAll();
      }
    }
    return tcs.getTask();
  }

  /**
   * Returns the number of commands currently in the set waiting to be run.
   */
  @Override
  public int pendingCount() {
    return getPendingCount();
  }

  /**
   * Gets rid of all pending commands.
   */
  public void clear() {
    synchronized (lock) {
      File[] files = cachePath.listFiles();
      if (files == null) {
        return;
      }
      for (File file : files) {
        removeFile(file);
      }
      pendingTasks.clear();
    }
  }

  /**
   * Manually sets the network connection status.
   */
  public void setConnected(boolean connected) {
    synchronized (lock) {
      if (isConnected() != connected) {
        if (connected) {
          lock.notifyAll();
        }
      }
      super.setConnected(connected);
    }
  }

  /**
   * This is kind of like ParseTaskUtils.wait(), except that it gives up the CommandCache's lock
   * while the task is running, and reclaims it before returning.
   */
  private <T> T waitForTaskWithoutLock(Task<T> task) throws ParseException {
    synchronized (lock) {
      final Capture<Boolean> finished = new Capture<>(false);
      task.continueWith(new Continuation<T, Void>() {
        @Override
        public Void then(Task<T> task) throws Exception {
          finished.set(true);
          synchronized(lock) {
            lock.notifyAll();
          }
          return null;
        }
      }, Task.BACKGROUND_EXECUTOR);
      while (!finished.get()) {
        try {
          lock.wait();
        } catch (InterruptedException ie) {
          shouldStop = true;
        }
      }
      return ParseTaskUtils.wait(task);  // Just to get the return value and maybe rethrow.
    }
  }
  
  /**
   * Attempts to run every command in the disk queue in order, synchronously. If there is no network
   * connection, returns immediately without doing anything. If there is supposedly a connection,
   * but parse can't be reached, waits timeoutRetryWaitSeconds before retrying up to
   * retriesRemaining times. Blocks until either there's a connection, or the retries are exhausted.
   * If any command fails, just deletes it and moves on to the next one.
   */
  private void maybeRunAllCommandsNow(int retriesRemaining) {
    synchronized (lock) {
      unprocessedCommandsExist = false;
      
      if (!isConnected()) {
        // There's no way to do work when there's no network connection.
        return;
      }

      String[] fileNames = cachePath.list();
      if (fileNames == null || fileNames.length == 0) {
        return;
      }
      Arrays.sort(fileNames);
      for (String fileName : fileNames) {
        final File file = new File(cachePath, fileName);

        // Read one command from the cache.
        JSONObject json;
        try {
          json = ParseFileUtils.readFileToJSONObject(file);
        } catch (FileNotFoundException e) {
          // This shouldn't really be possible.
          if (Parse.LOG_LEVEL_ERROR >= Parse.getLogLevel()) {
            log.log(Level.SEVERE, "File disappeared from cache while being read.", e);
          }
          continue;
        } catch (IOException e) {
          if (Parse.LOG_LEVEL_ERROR >= Parse.getLogLevel()) {
            log.log(Level.SEVERE, "Unable to read contents of file in cache.", e);
          }
          removeFile(file);
          continue;
        } catch (JSONException e) {
          if (Parse.LOG_LEVEL_ERROR >= Parse.getLogLevel()) {
            log.log(Level.SEVERE, "Error parsing JSON found in cache.", e);
          }
          removeFile(file);
          continue;
        }

        // Convert the command from a string.
        final ParseRESTCommand command;
        final TaskCompletionSource<JSONObject> tcs =
            pendingTasks.containsKey(file) ? pendingTasks.get(file) : null;

        try {
          command = commandFromJSON(json);
        } catch (JSONException e) {
          if (Parse.LOG_LEVEL_ERROR >= Parse.getLogLevel()) {
            log.log(Level.SEVERE, "Unable to create ParseCommand from JSON.", e);
          }
          removeFile(file);
          continue;
        }

        try {
          Task<JSONObject> commandTask;
          if (command == null) {
            commandTask = Task.forResult(null);
            if (tcs != null) {
              tcs.setResult(null);
            }
            notifyTestHelper(TestHelper.COMMAND_OLD_FORMAT_DISCARDED);
          } else {
            commandTask = command.executeAsync(httpClient).continueWithTask(new Continuation<JSONObject, Task<JSONObject>>() {
              @Override
              public Task<JSONObject> then(Task<JSONObject> task) throws Exception {
                String localId = command.getLocalId();
                Exception error = task.getError();
                if (error != null) {
                  if (error instanceof ParseException
                      && ((ParseException) error).getCode() == ParseException.CONNECTION_FAILED) {
                    // do nothing
                  } else {
                    if (tcs != null) {
                      tcs.setError(error);
                    }
                  }
                  return task;
                }

                JSONObject json = task.getResult();
                if (tcs != null) {
                  tcs.setResult(json);
                } else if (localId != null) {
                  // If this command created a new objectId, add it to the map.
                  String objectId = json.optString("objectId", null);
                  if (objectId != null) {
                    ParseCorePlugins.getInstance()
                        .getLocalIdManager().setObjectId(localId, objectId);
                  }
                }
                return task;
              }
            });
          }

          waitForTaskWithoutLock(commandTask);
          if (tcs != null) {
            waitForTaskWithoutLock(tcs.getTask());
          }
          
          // The command succeeded. Remove it from the cache.
          removeFile(file);
          notifyTestHelper(TestHelper.COMMAND_SUCCESSFUL);
        } catch (ParseException e) {
          if (e.getCode() == ParseException.CONNECTION_FAILED) {
            if (retriesRemaining > 0) {
              // Reachability says we have a network connection, but we can't actually contact
              // Parse. Wait N minutes, or until we get signaled again before doing anything else.
              if (Parse.LOG_LEVEL_INFO >= Parse.getLogLevel()) {
                log.info("Network timeout in command cache. Waiting for " + timeoutRetryWaitSeconds
                    + " seconds and then retrying " + retriesRemaining + " times.");
              }
              long currentTime = System.currentTimeMillis();
              long waitUntil = currentTime + (long) (timeoutRetryWaitSeconds * 1000);
              while (currentTime < waitUntil) {
                // We haven't waited long enough, but if we lost the connection,
                // or should stop, just quit.
                if (!isConnected() || shouldStop) {
                  if (Parse.LOG_LEVEL_INFO >= Parse.getLogLevel()) {
                    log.info("Aborting wait because runEventually thread should stop.");
                  }
                  return;
                }
                try {
                  lock.wait(waitUntil - currentTime);
                } catch (InterruptedException ie) {
                  shouldStop = true;
                }
                currentTime = System.currentTimeMillis();
                if (currentTime < (waitUntil - (long) (timeoutRetryWaitSeconds * 1000))) {
                  // This situation should be impossible, so it must mean the clock changed.
                  currentTime = (waitUntil - (long) (timeoutRetryWaitSeconds * 1000));
                }
              }
              maybeRunAllCommandsNow(retriesRemaining - 1);
            } else {
              setConnected(false);

              notifyTestHelper(TestHelper.NETWORK_DOWN);
            }
          } else {
            if (Parse.LOG_LEVEL_ERROR >= Parse.getLogLevel()) {
              log.log(Level.SEVERE, "Failed to run command.", e);
            }
            // Delete the command from the cache, even though it failed.
            // Otherwise, we'll just keep trying it forever.
            removeFile(file);
            notifyTestHelper(TestHelper.COMMAND_FAILED, e);
          }
        }
      }
    }
  }

  /**
   * The main function of the run loop thread. This function basically loops forever (unless pause
   * is called). On each iteration, if it hasn't been told to stop, it calls maybeRunAllCommandsNow
   * to try to execute everything queued up on disk. Then it waits until it gets signaled again by
   * lock.notify(). Usually that happens as a result of either (1) Parse being initialized, (2)
   * runEventually being called, or (3) the OS notifying that the network connection has been
   * re-established.
   */
  private void runLoop() {
    if (Parse.LOG_LEVEL_INFO >= Parse.getLogLevel()) {
      log.info("Parse command cache has started processing queued commands.");
    }
    // Make sure we marked as running.
    synchronized (runningLock) {
      if (running) {
        // Don't run this thread more than once.
        return;
      } else {
        running = true;
        runningLock.notifyAll();
      }
    }

    boolean shouldRun;
    synchronized (lock) {
      shouldRun = !(shouldStop || Thread.interrupted());
    }
    while (shouldRun) {
      synchronized (lock) {
        try {
          maybeRunAllCommandsNow(timeoutMaxRetries);
          if (!shouldStop) {
            try {
              /*
               * If an unprocessed command was added, avoid waiting because we want
               * maybeRunAllCommandsNow to run at least once to potentially process that command.
               */
              if (!unprocessedCommandsExist) {
                lock.wait();
              }
            } catch (InterruptedException e) {
              shouldStop = true;
            }
          }
        } catch (Exception e) {
          if (Parse.LOG_LEVEL_ERROR >= Parse.getLogLevel()) {
            log.log(Level.SEVERE, "saveEventually thread had an error.", e);
          }
        } finally {
          shouldRun = !shouldStop;
        }
      }
    }

    synchronized (runningLock) {
      running = false;
      runningLock.notifyAll();
    }
    if (Parse.LOG_LEVEL_INFO >= Parse.getLogLevel()) {
      log.info("saveEventually thread has stopped processing commands.");
    }
  }
}
