/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Pair;

import com.parse.OfflineQueryLogic.ConstraintMatcher;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import bolts.Capture;
import bolts.Continuation;
import bolts.Task;
import bolts.TaskCompletionSource;

/** package */ class OfflineStore {

  /**
   * SQLite has a max of 999 SQL variables in a single statement.
   */
  private static final int MAX_SQL_VARIABLES = 999;

  /**
   * Extends the normal JSON -> ParseObject decoding to also deal with placeholders for new objects
   * that have been saved offline.
   */
  private class OfflineDecoder extends ParseDecoder {
    // A map of UUID -> Task that will be finished once the given ParseObject is loaded.
    // The Tasks should all be finished before decode is called.
    private Map<String, Task<ParseObject>> offlineObjects;

    private OfflineDecoder(Map<String, Task<ParseObject>> offlineObjects) {
      this.offlineObjects = offlineObjects;
    }

    @Override
    public Object decode(Object object) {
      // If we see an offline id, make sure to decode it.
      if (object instanceof JSONObject
          && ((JSONObject) object).optString("__type").equals("OfflineObject")) {
        String uuid = ((JSONObject) object).optString("uuid");
        return offlineObjects.get(uuid).getResult();
      }

      /*
       * Embedded objects can't show up here, because we never stored them that way offline.
       */

      return super.decode(object);
    }
  }

  /**
   * An encoder that can encode objects that are available offline. After using this encoder, you
   * must call whenFinished() and wait for its result to be finished before the results of the
   * encoding will be valid.
   */
  private class OfflineEncoder extends ParseEncoder {
    private ParseSQLiteDatabase db;
    private ArrayList<Task<Void>> tasks = new ArrayList<>();
    private final Object tasksLock = new Object();

    /**
     * Creates an encoder.
     *
     * @param db
     *          A database connection to use.
     */
    public OfflineEncoder(ParseSQLiteDatabase db) {
      this.db = db;
    }

    /**
     * The results of encoding an object with this encoder will not be valid until the task returned
     * by this method is finished.
     */
    public Task<Void> whenFinished() {
      return Task.whenAll(tasks).continueWithTask(new Continuation<Void, Task<Void>>() {
        @Override
        public Task<Void> then(Task<Void> ignore) throws Exception {
          synchronized (tasksLock) {
            // It might be better to return an aggregate error here.
            for (Task<Void> task : tasks) {
              if (task.isFaulted() || task.isCancelled()) {
                return task;
              }
            }
            tasks.clear();
            return Task.forResult(null);
          }
        }
      });
    }

    /**
     * Implements an encoding strategy for Parse Objects that uses offline ids when necessary.
     */
    @Override
    public JSONObject encodeRelatedObject(ParseObject object) {
      try {
        if (object.getObjectId() != null) {
          JSONObject result = new JSONObject();
          result.put("__type", "Pointer");
          result.put("objectId", object.getObjectId());
          result.put("className", object.getClassName());
          return result;
        }

        final JSONObject result = new JSONObject();
        result.put("__type", "OfflineObject");
        synchronized (tasksLock) {
          tasks.add(getOrCreateUUIDAsync(object, db).onSuccess(new Continuation<String, Void>() {
            @Override
            public Void then(Task<String> task) throws Exception {
              result.put("uuid", task.getResult());
              return null;
            }
          }));
        }
        return result;
      } catch (JSONException e) {
        // This can literally never happen.
        throw new RuntimeException(e);
      }
    }
  }

  // Lock for all members of the store.
  final private Object lock = new Object();

  // Helper for accessing the database.
  final private OfflineSQLiteOpenHelper helper;

  /**
   * In-memory map of UUID -> ParseObject. This is used so that we can always return the same
   * instance for a given object. The only objects in this map are ones that are in the database.
   */
  final private WeakValueHashMap<String, ParseObject> uuidToObjectMap = new WeakValueHashMap<>();

  /**
   * In-memory map of ParseObject -> UUID. This is used to that when we see an unsaved ParseObject
   * that's already in the database, we can update the same record in the database. It stores a Task
   * instead of the String, because one thread may want to reserve the spot. Once the task is
   * finished, there will be a row for this UUID in the database.
   */
  final private WeakHashMap<ParseObject, Task<String>> objectToUuidMap = new WeakHashMap<>();

  /**
   * In-memory set of ParseObjects that have been fetched from the local database already. If the
   * object is in the map, a fetch of it has been started. If the value is a finished task, then the
   * fetch was completed.
   */
  final private WeakHashMap<ParseObject, Task<ParseObject>> fetchedObjects = new WeakHashMap<>();

  /**
   * Used by the static method to create the singleton.
   */
  /* package */ OfflineStore(Context context) {
    this(new OfflineSQLiteOpenHelper(context));
  }

  /* package */ OfflineStore(OfflineSQLiteOpenHelper helper) {
    this.helper = helper;
  }

  /**
   * Gets the UUID for the given object, if it has one. Otherwise, creates a new UUID for the object
   * and adds a new row to the database for the object with no data.
   */
  private Task<String> getOrCreateUUIDAsync(final ParseObject object, ParseSQLiteDatabase db) {
    final String newUUID = UUID.randomUUID().toString();
    final TaskCompletionSource<String> tcs = new TaskCompletionSource<>();

    synchronized (lock) {
      Task<String> uuidTask = objectToUuidMap.get(object);
      if (uuidTask != null) {
        return uuidTask;
      }

      // The object doesn't have a UUID yet, so we're gonna have to make one.
      objectToUuidMap.put(object, tcs.getTask());
      uuidToObjectMap.put(newUUID, object);
      fetchedObjects.put(object, tcs.getTask().onSuccess(new Continuation<String, ParseObject>() {
        @Override
        public ParseObject then(Task<String> task) throws Exception {
          return object;
        }
      }));
    }

    /*
     * We need to put a placeholder row in the database so that later on, the save can just be an
     * update. This could be a pointer to an object that itself never gets saved offline, in which
     * case the consumer will just have to deal with that.
     */
    ContentValues values = new ContentValues();
    values.put(OfflineSQLiteOpenHelper.KEY_UUID, newUUID);
    values.put(OfflineSQLiteOpenHelper.KEY_CLASS_NAME, object.getClassName());
    db.insertOrThrowAsync(OfflineSQLiteOpenHelper.TABLE_OBJECTS, values).continueWith(
        new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) throws Exception {
            // This will signal that the UUID does represent a row in the database.
            tcs.setResult(newUUID);
            return null;
          }
        });

    return tcs.getTask();
  }

  /**
   * Gets an unfetched pointer to an object in the db, based on its uuid. The object may or may not
   * be in memory, but it must be in the database. If it is already in memory, that instance will be
   * returned. Since this is only for creating pointers to objects that are referenced by other
   * objects in the data store, that's a fair assumption.
   *
   * @param uuid
   *          The object to retrieve.
   * @param db
   *          The database instance to retrieve from.
   * @return The object with that UUID.
   */
  private <T extends ParseObject> Task<T> getPointerAsync(final String uuid,
      ParseSQLiteDatabase db) {
    synchronized (lock) {
      @SuppressWarnings("unchecked")
      T existing = (T) uuidToObjectMap.get(uuid);
      if (existing != null) {
        return Task.forResult(existing);
      }
    }

    /*
     * We want to just return the pointer, but we have to look in the database to know if there's
     * something with this classname and object id already.
     */

    String[] select = { OfflineSQLiteOpenHelper.KEY_CLASS_NAME, OfflineSQLiteOpenHelper.KEY_OBJECT_ID };
    String where = OfflineSQLiteOpenHelper.KEY_UUID + " = ?";
    String[] args = { uuid };
    return db.queryAsync(OfflineSQLiteOpenHelper.TABLE_OBJECTS, select, where, args).onSuccess(
        new Continuation<Cursor, T>() {
          @Override
          public T then(Task<Cursor> task) throws Exception {
            Cursor cursor = task.getResult();
            cursor.moveToFirst();
            if (cursor.isAfterLast()) {
              cursor.close();
              throw new IllegalStateException("Attempted to find non-existent uuid " + uuid);
            }

            synchronized (lock) {
              // We need to check again since another task might have come around and added it to
              // the map.
              //TODO (grantland): Maybe we should insert a Task that is resolved when the query
              // completes like we do in getOrCreateUUIDAsync?
              @SuppressWarnings("unchecked")
              T existing = (T) uuidToObjectMap.get(uuid);
              if (existing != null) {
                return existing;
              }

              String className = cursor.getString(0);
              String objectId = cursor.getString(1);
              cursor.close();
              @SuppressWarnings("unchecked")
              T pointer = (T) ParseObject.createWithoutData(className, objectId);
              /*
               * If it doesn't have an objectId, we don't really need the UUID, and this simplifies
               * some other logic elsewhere if we only update the map for new objects.
               */
              if (objectId == null) {
                uuidToObjectMap.put(uuid, pointer);
                objectToUuidMap.put(pointer, Task.forResult(uuid));
              }
              return pointer;
            }
          }
        });
  }

  /**
   * Runs a ParseQuery against the store's contents.
   *
   * @return The objects that match the query's constraints.
   */
  /* package for OfflineQueryLogic */ <T extends ParseObject> Task<List<T>> findAsync(
      ParseQuery.State<T> query,
      ParseUser user,
      ParsePin pin,
      ParseSQLiteDatabase db) {
    return findAsync(query, user, pin, false, db);
  }

  /**
   * Runs a ParseQuery against the store's contents. May cause any instances of T to get fetched
   * from the offline database. TODO(klimt): Should the query consider objects that are in memory,
   * but not in the offline store?
   *
   * @param query The query.
   * @param user The user making the query.
   * @param pin (Optional) The pin we are querying across. If null, all pins.
   * @param isCount True if we are doing a count.
   * @param db The SQLiteDatabase.
   * @param <T> Subclass of ParseObject.
   * @return The objects that match the query's constraints.
   */
  private <T extends ParseObject> Task<List<T>> findAsync(
      final ParseQuery.State<T> query,
      final ParseUser user,
      final ParsePin pin,
      final boolean isCount,
      final ParseSQLiteDatabase db) {
    /*
     * This is currently unused, but is here to allow future querying across objects that are in the
     * process of being deleted eventually.
     */
    final boolean includeIsDeletingEventually = false;

    final OfflineQueryLogic queryLogic = new OfflineQueryLogic(this);

    final List<T> results = new ArrayList<>();

    Task<Cursor> queryTask;
    if (pin == null) {
      String table = OfflineSQLiteOpenHelper.TABLE_OBJECTS;
      String[] select = { OfflineSQLiteOpenHelper.KEY_UUID };
      String where = OfflineSQLiteOpenHelper.KEY_CLASS_NAME + "=?";
      if (!includeIsDeletingEventually) {
        where += " AND " + OfflineSQLiteOpenHelper.KEY_IS_DELETING_EVENTUALLY + "=0";
      }
      String[] args = { query.className() };

      queryTask = db.queryAsync(table, select, where, args);
    } else {
      Task<String> uuidTask = objectToUuidMap.get(pin);
      if (uuidTask == null) {
        // Pin was never saved locally, therefore there won't be any results.
        return Task.forResult(results);
      }

      queryTask = uuidTask.onSuccessTask(new Continuation<String, Task<Cursor>>() {
        @Override
        public Task<Cursor> then(Task<String> task) throws Exception {
          String uuid = task.getResult();

          String table = OfflineSQLiteOpenHelper.TABLE_OBJECTS + " A " +
              " INNER JOIN " + OfflineSQLiteOpenHelper.TABLE_DEPENDENCIES + " B " +
              " ON A." + OfflineSQLiteOpenHelper.KEY_UUID + "=B." + OfflineSQLiteOpenHelper.KEY_UUID;
          String[] select = {"A." + OfflineSQLiteOpenHelper.KEY_UUID};
          String where = OfflineSQLiteOpenHelper.KEY_CLASS_NAME + "=?" +
              " AND " + OfflineSQLiteOpenHelper.KEY_KEY + "=?";
          if (!includeIsDeletingEventually) {
            where += " AND " + OfflineSQLiteOpenHelper.KEY_IS_DELETING_EVENTUALLY + "=0";
          }
          String[] args = { query.className(), uuid };

          return db.queryAsync(table, select, where, args);
        }
      });
    }

    return queryTask.onSuccessTask(new Continuation<Cursor, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Cursor> task) throws Exception {
        Cursor cursor = task.getResult();
        List<String> uuids = new ArrayList<>();
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
          uuids.add(cursor.getString(0));
        }
        cursor.close();

        // Find objects that match the where clause.
        final ConstraintMatcher<T> matcher = queryLogic.createMatcher(query, user);

        Task<Void> checkedAllObjects = Task.forResult(null);
        for (final String uuid : uuids) {
          final Capture<T> object = new Capture<>();

          checkedAllObjects = checkedAllObjects.onSuccessTask(new Continuation<Void, Task<T>>() {
            @Override
            public Task<T> then(Task<Void> task) throws Exception {
              return getPointerAsync(uuid, db);
            }
          }).onSuccessTask(new Continuation<T, Task<T>>() {
            @Override
            public Task<T> then(Task<T> task) throws Exception {
              object.set(task.getResult());
              return fetchLocallyAsync(object.get(), db);
            }
          }).onSuccessTask(new Continuation<T, Task<Boolean>>() {
            @Override
            public Task<Boolean> then(Task<T> task) throws Exception {
              if (!object.get().isDataAvailable()) {
                return Task.forResult(false);
              }
              return matcher.matchesAsync(object.get(), db);
            }
          }).onSuccess(new Continuation<Boolean, Void>() {
            @Override
            public Void then(Task<Boolean> task) {
              if (task.getResult()) {
                results.add(object.get());
              }
              return null;
            }
          });
        }

        return checkedAllObjects;
      }
    }).onSuccessTask(new Continuation<Void, Task<List<T>>>() {
      @Override
      public Task<List<T>> then(Task<Void> task) throws Exception {
        // Sort by any sort operators.
        OfflineQueryLogic.sort(results, query);

        // Apply the skip.
        List<T> trimmedResults = results;
        int skip = query.skip();
        if (!isCount && skip >= 0) {
          skip = Math.min(query.skip(), trimmedResults.size());
          trimmedResults = trimmedResults.subList(skip, trimmedResults.size());
        }

        // Trim to the limit.
        int limit = query.limit();
        if (!isCount && limit >= 0 && trimmedResults.size() > limit) {
          trimmedResults = trimmedResults.subList(0, limit);
        }

        // Fetch the includes.
        Task<Void> fetchedIncludesTask = Task.forResult(null);
        for (final T object : trimmedResults) {
          fetchedIncludesTask = fetchedIncludesTask.onSuccessTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) throws Exception {
              return OfflineQueryLogic.fetchIncludesAsync(OfflineStore.this, object, query, db);
            }
          });
        }

        final List<T> finalTrimmedResults = trimmedResults;
        return fetchedIncludesTask.onSuccess(new Continuation<Void, List<T>>() {
          @Override
          public List<T> then(Task<Void> task) throws Exception {
            return finalTrimmedResults;
          }
        });
      }
    });
  }

  /**
   * Gets the data for the given object from the offline database. Returns a task that will be
   * completed if data for the object was available. If the object is not in the cache, the task
   * will be faulted, with a CACHE_MISS error.
   *
   * @param object
   *          The object to fetch.
   * @param db
   *          A database connection to use.
   */
  /* package for OfflineQueryLogic */ <T extends ParseObject> Task<T> fetchLocallyAsync(
      final T object,
      final ParseSQLiteDatabase db) {
    final TaskCompletionSource<T> tcs = new TaskCompletionSource<>();
    Task<String> uuidTask;

    synchronized (lock) {
      if (fetchedObjects.containsKey(object)) {
        /*
         * The object has already been fetched from the offline store, so any data that's in there
         * is already reflected in the in-memory version. There's nothing more to do.
         */
        //noinspection unchecked
        return (Task<T>) fetchedObjects.get(object);
      }

      /*
       * Put a placeholder so that anyone else who attempts to fetch this object will just wait for
       * this call to finish doing it.
       */
      //noinspection unchecked
      fetchedObjects.put(object, (Task<ParseObject>) tcs.getTask());

      uuidTask = objectToUuidMap.get(object);
    }
    String className = object.getClassName();
    String objectId = object.getObjectId();

    /*
     * If this gets set, then it will contain data from the offline store that needs to be merged
     * into the existing object in memory.
     */
    Task<String> jsonStringTask = Task.forResult(null);

    if (objectId == null) {
      // This Object has never been saved to Parse.
      if (uuidTask == null) {
        /*
         * This object was not pulled from the data store or previously saved to it, so there's
         * nothing that can be fetched from it. This isn't an error, because it's really convenient
         * to try to fetch objects from the offline store just to make sure they are up-to-date, and
         * we shouldn't force developers to specially handle this case.
         */
      } else {
        /*
         * This object is a new ParseObject that is known to the data store, but hasn't been
         * fetched. The only way this could happen is if the object had previously been stored in
         * the offline store, then the object was removed from memory (maybe by rebooting), and then
         * a object with a pointer to it was fetched, so we only created the pointer. We need to
         * pull the data out of the database using the UUID.
         */
        final String[] select = { OfflineSQLiteOpenHelper.KEY_JSON };
        final String where = OfflineSQLiteOpenHelper.KEY_UUID + " = ?";
        final Capture<String> uuid = new Capture<>();
        jsonStringTask = uuidTask.onSuccessTask(new Continuation<String, Task<Cursor>>() {
          @Override
          public Task<Cursor> then(Task<String> task) throws Exception {
            uuid.set(task.getResult());
            String[] args = { uuid.get() };
            return db.queryAsync(OfflineSQLiteOpenHelper.TABLE_OBJECTS, select, where, args);
          }
        }).onSuccess(new Continuation<Cursor, String>() {
          @Override
          public String then(Task<Cursor> task) throws Exception {
            Cursor cursor = task.getResult();
            cursor.moveToFirst();
            if (cursor.isAfterLast()) {
              cursor.close();
              throw new IllegalStateException("Attempted to find non-existent uuid " + uuid.get());
            }
            String json = cursor.getString(0);
            cursor.close();

            return json;
          }
        });
      }
    } else {
      if (uuidTask != null) {
        /*
         * This object is an existing ParseObject, and we must've already pulled its data out of the
         * offline store, or else we wouldn't know its UUID. This should never happen.
         */
        tcs.setError(new IllegalStateException("This object must have already been "
            + "fetched from the local datastore, but isn't marked as fetched."));
        synchronized (lock) {
          // Forget we even tried to fetch this object, so that retries will actually... retry.
          fetchedObjects.remove(object);
        }
        return tcs.getTask();
      }

      /*
       * We've got a pointer to an existing ParseObject, but we've never pulled its data out of the
       * offline store. Since fetching from the server forces a fetch from the offline store, that
       * means this is a pointer. We need to try to find any existing entry for this object in the
       * database.
       */
      String[] select = { OfflineSQLiteOpenHelper.KEY_JSON, OfflineSQLiteOpenHelper.KEY_UUID  };
      String where =
          String.format("%s = ? AND %s = ?", OfflineSQLiteOpenHelper.KEY_CLASS_NAME,
              OfflineSQLiteOpenHelper.KEY_OBJECT_ID);
      String[] args = { className, objectId };
      jsonStringTask =
          db.queryAsync(OfflineSQLiteOpenHelper.TABLE_OBJECTS, select, where, args).onSuccess(
              new Continuation<Cursor, String>() {
                @Override
                public String then(Task<Cursor> task) throws Exception {
                  Cursor cursor = task.getResult();
                  cursor.moveToFirst();
                  if (cursor.isAfterLast()) {
                    /*
                     * This is a pointer that came from Parse that references an object that has
                     * never been saved in the offline store before. This just means there's no data
                     * in the store that needs to be merged into the object.
                     */
                    cursor.close();
                    throw new ParseException(ParseException.CACHE_MISS,
                        "This object is not available in the offline cache.");
                  }

                  // we should fetch its data and record its UUID for future reference.
                  String jsonString = cursor.getString(0);
                  String newUUID = cursor.getString(1);
                  cursor.close();

                  synchronized (lock) {
                    /*
                     * It's okay to put this object into the uuid map. No one will try to fetch
                     * it, because it's already in the fetchedObjects map. And no one will try to
                     * save to it without fetching it first, so everything should be just fine.
                     */
                    objectToUuidMap.put(object, Task.forResult(newUUID));
                    uuidToObjectMap.put(newUUID, object);
                  }

                  return jsonString;
                }
              });
    }

    return jsonStringTask.onSuccessTask(new Continuation<String, Task<Void>>() {
      @Override
      public Task<Void> then(Task<String> task) throws Exception {
        String jsonString = task.getResult();
        if (jsonString == null) {
          /*
           * This means we tried to fetch an object from the database that was never actually saved
           * locally. This probably means that its parent object was saved locally and we just
           * created a pointer to this object. This should be considered a cache miss.
           */
          return Task.forError(new ParseException(ParseException.CACHE_MISS,
              "Attempted to fetch an object offline which was never saved to the offline cache."));
        }
        final JSONObject json;
        try {
          /*
           * We can assume that whatever is in the database is the last known server state. The only
           * things to maintain from the in-memory object are any changes since the object was last
           * put in the database.
           */
          json = new JSONObject(jsonString);
        } catch (JSONException e) {
          return Task.forError(e);
        }

        // Fetch all the offline objects before we decode.
        final Map<String, Task<ParseObject>> offlineObjects = new HashMap<>();

        (new ParseTraverser() {
          @Override
          protected boolean visit(Object object) {
            if (object instanceof JSONObject
                && ((JSONObject) object).optString("__type").equals("OfflineObject")) {
              String uuid = ((JSONObject) object).optString("uuid");
              offlineObjects.put(uuid, getPointerAsync(uuid, db));
            }
            return true;
          }
        }).setTraverseParseObjects(false).setYieldRoot(false).traverse(json);

        return Task.whenAll(offlineObjects.values()).onSuccess(new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) throws Exception {
            object.mergeREST(object.getState(), json, new OfflineDecoder(offlineObjects));
            return null;
          }
        });
      }
    }).continueWithTask(new Continuation<Void, Task<T>>() {
      @Override
      public Task<T> then(Task<Void> task) throws Exception {
        if (task.isCancelled()) {
          tcs.setCancelled();
        } else if (task.isFaulted()) {
          tcs.setError(task.getError());
        } else {
          tcs.setResult(object);
        }
        return tcs.getTask();
      }
    });
  }

  /**
   * Gets the data for the given object from the offline database. Returns a task that will be
   * completed if data for the object was available. If the object is not in the cache, the task
   * will be faulted, with a CACHE_MISS error.
   *
   * @param object
   *          The object to fetch.
   */
  /* package */ <T extends ParseObject> Task<T> fetchLocallyAsync(final T object) {
    return runWithManagedConnection(new SQLiteDatabaseCallable<Task<T>>() {
      @Override
      public Task<T> call(ParseSQLiteDatabase db) {
        return fetchLocallyAsync(object, db);
      }
    });
  }

  /**
   * Stores a single object in the local database. If the object is a pointer, isn't dirty, and has
   * an objectId already, it may not be saved, since it would provide no useful data.
   *
   * @param object
   *          The object to save.
   * @param db
   *          A database connection to use.
   */
  private Task<Void> saveLocallyAsync(
      final String key, final ParseObject object, final ParseSQLiteDatabase db) {
    // If this is just a clean, unfetched pointer known to Parse, then there is nothing to save.
    if (object.getObjectId() != null && !object.isDataAvailable() && !object.hasChanges()
        && !object.hasOutstandingOperations()) {
      return Task.forResult(null);
    }

    final Capture<String> uuidCapture = new Capture<>();

    // Make sure we have a UUID for the object to be saved.
    return getOrCreateUUIDAsync(object, db).onSuccessTask(new Continuation<String, Task<Void>>() {
      @Override
      public Task<Void> then(Task<String> task) throws Exception {
        String uuid = task.getResult();
        uuidCapture.set(uuid);
        return updateDataForObjectAsync(uuid, object, db);
      }
    }).onSuccessTask(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> task) throws Exception {
        final ContentValues values = new ContentValues();
        values.put(OfflineSQLiteOpenHelper.KEY_KEY, key);
        values.put(OfflineSQLiteOpenHelper.KEY_UUID, uuidCapture.get());
        return db.insertWithOnConflict(OfflineSQLiteOpenHelper.TABLE_DEPENDENCIES, values,
            SQLiteDatabase.CONFLICT_IGNORE);
      }
    });
  }

  /**
   * Stores an object (and optionally, every object it points to recursively) in the local database.
   * If any of the objects have not been fetched from Parse, they will not be stored. However, if
   * they have changed data, the data will be retained. To get the objects back later, you can use a
   * ParseQuery with a cache policy that uses the local cache, or you can create an unfetched
   * pointer with ParseObject.createWithoutData() and then call fetchFromLocalDatastore() on it. If you modify
   * the object after saving it locally, such as by fetching it or saving it, those changes will
   * automatically be applied to the cache.
   *
   * Any objects previously stored with the same key will be removed from the local database.
   *
   * @param object Root object
   * @param includeAllChildren {@code true} to recursively save all pointers.
   * @param db DB connection
   * @return A Task that will be resolved when saving is complete
   */
  private Task<Void> saveLocallyAsync(
      final ParseObject object, final boolean includeAllChildren, final ParseSQLiteDatabase db) {
    final ArrayList<ParseObject> objectsInTree = new ArrayList<>();
    // Fetch all objects locally in case they are being re-added
    if (!includeAllChildren) {
      objectsInTree.add(object);
    } else {
      (new ParseTraverser() {
        @Override
        protected boolean visit(Object object) {
          if (object instanceof ParseObject) {
            objectsInTree.add((ParseObject) object);
          }
          return true;
        }
      }).setYieldRoot(true).setTraverseParseObjects(true).traverse(object);
    }

    return saveLocallyAsync(object, objectsInTree, db);
  }


  private Task<Void> saveLocallyAsync(
      final ParseObject object, List<ParseObject> children, final ParseSQLiteDatabase db) {
    final List<ParseObject> objects = children != null
        ? new ArrayList<>(children)
        : new ArrayList<ParseObject>();
    if (!objects.contains(object)) {
      objects.add(object);
    }

    // Call saveLocallyAsync for each of them individually.
    final List<Task<Void>> tasks = new ArrayList<>();
    for (ParseObject obj : objects) {
      tasks.add(fetchLocallyAsync(obj, db).makeVoid());
    }

    return Task.whenAll(tasks).continueWithTask(new Continuation<Void, Task<String>>() {
      @Override
      public Task<String> then(Task<Void> task) throws Exception {
        return objectToUuidMap.get(object);
      }
    }).onSuccessTask(new Continuation<String, Task<Void>>() {
      @Override
      public Task<Void> then(Task<String> task) throws Exception {
        String uuid = task.getResult();
        if (uuid == null) {
          // The root object was never stored in the offline store, so nothing to unpin.
          return null;
        }

        // Delete all objects locally corresponding to the key we're trying to use in case it was
        // used before (overwrite)
        return unpinAsync(uuid, db);
      }
    }).onSuccessTask(new Continuation<Void, Task<String>>() {
      @Override
      public Task<String> then(Task<Void> task) throws Exception {
        return getOrCreateUUIDAsync(object, db);
      }
    }).onSuccessTask(new Continuation<String, Task<Void>>() {
      @Override
      public Task<Void> then(Task<String> task) throws Exception {
        String uuid = task.getResult();

        // Call saveLocallyAsync for each of them individually.
        final List<Task<Void>> tasks = new ArrayList<>();
        for (ParseObject obj : objects) {
          tasks.add(saveLocallyAsync(uuid, obj, db));
        }

        return Task.whenAll(tasks);
      }
    });
  }

  private Task<Void> unpinAsync(final ParseObject object, final ParseSQLiteDatabase db) {
    Task<String> uuidTask = objectToUuidMap.get(object);
    if (uuidTask == null) {
      // The root object was never stored in the offline store, so nothing to unpin.
      return Task.forResult(null);
    }
    return uuidTask.continueWithTask(new Continuation<String, Task<Void>>() {
      @Override
      public Task<Void> then(Task<String> task) throws Exception {
        final String uuid = task.getResult();
        if (uuid == null) {
          // The root object was never stored in the offline store, so nothing to unpin.
          return Task.forResult(null);
        }
        return unpinAsync(uuid, db);
      }
    });
  }

  private Task<Void> unpinAsync(final String key, final ParseSQLiteDatabase db) {
    final List<String> uuidsToDelete = new LinkedList<>();
    // A continueWithTask that ends with "return task" is essentially a try-finally.
    return Task.forResult((Void) null).continueWithTask(new Continuation<Void, Task<Cursor>>() {
      @Override
      public Task<Cursor> then(Task<Void> task) throws Exception {
        // Fetch all uuids from Dependencies for key=? grouped by uuid having a count of 1
        String sql = "SELECT " + OfflineSQLiteOpenHelper.KEY_UUID + " FROM " + OfflineSQLiteOpenHelper.TABLE_DEPENDENCIES +
            " WHERE " + OfflineSQLiteOpenHelper.KEY_KEY + "=? AND " + OfflineSQLiteOpenHelper.KEY_UUID + " IN (" +
            " SELECT " + OfflineSQLiteOpenHelper.KEY_UUID + " FROM " + OfflineSQLiteOpenHelper.TABLE_DEPENDENCIES +
            " GROUP BY " + OfflineSQLiteOpenHelper.KEY_UUID +
            " HAVING COUNT(" + OfflineSQLiteOpenHelper.KEY_UUID + ")=1" +
            ")";
        String[] args = {key};
        return db.rawQueryAsync(sql, args);
      }
    }).onSuccessTask(new Continuation<Cursor, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Cursor> task) throws Exception {
        // DELETE FROM Objects

        Cursor cursor = task.getResult();
        while (cursor.moveToNext()) {
          uuidsToDelete.add(cursor.getString(0));
        }
        cursor.close();

        return deleteObjects(uuidsToDelete, db);
      }
    }).onSuccessTask(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> task) throws Exception {
        // DELETE FROM Dependencies
        String where = OfflineSQLiteOpenHelper.KEY_KEY + "=?";
        String[] args = {key};
        return db.deleteAsync(OfflineSQLiteOpenHelper.TABLE_DEPENDENCIES, where, args);
      }
    }).onSuccess(new Continuation<Void, Void>() {
      @Override
      public Void then(Task<Void> task) throws Exception {
        synchronized (lock) {
          // Remove uuids from memory
          for (String uuid : uuidsToDelete) {
            ParseObject object = uuidToObjectMap.get(uuid);
            if (object != null) {
              objectToUuidMap.remove(object);
              uuidToObjectMap.remove(uuid);
            }
          }
        }
        return null;
      }
    });
  }

  private Task<Void> deleteObjects(final List<String> uuids, final ParseSQLiteDatabase db) {
    if (uuids.size() <= 0) {
      return Task.forResult(null);
    }

    // SQLite has a max 999 SQL variables in a statement, so we need to split it up into manageable
    // chunks. We can do this because we're already in a transaction.
    if (uuids.size() > MAX_SQL_VARIABLES) {
      return deleteObjects(uuids.subList(0, MAX_SQL_VARIABLES), db).onSuccessTask(new Continuation<Void, Task<Void>>() {
        @Override
        public Task<Void> then(Task<Void> task) throws Exception {
          return deleteObjects(uuids.subList(MAX_SQL_VARIABLES, uuids.size()), db);
        }
      });
    }

    String[] placeholders = new String[uuids.size()];
    for (int i = 0; i < placeholders.length; i++) {
      placeholders[i] = "?";
    }
    String where = OfflineSQLiteOpenHelper.KEY_UUID + " IN (" + TextUtils.join(",", placeholders) + ")";
    // dynamic args
    String[] args = uuids.toArray(new String[uuids.size()]);
    return db.deleteAsync(OfflineSQLiteOpenHelper.TABLE_OBJECTS, where, args);
  }

  /**
   * Takes an object that has been fetched from the database before and updates it with whatever
   * data is in memory. This will only be used when data comes back from the server after a fetch or
   * a save.
   */
  /* package */ Task<Void> updateDataForObjectAsync(final ParseObject object) {
    Task<ParseObject> fetched;
    // Make sure the object is fetched.
    synchronized (lock) {
      fetched = fetchedObjects.get(object);
      if (fetched == null) {
        return Task.forError(new IllegalStateException(
            "An object cannot be updated if it wasn't fetched."));
      }
    }
    return fetched.continueWithTask(new Continuation<ParseObject, Task<Void>>() {
      @Override
      public Task<Void> then(Task<ParseObject> task) throws Exception {
        if (task.isFaulted()) {
          // Catch CACHE_MISS
          //noinspection ThrowableResultOfMethodCallIgnored
          if (task.getError() instanceof ParseException
              && ((ParseException) task.getError()).getCode() == ParseException.CACHE_MISS) {
            return Task.forResult(null);
          }
          return task.makeVoid();
        }

        return helper.getWritableDatabaseAsync().continueWithTask(new Continuation<ParseSQLiteDatabase, Task<Void>>() {
          @Override
          public Task<Void> then(Task<ParseSQLiteDatabase> task) throws Exception {
            final ParseSQLiteDatabase db = task.getResult();
            return db.beginTransactionAsync().onSuccessTask(new Continuation<Void, Task<Void>>() {
              @Override
              public Task<Void> then(Task<Void> task) throws Exception {
                return updateDataForObjectAsync(object, db).onSuccessTask(new Continuation<Void, Task<Void>>() {
                  @Override
                  public Task<Void> then(Task<Void> task) throws Exception {
                    return db.setTransactionSuccessfulAsync();
                  }
                }).continueWithTask(new Continuation<Void, Task<Void>>() {
                  // } finally {
                  @Override
                  public Task<Void> then(Task<Void> task) throws Exception {
                    db.endTransactionAsync();
                    db.closeAsync();
                    return task;
                  }
                });
              }
            });
          }
        });
      }
    });
  }

  private Task<Void> updateDataForObjectAsync(
      final ParseObject object,
      final ParseSQLiteDatabase db) {
    // Make sure the object has a UUID.
    Task<String> uuidTask;
    synchronized (lock) {
      uuidTask = objectToUuidMap.get(object);
      if (uuidTask == null) {
        // It was fetched, but it has no UUID. That must mean it isn't actually in the database.
        return Task.forResult(null);
      }
    }
    return uuidTask.onSuccessTask(new Continuation<String, Task<Void>>() {
      @Override
      public Task<Void> then(Task<String> task) throws Exception {
        String uuid = task.getResult();
        return updateDataForObjectAsync(uuid, object, db);
      }
    });
  }

  private Task<Void> updateDataForObjectAsync(
      final String uuid,
      final ParseObject object,
      final ParseSQLiteDatabase db) {
    // Now actually encode the object as JSON.
    OfflineEncoder encoder = new OfflineEncoder(db);
    final JSONObject json = object.toRest(encoder);

    return encoder.whenFinished().onSuccessTask(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> task) throws Exception {
        // Put the JSON in the database.
        String className = object.getClassName();
        String objectId = object.getObjectId();
        int isDeletingEventually = json.getInt(ParseObject.KEY_IS_DELETING_EVENTUALLY);

        final ContentValues values = new ContentValues();
        values.put(OfflineSQLiteOpenHelper.KEY_CLASS_NAME, className);
        values.put(OfflineSQLiteOpenHelper.KEY_JSON, json.toString());
        if (objectId != null) {
          values.put(OfflineSQLiteOpenHelper.KEY_OBJECT_ID, objectId);
        }
        values.put(OfflineSQLiteOpenHelper.KEY_IS_DELETING_EVENTUALLY, isDeletingEventually);
        String where = OfflineSQLiteOpenHelper.KEY_UUID + " = ?";
        String[] args = {uuid};
        return db.updateAsync(OfflineSQLiteOpenHelper.TABLE_OBJECTS, values, where, args).makeVoid();
      }
    });
  }

  /* package */ Task<Void> deleteDataForObjectAsync(final ParseObject object) {
    return helper.getWritableDatabaseAsync().continueWithTask(new Continuation<ParseSQLiteDatabase, Task<Void>>() {
      @Override
      public Task<Void> then(Task<ParseSQLiteDatabase> task) throws Exception {
        final ParseSQLiteDatabase db = task.getResult();
        return db.beginTransactionAsync().onSuccessTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return deleteDataForObjectAsync(object, db).onSuccessTask(new Continuation<Void, Task<Void>>() {
              @Override
              public Task<Void> then(Task<Void> task) throws Exception {
                return db.setTransactionSuccessfulAsync();
              }
            }).continueWithTask(new Continuation<Void, Task<Void>>() {
              // } finally {
              @Override
              public Task<Void> then(Task<Void> task) throws Exception {
                db.endTransactionAsync();
                db.closeAsync();
                return task;
              }
            });
          }
        });
      }
    });
  }

  private Task<Void> deleteDataForObjectAsync(final ParseObject object, final ParseSQLiteDatabase db) {
    final Capture<String> uuid = new Capture<>();

    // Make sure the object has a UUID.
    Task<String> uuidTask;
    synchronized (lock) {
      uuidTask = objectToUuidMap.get(object);
      if (uuidTask == null) {
        // It was fetched, but it has no UUID. That must mean it isn't actually in the database.
        return Task.forResult(null);
      }
    }
    uuidTask = uuidTask.onSuccessTask(new Continuation<String, Task<String>>() {
      @Override
      public Task<String> then(Task<String> task) throws Exception {
        uuid.set(task.getResult());
        return task;
      }
    });

    // If the object was the root of a pin, unpin it.
    Task<Void> unpinTask = uuidTask.onSuccessTask(new Continuation<String, Task<Cursor>>() {
      @Override
      public Task<Cursor> then(Task<String> task) throws Exception {
        // Find all the roots for this object.
        String[] select = { OfflineSQLiteOpenHelper.KEY_KEY };
        String where = OfflineSQLiteOpenHelper.KEY_UUID + "=?";
        String[] args = { uuid.get() };
        return db.queryAsync(OfflineSQLiteOpenHelper.TABLE_DEPENDENCIES, select, where, args);
      }
    }).onSuccessTask(new Continuation<Cursor, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Cursor> task) throws Exception {
        // Try to unpin this object from the pin label if it's a root of the ParsePin.
        Cursor cursor = task.getResult();
        List<String> uuids = new ArrayList<>();
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
          uuids.add(cursor.getString(0));
        }
        cursor.close();

        List<Task<Void>> tasks = new ArrayList<>();
        for (final String uuid : uuids) {
          Task<Void> unpinTask = getPointerAsync(uuid, db).onSuccessTask(new Continuation<ParseObject, Task<ParsePin>>() {
            @Override
            public Task<ParsePin> then(Task<ParseObject> task) throws Exception {
              ParsePin pin = (ParsePin) task.getResult();
              return fetchLocallyAsync(pin, db);
            }
          }).continueWithTask(new Continuation<ParsePin, Task<Void>>() {
            @Override
            public Task<Void> then(Task<ParsePin> task) throws Exception {
              ParsePin pin = task.getResult();

              List<ParseObject> modified = pin.getObjects();
              if (modified == null || !modified.contains(object)) {
                return task.makeVoid();
              }

              modified.remove(object);
              if (modified.size() == 0) {
                return unpinAsync(uuid, db);
              }

              pin.setObjects(modified);
              return saveLocallyAsync(pin, true, db);
            }
          });
          tasks.add(unpinTask);
        }

        return Task.whenAll(tasks);
      }
    });

    // Delete the object from the Local Datastore in case it wasn't the root of a pin.
    return unpinTask.onSuccessTask(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> task) throws Exception {
        String where = OfflineSQLiteOpenHelper.KEY_UUID + "=?";
        String[] args = {uuid.get()};
        return db.deleteAsync(OfflineSQLiteOpenHelper.TABLE_DEPENDENCIES, where, args);
      }
    }).onSuccessTask(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> task) throws Exception {
        String where = OfflineSQLiteOpenHelper.KEY_UUID + "=?";
        String[] args = {uuid.get()};
        return db.deleteAsync(OfflineSQLiteOpenHelper.TABLE_OBJECTS, where, args);
      }
    }).onSuccessTask(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> task) throws Exception {
        synchronized (lock) {
          // Clean up
          //TODO (grantland): we should probably clean up uuidToObjectMap and objectToUuidMap, but
          // getting the uuid requires a task and things might get a little funky...
          fetchedObjects.remove(object);
        }
        return task;
      }
    });
  }

  //region ParsePin

  private Task<ParsePin> getParsePin(final String name, ParseSQLiteDatabase db) {
    ParseQuery.State<ParsePin> query = new ParseQuery.State.Builder<>(ParsePin.class)
        .whereEqualTo(ParsePin.KEY_NAME, name)
        .build();

    /* We need to call directly to the OfflineStore since we don't want/need a user to query for
     * ParsePins
     */
    return findAsync(query, null, null, db).onSuccess(new Continuation<List<ParsePin>, ParsePin>() {
      @Override
      public ParsePin then(Task<List<ParsePin>> task) throws Exception {
        ParsePin pin = null;
        if (task.getResult() != null && task.getResult().size() > 0) {
          pin = task.getResult().get(0);
        }

        //TODO (grantland): What do we do if there are more than 1 result?

        if (pin == null) {
          pin = ParseObject.create(ParsePin.class);
          pin.setName(name);
        }
        return pin;
      }
    });
  }

  /* package */ <T extends ParseObject> Task<Void> pinAllObjectsAsync(
      final String name,
      final List<T> objects,
      final boolean includeChildren) {
    return runWithManagedTransaction(new SQLiteDatabaseCallable<Task<Void>>() {
      @Override
      public Task<Void> call(ParseSQLiteDatabase db) {
        return pinAllObjectsAsync(name, objects, includeChildren, db);
      }
    });
  }

  private <T extends ParseObject> Task<Void> pinAllObjectsAsync(
      final String name,
      final List<T> objects,
      final boolean includeChildren,
      final ParseSQLiteDatabase db) {
    if (objects == null || objects.size() == 0) {
      return Task.forResult(null);
    }

    return getParsePin(name, db).onSuccessTask(new Continuation<ParsePin, Task<Void>>() {
      @Override
      public Task<Void> then(Task<ParsePin> task) throws Exception {
        ParsePin pin = task.getResult();

        //TODO (grantland): change to use relations. currently the related PO are only getting saved
        // offline as pointers.
//        ParseRelation<ParseObject> relation = pin.getRelation(KEY_OBJECTS);
//        relation.add(object);

        // Hack to store collections in a pin
        List<ParseObject> modified = pin.getObjects();
        if (modified == null) {
          modified = new ArrayList<ParseObject>(objects);
        } else {
          for (ParseObject object : objects) {
            if (!modified.contains(object)) {
              modified.add(object);
            }
          }
        }
        pin.setObjects(modified);

        if (includeChildren) {
          return saveLocallyAsync(pin, true, db);
        }
        return saveLocallyAsync(pin, pin.getObjects(), db);
      }
    });
  }

  /* package */ <T extends ParseObject> Task<Void> unpinAllObjectsAsync(
      final String name,
      final List<T> objects) {
    return runWithManagedTransaction(new SQLiteDatabaseCallable<Task<Void>>() {
      @Override
      public Task<Void> call(ParseSQLiteDatabase db) {
        return unpinAllObjectsAsync(name, objects, db);
      }
    });
  }

  private <T extends ParseObject> Task<Void> unpinAllObjectsAsync(
      String name,
      final List<T> objects,
      final ParseSQLiteDatabase db) {
    if (objects == null || objects.size() == 0) {
      return Task.forResult(null);
    }

    return getParsePin(name, db).onSuccessTask(new Continuation<ParsePin, Task<Void>>() {
      @Override
      public Task<Void> then(Task<ParsePin> task) throws Exception {
        ParsePin pin = task.getResult();

        //TODO (grantland): change to use relations. currently the related PO are only getting saved
        // offline as pointers.
//        ParseRelation<ParseObject> relation = pin.getRelation(KEY_OBJECTS);
//        relation.remove(object);

        // Hack to store collections in a pin
        List<ParseObject> modified = pin.getObjects();
        if (modified == null) {
          // Unpin a pin that doesn't exist. Wat?
          return Task.forResult(null);
        }

        modified.removeAll(objects);
        if (modified.size() == 0) {
          return unpinAsync(pin, db);
        }
        pin.setObjects(modified);

        return saveLocallyAsync(pin, true, db);
      }
    });
  }

  /* package */ Task<Void> unpinAllObjectsAsync(final String name) {
    return runWithManagedTransaction(new SQLiteDatabaseCallable<Task<Void>>() {
      @Override
      public Task<Void> call(ParseSQLiteDatabase db) {
        return unpinAllObjectsAsync(name, db);
      }
    });
  }

  private Task<Void> unpinAllObjectsAsync(final String name, final ParseSQLiteDatabase db) {
    return getParsePin(name, db).continueWithTask(new Continuation<ParsePin, Task<Void>>() {
      @Override
      public Task<Void> then(Task<ParsePin> task) throws Exception {
        if (task.isFaulted()) {
          return task.makeVoid();
        }
        ParsePin pin = task.getResult();
        return unpinAsync(pin, db);
      }
    });
  }

  /* package */ <T extends ParseObject> Task<List<T>> findFromPinAsync(
      final String name,
      final ParseQuery.State<T> state,
      final ParseUser user) {
    return runWithManagedConnection(new SQLiteDatabaseCallable<Task<List<T>>>() {
      @Override
      public Task<List<T>> call(ParseSQLiteDatabase db) {
        return findFromPinAsync(name, state, user, db);
      }
    });
  }

  private <T extends ParseObject> Task<List<T>> findFromPinAsync(
      final String name,
      final ParseQuery.State<T> state,
      final ParseUser user,
      final ParseSQLiteDatabase db) {
    Task<ParsePin> task;
    if (name != null) {
      task = getParsePin(name, db);
    } else {
      task = Task.forResult(null);
    }
    return task.onSuccessTask(new Continuation<ParsePin, Task<List<T>>>() {
      @Override
      public Task<List<T>> then(Task<ParsePin> task) throws Exception {
        ParsePin pin = task.getResult();
        return findAsync(state, user, pin, false, db);
      }
    });
  }

  /* package */ <T extends ParseObject> Task<Integer> countFromPinAsync(
      final String name,
      final ParseQuery.State<T> state,
      final ParseUser user) {
    return runWithManagedConnection(new SQLiteDatabaseCallable<Task<Integer>>() {
      @Override
      public Task<Integer> call(ParseSQLiteDatabase db) {
        return countFromPinAsync(name, state, user, db);
      }
    });
  }

  private <T extends ParseObject> Task<Integer> countFromPinAsync(
      final String name,
      final ParseQuery.State<T> state,
      final ParseUser user,
      final ParseSQLiteDatabase db) {
    Task<ParsePin> task;
    if (name != null) {
      task = getParsePin(name, db);
    } else {
      task = Task.forResult(null);
    }
    return task.onSuccessTask(new Continuation<ParsePin, Task<Integer>>() {
      @Override
      public Task<Integer> then(Task<ParsePin> task) throws Exception {
        ParsePin pin = task.getResult();
        return findAsync(state, user, pin, true, db).onSuccess(new Continuation<List<T>, Integer>() {
          @Override
          public Integer then(Task<List<T>> task) throws Exception {
            return task.getResult().size();
          }
        });
      }
    });
  }

  //endregion

  //region Single Instance

  /**
   * In-memory map of (className, objectId) -> ParseObject. This is used so that we can always
   * return the same instance for a given object. Objects in this map may or may not be in the
   * database.
   */
  private final WeakValueHashMap<Pair<String, String>, ParseObject>
      classNameAndObjectIdToObjectMap = new WeakValueHashMap<>();

  /**
   * This should be called by the ParseObject constructor notify the store that there is an object
   * with this className and objectId.
   */
  /* package */ void registerNewObject(ParseObject object) {
    synchronized (lock) {
      String objectId = object.getObjectId();
      if (objectId != null) {
        String className = object.getClassName();
        Pair<String, String> classNameAndObjectId = Pair.create(className, objectId);
        classNameAndObjectIdToObjectMap.put(classNameAndObjectId, object);
      }
    }
  }

  /* package */ void unregisterObject(ParseObject object) {
    synchronized (lock) {
      String objectId = object.getObjectId();
      if (objectId != null) {
        classNameAndObjectIdToObjectMap.remove(Pair.create(object.getClassName(), objectId));
      }
    }
  }

  /**
   * This should only ever be called from ParseObject.createWithoutData().
   *
   * @return a pair of ParseObject and Boolean. The ParseObject is the object. The Boolean is true
   *         iff the object was newly created.
   */
  /* package */ ParseObject getObject(String className, String objectId) {
    if (objectId == null) {
      throw new IllegalStateException("objectId cannot be null.");
    }

    Pair<String, String> classNameAndObjectId = Pair.create(className, objectId);
    // This lock should never be held by anyone doing disk or database access.
    synchronized (lock) {
      return classNameAndObjectIdToObjectMap.get(classNameAndObjectId);
    }
  }

  /**
   * When an object is finished saving, it gets an objectId. Then it should call this method to
   * clean up the bookeeping around ids.
   */
  /* package */ void updateObjectId(ParseObject object, String oldObjectId, String newObjectId) {
    if (oldObjectId != null) {
      if (oldObjectId.equals(newObjectId)) {
        return;
      }
      /**
       * Special case for re-saving installation if it was deleted on the server
       * @see ParseInstallation#saveAsync(String, Task)
       */
      if (object instanceof ParseInstallation
          && newObjectId == null) {
        synchronized (lock) {
          classNameAndObjectIdToObjectMap.remove(Pair.create(object.getClassName(), oldObjectId));
        }
        return;
      } else {
        throw new RuntimeException("objectIds cannot be changed in offline mode.");
      }
    }

    String className = object.getClassName();
    Pair<String, String> classNameAndNewObjectId = Pair.create(className, newObjectId);

    synchronized (lock) {
      // See if there's already an entry for the new object id.
      ParseObject existing = classNameAndObjectIdToObjectMap.get(classNameAndNewObjectId);
      if (existing != null && existing != object) {
        throw new RuntimeException("Attempted to change an objectId to one that's "
            + "already known to the Offline Store.");
      }

      // Okay, all clear to add the new reference.
      classNameAndObjectIdToObjectMap.put(classNameAndNewObjectId, object);
    }
  }

  //endregion

  /**
   * Wraps SQLite operations with a managed SQLite connection.
   */
  private <T> Task<T> runWithManagedConnection(final SQLiteDatabaseCallable<Task<T>> callable) {
    return helper.getWritableDatabaseAsync().onSuccessTask(new Continuation<ParseSQLiteDatabase, Task<T>>() {
      @Override
      public Task<T> then(Task<ParseSQLiteDatabase> task) throws Exception {
        final ParseSQLiteDatabase db = task.getResult();
        return callable.call(db).continueWithTask(new Continuation<T, Task<T>>() {
          @Override
          public Task<T> then(Task<T> task) throws Exception {
            db.closeAsync();
            return task;
          }
        });
      }
    });
  }

  /**
   * Wraps SQLite operations with a managed SQLite connection and transaction.
   */
  private Task<Void> runWithManagedTransaction(final SQLiteDatabaseCallable<Task<Void>> callable) {
    return helper.getWritableDatabaseAsync().onSuccessTask(new Continuation<ParseSQLiteDatabase, Task<Void>>() {
      @Override
      public Task<Void> then(Task<ParseSQLiteDatabase> task) throws Exception {
        final ParseSQLiteDatabase db = task.getResult();
        return db.beginTransactionAsync().onSuccessTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return callable.call(db).onSuccessTask(new Continuation<Void, Task<Void>>() {
              @Override
              public Task<Void> then(Task<Void> task) throws Exception {
                return db.setTransactionSuccessfulAsync();
              }
            }).continueWithTask(new Continuation<Void, Task<Void>>() {
              @Override
              public Task<Void> then(Task<Void> task) throws Exception {
                db.endTransactionAsync();
                db.closeAsync();
                return task;
              }
            });
          }
        });
      }
    });
  }

  private interface SQLiteDatabaseCallable<T> {
    T call(ParseSQLiteDatabase db);
  }

  /*
   * Methods for testing.
   */

  /**
   * Clears all in-memory caches so that data must be retrieved from disk.
   */
  void simulateReboot() {
    synchronized (lock) {
      uuidToObjectMap.clear();
      objectToUuidMap.clear();
      classNameAndObjectIdToObjectMap.clear();
      fetchedObjects.clear();
    }
  }

  /**
   * Clears the database on disk.
   */
  void clearDatabase(Context context) {
    helper.clearDatabase(context);
  }
}
