/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.Arrays;
import java.util.List;

import bolts.Continuation;
import bolts.Task;

/** package */ class OfflineObjectStore<T extends ParseObject> implements ParseObjectStore<T> {

  private static ParseObjectSubclassingController getSubclassingController() {
    return ParseCorePlugins.getInstance().getSubclassingController();
  }

  private static <T extends ParseObject> Task<T> migrate(
      final ParseObjectStore<T> from, final ParseObjectStore<T> to) {
    return from.getAsync().onSuccessTask(new Continuation<T, Task<T>>() {
      @Override
      public Task<T> then(Task<T> task) throws Exception {
        final T object = task.getResult();
        if (object == null) {
          return task;
        }

        return Task.whenAll(Arrays.asList(
            from.deleteAsync(),
            to.setAsync(object)
        )).continueWith(new Continuation<Void, T>() {
          @Override
          public T then(Task<Void> task) throws Exception {
            return object;
          }
        });
      }
    });
  }

  private final String className;
  private final String pinName;
  private final ParseObjectStore<T> legacy;

  public OfflineObjectStore(Class<T> clazz, String pinName, ParseObjectStore<T> legacy) {
    this(getSubclassingController().getClassName(clazz), pinName, legacy);
  }

  public OfflineObjectStore(String className, String pinName, ParseObjectStore<T> legacy) {
    this.className = className;
    this.pinName = pinName;
    this.legacy = legacy;
  }

  @Override
  public Task<Void> setAsync(final T object) {
    return ParseObject.unpinAllInBackground(pinName).continueWithTask(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> task) throws Exception {
        return object.pinInBackground(pinName, false);
      }
    });
  }

  @Override
  public Task<T> getAsync() {
    // We need to set `ignoreACLs` since we can't use ACLs without the current user.
    ParseQuery<T> query = ParseQuery.<T>getQuery(className)
        .fromPin(pinName)
        .ignoreACLs();
    return query.findInBackground().onSuccessTask(new Continuation<List<T>, Task<T>>() {
      @Override
      public Task<T> then(Task<List<T>> task) throws Exception {
        List<T> results = task.getResult();
        if (results != null) {
          if (results.size() == 1) {
            return Task.forResult(results.get(0));
          } else {
            return ParseObject.unpinAllInBackground(pinName).cast();
          }
        }
        return Task.forResult(null);
      }
    }).onSuccessTask(new Continuation<T, Task<T>>() {
      @Override
      public Task<T> then(Task<T> task) throws Exception {
        T ldsObject = task.getResult();
        if (ldsObject != null) {
          return task;
        }

        return migrate(legacy, OfflineObjectStore.this).cast();
      }
    });
  }

  @Override
  public Task<Boolean> existsAsync() {
    // We need to set `ignoreACLs` since we can't use ACLs without the current user.
    ParseQuery<T> query = ParseQuery.<T>getQuery(className)
        .fromPin(pinName)
        .ignoreACLs();
    return query.countInBackground().onSuccessTask(new Continuation<Integer, Task<Boolean>>() {
      @Override
      public Task<Boolean> then(Task<Integer> task) throws Exception {
        boolean exists = task.getResult() == 1;
        if (exists) {
          return Task.forResult(true);
        }
        return legacy.existsAsync();
      }
    });
  }

  @Override
  public Task<Void> deleteAsync() {
    final Task<Void> ldsTask = ParseObject.unpinAllInBackground(pinName);
    return Task.whenAll(Arrays.asList(
        legacy.deleteAsync(),
        ldsTask
    )).continueWithTask(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> task) throws Exception {
        // We only really care about the result of unpinning.
        return ldsTask;
      }
    });
  }
}
