/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import android.util.Pair
import com.parse.ParseObject.Companion.create
import com.parse.boltsinternal.Capture
import com.parse.boltsinternal.Continuation
import com.parse.boltsinternal.Task
import com.parse.boltsinternal.TaskCompletionSource
import org.json.JSONException
import org.json.JSONObject
import java.util.*

internal open class OfflineStore     /* package */(  // Helper for accessing the database.
    private val helper: OfflineSQLiteOpenHelper
) {
    // Lock for all members of the store.
    private val lock = Any()

    /**
     * In-memory map of UUID -> ParseObject. This is used so that we can always return the same
     * instance for a given object. The only objects in this map are ones that are in the database.
     */
    private val uuidToObjectMap = WeakValueHashMap<String, ParseObject?>()

    /**
     * In-memory map of ParseObject -> UUID. This is used to that when we see an unsaved ParseObject
     * that's already in the database, we can update the same record in the database. It stores a Task
     * instead of the String, because one thread may want to reserve the spot. Once the task is
     * finished, there will be a row for this UUID in the database.
     */
    private val objectToUuidMap = WeakHashMap<ParseObject?, Task<String>>()

    /**
     * In-memory set of ParseObjects that have been fetched from the local database already. If the
     * object is in the map, a fetch of it has been started. If the value is a finished task, then the
     * fetch was completed.
     */
    private val fetchedObjects = WeakHashMap<ParseObject, Task<ParseObject>>()

    /**
     * In-memory map of (className, objectId) -> ParseObject. This is used so that we can always
     * return the same instance for a given object. Objects in this map may or may not be in the
     * database.
     */
    private val classNameAndObjectIdToObjectMap =
        WeakValueHashMap<Pair<String?, String?>, ParseObject>()

    /**
     * Used by the static method to create the singleton.
     */
    /* package */
    constructor(context: Context?) : this(OfflineSQLiteOpenHelper(context)) {}

    /**
     * Gets the UUID for the given object, if it has one. Otherwise, creates a new UUID for the object
     * and adds a new row to the database for the object with no data.
     */
    private fun getOrCreateUUIDAsync(`object`: ParseObject, db: ParseSQLiteDatabase): Task<String> {
        val newUUID = UUID.randomUUID().toString()
        val tcs = TaskCompletionSource<String>()
        synchronized(lock) {
            val uuidTask = objectToUuidMap[`object`]
            if (uuidTask != null) {
                return uuidTask
            }

            // The object doesn't have a UUID yet, so we're gonna have to make one.
            objectToUuidMap[`object`] = tcs.task
            uuidToObjectMap.put(newUUID, `object`)
            fetchedObjects.put(`object`, tcs.task.onSuccess { `object` })
        }

        /*
         * We need to put a placeholder row in the database so that later on, the save can just be an
         * update. This could be a pointer to an object that itself never gets saved offline, in which
         * case the consumer will just have to deal with that.
         */
        val values = ContentValues()
        values.put(OfflineSQLiteOpenHelper.KEY_UUID, newUUID)
        values.put(OfflineSQLiteOpenHelper.KEY_CLASS_NAME, `object`.className)
        db.insertOrThrowAsync(OfflineSQLiteOpenHelper.TABLE_OBJECTS, values).continueWith {
            // This will signal that the UUID does represent a row in the database.
            tcs.setResult(newUUID)
            null
        }
        return tcs.task
    }

    /**
     * Gets an unfetched pointer to an object in the db, based on its uuid. The object may or may not
     * be in memory, but it must be in the database. If it is already in memory, that instance will be
     * returned. Since this is only for creating pointers to objects that are referenced by other
     * objects in the data store, that's a fair assumption.
     *
     * @param uuid The object to retrieve.
     * @param db   The database instance to retrieve from.
     * @return The object with that UUID.
     */
    private fun <T : ParseObject?> getPointerAsync(
        uuid: String,
        db: ParseSQLiteDatabase
    ): Task<T> {
        synchronized(lock) {
            val existing = uuidToObjectMap[uuid] as T?
            if (existing != null) {
                return Task.forResult(existing)
            }
        }

        /*
         * We want to just return the pointer, but we have to look in the database to know if there's
         * something with this classname and object id already.
         */
        val select =
            arrayOf(OfflineSQLiteOpenHelper.KEY_CLASS_NAME, OfflineSQLiteOpenHelper.KEY_OBJECT_ID)
        val where = OfflineSQLiteOpenHelper.KEY_UUID + " = ?"
        val args = arrayOf(uuid)
        return db.queryAsync(OfflineSQLiteOpenHelper.TABLE_OBJECTS, select, where, args)
            .onSuccess { task: Task<Cursor> ->
                val cursor = task.result
                cursor.moveToFirst()
                if (cursor.isAfterLast) {
                    cursor.close()
                    throw IllegalStateException("Attempted to find non-existent uuid $uuid")
                }
                synchronized(lock) {

                    // We need to check again since another task might have come around and added it to
                    // the map.
                    //TODO (grantland): Maybe we should insert a Task that is resolved when the query
                    // completes like we do in getOrCreateUUIDAsync?
                    val existing = uuidToObjectMap[uuid] as T?
                    if (existing != null) {
                        return@onSuccess existing
                    }
                    val className = cursor.getString(0)
                    val objectId = cursor.getString(1)
                    cursor.close()
                    val pointer = ParseObject.createWithoutData(className, objectId) as T?
                    /*
                         * If it doesn't have an objectId, we don't really need the UUID, and this simplifies
                         * some other logic elsewhere if we only update the map for new objects.
                         */if (objectId == null) {
                    uuidToObjectMap.put(uuid, pointer)
                    objectToUuidMap[pointer] = Task.forResult(uuid)
                }
                    return@onSuccess pointer
                }
            }
    }

    /**
     * Runs a ParseQuery against the store's contents.
     *
     * @return The objects that match the query's constraints.
     */
    /* package for OfflineQueryLogic */
    fun <T : ParseObject> findAsync(
        query: ParseQuery.State<T>,
        user: ParseUser?,
        pin: ParsePin?,
        db: ParseSQLiteDatabase
    ): Task<List<T>?> {
        return findAsync(query, user, pin, false, db)
    }

    /**
     * Runs a ParseQuery against the store's contents. May cause any instances of T to get fetched
     * from the offline database. TODO(klimt): Should the query consider objects that are in memory,
     * but not in the offline store?
     *
     * @param query   The query.
     * @param user    The user making the query.
     * @param pin     (Optional) The pin we are querying across. If null, all pins.
     * @param isCount True if we are doing a count.
     * @param db      The SQLiteDatabase.
     * @param <T>     Subclass of ParseObject.
     * @return The objects that match the query's constraints.
    </T> */
    private fun <T : ParseObject> findAsync(
        query: ParseQuery.State<T>,
        user: ParseUser?,
        pin: ParsePin?,
        isCount: Boolean,
        db: ParseSQLiteDatabase
    ): Task<List<T>?> {
        /*
         * This is currently unused, but is here to allow future querying across objects that are in the
         * process of being deleted eventually.
         */
        val includeIsDeletingEventually = false
        val queryLogic = OfflineQueryLogic(this)
        val results: MutableList<T> = ArrayList()
        val queryTask: Task<Cursor>
        if (pin == null) {
            val table = OfflineSQLiteOpenHelper.TABLE_OBJECTS
            val select = arrayOf(OfflineSQLiteOpenHelper.KEY_UUID)
            var where = OfflineSQLiteOpenHelper.KEY_CLASS_NAME + "=?"
            if (!includeIsDeletingEventually) {
                where += " AND " + OfflineSQLiteOpenHelper.KEY_IS_DELETING_EVENTUALLY + "=0"
            }
            val args = arrayOf(query.className())
            queryTask = db.queryAsync(table, select, where, args)
        } else {
            val uuidTask = objectToUuidMap[pin]
                ?: // Pin was never saved locally, therefore there won't be any results.
                return Task.forResult(results)
            queryTask = uuidTask.onSuccessTask { task: Task<String> ->
                val uuid = task.result
                val table = OfflineSQLiteOpenHelper.TABLE_OBJECTS + " A " +
                        " INNER JOIN " + OfflineSQLiteOpenHelper.TABLE_DEPENDENCIES + " B " +
                        " ON A." + OfflineSQLiteOpenHelper.KEY_UUID + "=B." + OfflineSQLiteOpenHelper.KEY_UUID
                val select = arrayOf("A." + OfflineSQLiteOpenHelper.KEY_UUID)
                var where = OfflineSQLiteOpenHelper.KEY_CLASS_NAME + "=?" +
                        " AND " + OfflineSQLiteOpenHelper.KEY_KEY + "=?"
                if (!includeIsDeletingEventually) {
                    where += " AND " + OfflineSQLiteOpenHelper.KEY_IS_DELETING_EVENTUALLY + "=0"
                }
                val args = arrayOf(query.className(), uuid)
                db.queryAsync(table, select, where, args)
            }
        }
        return queryTask.onSuccessTask { task: Task<Cursor> ->
            val cursor = task.result
            val uuids: MutableList<String> = ArrayList()
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                uuids.add(cursor.getString(0))
                cursor.moveToNext()
            }
            cursor.close()

            // Find objects that match the where clause.
            val matcher = queryLogic.createMatcher(query, user)
            var checkedAllObjects = Task.forResult<Void?>(null)
            for (uuid in uuids) {
                val `object` = Capture<T>()
                checkedAllObjects =
                    checkedAllObjects.onSuccessTask(Continuation<Void?, Task<T>> {
                        getPointerAsync(
                            uuid,
                            db
                        )
                    }).onSuccessTask { task15: Task<T> ->
                        `object`.set(task15.result)
                        fetchLocallyAsync(`object`.get(), db)
                    }.onSuccessTask {
                        if (!`object`.get()!!.isDataAvailable()) {
                            return@onSuccessTask Task.forResult(false)
                        }
                        matcher.matchesAsync(`object`.get(), db)
                    }.onSuccess { task13: Task<Boolean> ->
                        if (task13.result) {
                            results.add(`object`.get())
                        }
                        null
                    }
            }
            checkedAllObjects
        }.onSuccessTask { task: Task<Void?>? ->
            // Sort by any sort operators.
            OfflineQueryLogic.sort(results, query)

            // Apply the skip.
            var trimmedResults: List<T> = results
            var skip = query.skip()
            if (!isCount && skip >= 0) {
                skip = Math.min(query.skip(), trimmedResults.size)
                trimmedResults = trimmedResults.subList(skip, trimmedResults.size)
            }

            // Trim to the limit.
            val limit = query.limit()
            if (!isCount && limit >= 0 && trimmedResults.size > limit) {
                trimmedResults = trimmedResults.subList(0, limit)
            }

            // Fetch the includes.
            var fetchedIncludesTask = Task.forResult<Void?>(null)
            for (`object` in trimmedResults) {
                fetchedIncludesTask = fetchedIncludesTask.onSuccessTask { task12: Task<Void?>? ->
                    OfflineQueryLogic.fetchIncludesAsync(
                        this@OfflineStore,
                        `object`,
                        query,
                        db
                    )
                }
            }
            val finalTrimmedResults = trimmedResults
            fetchedIncludesTask.onSuccess { task1: Task<Void?>? -> finalTrimmedResults }
        }
    }

    /**
     * Gets the data for the given object from the offline database. Returns a task that will be
     * completed if data for the object was available. If the object is not in the cache, the task
     * will be faulted, with a CACHE_MISS error.
     *
     * @param object The object to fetch.
     * @param db     A database connection to use.
     */
    /* package for OfflineQueryLogic */
    fun <T : ParseObject> fetchLocallyAsync(
        `object`: T,
        db: ParseSQLiteDatabase
    ): Task<T> {
        val tcs = TaskCompletionSource<T>()
        var uuidTask: Task<String>?
        synchronized(lock) {
            if (fetchedObjects.containsKey(`object`)) {
                /*
                 * The object has already been fetched from the offline store, so any data that's in there
                 * is already reflected in the in-memory version. There's nothing more to do.
                 */
                return fetchedObjects[`object`] as Task<T>
            }

            /*
             * Put a placeholder so that anyone else who attempts to fetch this object will just wait for
             * this call to finish doing it.
             */fetchedObjects[`object`] = tcs.task as Task<ParseObject>
            uuidTask = objectToUuidMap[`object`]
        }
        val className = `object`.className
        val objectId = `object`.objectId

        /*
         * If this gets set, then it will contain data from the offline store that needs to be merged
         * into the existing object in memory.
         */
        var jsonStringTask = Task.forResult<String?>(null)
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
                val select = arrayOf(OfflineSQLiteOpenHelper.KEY_JSON)
                val where = OfflineSQLiteOpenHelper.KEY_UUID + " = ?"
                val uuid = Capture<String>()
                jsonStringTask = uuidTask!!.onSuccessTask { task: Task<String> ->
                    uuid.set(task.result)
                    val args = arrayOf(uuid.get())
                    db.queryAsync(OfflineSQLiteOpenHelper.TABLE_OBJECTS, select, where, args)
                }.onSuccess { task: Task<Cursor> ->
                    val cursor = task.result
                    cursor.moveToFirst()
                    if (cursor.isAfterLast) {
                        cursor.close()
                        throw IllegalStateException("Attempted to find non-existent uuid " + uuid.get())
                    }
                    val json = cursor.getString(0)
                    cursor.close()
                    json
                }
            }
        } else {
            if (uuidTask != null) {
                /*
                 * This object is an existing ParseObject, and we must've already pulled its data out of the
                 * offline store, or else we wouldn't know its UUID. This should never happen.
                 */
                tcs.setError(
                    IllegalStateException(
                        "This object must have already been "
                                + "fetched from the local datastore, but isn't marked as fetched."
                    )
                )
                synchronized(lock) {
                    // Forget we even tried to fetch this object, so that retries will actually... retry.
                    fetchedObjects.remove(`object`)
                }
                return tcs.task
            }

            /*
             * We've got a pointer to an existing ParseObject, but we've never pulled its data out of the
             * offline store. Since fetching from the server forces a fetch from the offline store, that
             * means this is a pointer. We need to try to find any existing entry for this object in the
             * database.
             */
            val select = arrayOf(OfflineSQLiteOpenHelper.KEY_JSON, OfflineSQLiteOpenHelper.KEY_UUID)
            val where = String.format(
                "%s = ? AND %s = ?", OfflineSQLiteOpenHelper.KEY_CLASS_NAME,
                OfflineSQLiteOpenHelper.KEY_OBJECT_ID
            )
            val args = arrayOf(className, objectId)
            jsonStringTask =
                db.queryAsync(OfflineSQLiteOpenHelper.TABLE_OBJECTS, select, where, args)
                    .onSuccess { task: Task<Cursor> ->
                        val cursor = task.result
                        cursor.moveToFirst()
                        if (cursor.isAfterLast) {
                            /*
                                     * This is a pointer that came from Parse that references an object that has
                                     * never been saved in the offline store before. This just means there's no data
                                     * in the store that needs to be merged into the object.
                                     */
                            cursor.close()
                            throw ParseException(
                                ParseException.CACHE_MISS,
                                "This object is not available in the offline cache."
                            )
                        }

                        // we should fetch its data and record its UUID for future reference.
                        val jsonString = cursor.getString(0)
                        val newUUID = cursor.getString(1)
                        cursor.close()
                        synchronized(lock) {

                            /*
                                     * It's okay to put this object into the uuid map. No one will try to fetch
                                     * it, because it's already in the fetchedObjects map. And no one will try to
                                     * save to it without fetching it first, so everything should be just fine.
                                     */objectToUuidMap[`object`] = Task.forResult(newUUID)
                            uuidToObjectMap.put(newUUID, `object`)
                        }
                        jsonString
                    }
        }
        return jsonStringTask.onSuccessTask(Continuation { task: Task<String?> ->
            val jsonString = task.result
                ?: /*
                 * This means we tried to fetch an object from the database that was never actually saved
                 * locally. This probably means that its parent object was saved locally and we just
                 * created a pointer to this object. This should be considered a cache miss.
                 */
                return@Continuation Task.forError(
                    ParseException(
                        ParseException.CACHE_MISS,
                        "Attempted to fetch an object offline which was never saved to the offline cache."
                    )
                )
            val json: JSONObject = try {
                /*
                 * We can assume that whatever is in the database is the last known server state. The only
                 * things to maintain from the in-memory object are any changes since the object was last
                 * put in the database.
                 */
                JSONObject(jsonString)
            } catch (e: JSONException) {
                return@Continuation Task.forError(e)
            }

            // Fetch all the offline objects before we decode.
            val offlineObjects: MutableMap<String, Task<ParseObject>> = HashMap()
            object : ParseTraverser() {
                override fun visit(object1: Any): Boolean {
                    if (object1 is JSONObject
                        && object1.optString("__type") == "OfflineObject"
                    ) {
                        val uuid = object1.optString("uuid")
                        offlineObjects[uuid] = getPointerAsync(uuid, db)
                    }
                    return true
                }
            }.setTraverseParseObjects(false).setYieldRoot(false).traverse(json)
            Task.whenAll(offlineObjects.values).onSuccess<Void?> { task1: Task<Void?>? ->
                `object`.mergeREST(`object`.state, json, OfflineDecoder(offlineObjects))
                null
            }
        } as Continuation<String?, Task<Void?>>).continueWithTask { task: Task<Void?> ->
            if (task.isCancelled) {
                tcs.setCancelled()
            } else if (task.isFaulted) {
                tcs.setError(task.error)
            } else {
                tcs.setResult(`object`)
            }
            tcs.task
        }
    }

    /**
     * Gets the data for the given object from the offline database. Returns a task that will be
     * completed if data for the object was available. If the object is not in the cache, the task
     * will be faulted, with a CACHE_MISS error.
     *
     * @param object The object to fetch.
     */
    /* package */
    fun <T : ParseObject> fetchLocallyAsync(`object`: T): Task<T> {
        return runWithManagedConnection(object : SQLiteDatabaseCallable<Task<T>> {
            override fun call(db: ParseSQLiteDatabase): Task<T> {
                return fetchLocallyAsync(
                    `object`,
                    db
                )
            }
        })
    }

    /**
     * Stores a single object in the local database. If the object is a pointer, isn't dirty, and has
     * an objectId already, it may not be saved, since it would provide no useful data.
     *
     * @param object The object to save.
     * @param db     A database connection to use.
     */
    private fun saveLocallyAsync(
        key: String, `object`: ParseObject, db: ParseSQLiteDatabase
    ): Task<Void?> {
        // If this is just a clean, unfetched pointer known to Parse, then there is nothing to save.
        if (`object`.objectId != null && !`object`.isDataAvailable() && !`object`.hasChanges()
            && !`object`.hasOutstandingOperations()
        ) {
            return Task.forResult(null)
        }
        val uuidCapture = Capture<String>()

        // Make sure we have a UUID for the object to be saved.
        return getOrCreateUUIDAsync(`object`, db).onSuccessTask { task: Task<String> ->
            val uuid = task.result
            uuidCapture.set(uuid)
            updateDataForObjectAsync(uuid, `object`, db)
        }.onSuccessTask { task: Task<Void?>? ->
            val values = ContentValues()
            values.put(OfflineSQLiteOpenHelper.KEY_KEY, key)
            values.put(OfflineSQLiteOpenHelper.KEY_UUID, uuidCapture.get())
            db.insertWithOnConflict(
                OfflineSQLiteOpenHelper.TABLE_DEPENDENCIES, values,
                SQLiteDatabase.CONFLICT_IGNORE
            )
        }
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
     *
     * Any objects previously stored with the same key will be removed from the local database.
     *
     * @param object             Root object
     * @param includeAllChildren `true` to recursively save all pointers.
     * @param db                 DB connection
     * @return A Task that will be resolved when saving is complete
     */
    private fun saveLocallyAsync(
        `object`: ParseObject, includeAllChildren: Boolean, db: ParseSQLiteDatabase
    ): Task<Void?> {
        val objectsInTree = ArrayList<ParseObject>()
        // Fetch all objects locally in case they are being re-added
        if (!includeAllChildren) {
            objectsInTree.add(`object`)
        } else {
            object : ParseTraverser() {
                override fun visit(`object`: Any): Boolean {
                    if (`object` is ParseObject) {
                        objectsInTree.add(`object`)
                    }
                    return true
                }
            }.setYieldRoot(true).setTraverseParseObjects(true).traverse(`object`)
        }
        return saveLocallyAsync(`object`, objectsInTree, db)
    }

    private fun saveLocallyAsync(
        `object`: ParseObject, children: List<ParseObject>?, db: ParseSQLiteDatabase
    ): Task<Void?> {
        val objects: MutableList<ParseObject> =
            if (children != null) ArrayList(children) else ArrayList()
        if (!objects.contains(`object`)) {
            objects.add(`object`)
        }

        // Call saveLocallyAsync for each of them individually.
        val tasks: MutableList<Task<Void>> = ArrayList()
        for (obj in objects) {
            tasks.add(fetchLocallyAsync(obj, db).makeVoid())
        }
        return Task.whenAll(tasks)
            .continueWithTask { task: Task<Void?>? -> objectToUuidMap[`object`] }
            .onSuccessTask { task: Task<String> ->
                val uuid = task.result
                    ?: // The root object was never stored in the offline store, so nothing to unpin.
                    return@onSuccessTask null
                unpinAsync(uuid, db)
            }.onSuccessTask { task: Task<Void?>? -> getOrCreateUUIDAsync(`object`, db) }
            .onSuccessTask { task: Task<String> ->
                val uuid = task.result

                // Call saveLocallyAsync for each of them individually.
                val tasks1: MutableList<Task<Void?>> = ArrayList()
                for (obj in objects) {
                    tasks1.add(saveLocallyAsync(uuid, obj, db))
                }
                Task.whenAll(tasks1)
            }
    }

    private fun unpinAsync(`object`: ParseObject, db: ParseSQLiteDatabase): Task<Void?> {
        val uuidTask = objectToUuidMap[`object`]
            ?: // The root object was never stored in the offline store, so nothing to unpin.
            return Task.forResult(null)
        return uuidTask.continueWithTask { task: Task<String> ->
            val uuid = task.result
                ?: // The root object was never stored in the offline store, so nothing to unpin.
                return@continueWithTask Task.forResult<Void?>(null)
            unpinAsync(uuid, db)
        }
    }

    private fun unpinAsync(key: String, db: ParseSQLiteDatabase): Task<Void?> {
        val uuidsToDelete: MutableList<String> = LinkedList()
        // A continueWithTask that ends with "return task" is essentially a try-finally.
        return Task.forResult(null as Void?).continueWithTask { task: Task<Void?>? ->
            // Fetch all uuids from Dependencies for key=? grouped by uuid having a count of 1
            val sql =
                "SELECT " + OfflineSQLiteOpenHelper.KEY_UUID + " FROM " + OfflineSQLiteOpenHelper.TABLE_DEPENDENCIES +
                        " WHERE " + OfflineSQLiteOpenHelper.KEY_KEY + "=? AND " + OfflineSQLiteOpenHelper.KEY_UUID + " IN (" +
                        " SELECT " + OfflineSQLiteOpenHelper.KEY_UUID + " FROM " + OfflineSQLiteOpenHelper.TABLE_DEPENDENCIES +
                        " GROUP BY " + OfflineSQLiteOpenHelper.KEY_UUID +
                        " HAVING COUNT(" + OfflineSQLiteOpenHelper.KEY_UUID + ")=1" +
                        ")"
            val args = arrayOf(key)
            db.rawQueryAsync(sql, args)
        }.onSuccessTask { task: Task<Cursor> ->
            // DELETE FROM Objects
            val cursor = task.result
            while (cursor.moveToNext()) {
                uuidsToDelete.add(cursor.getString(0))
            }
            cursor.close()
            deleteObjects(uuidsToDelete, db)
        }.onSuccessTask { task: Task<Void?>? ->
            // DELETE FROM Dependencies
            val where = OfflineSQLiteOpenHelper.KEY_KEY + "=?"
            val args = arrayOf(key)
            db.deleteAsync(OfflineSQLiteOpenHelper.TABLE_DEPENDENCIES, where, args)
        }.onSuccess { task: Task<Void>? ->
            synchronized(lock) {
                // Remove uuids from memory
                for (uuid in uuidsToDelete) {
                    val `object` = uuidToObjectMap[uuid]
                    if (`object` != null) {
                        objectToUuidMap.remove(`object`)
                        uuidToObjectMap.remove(uuid)
                    }
                }
            }
            null
        }
    }

    private fun deleteObjects(uuids: List<String>, db: ParseSQLiteDatabase): Task<Void?> {
        if (uuids.size <= 0) {
            return Task.forResult(null)
        }

        // SQLite has a max 999 SQL variables in a statement, so we need to split it up into manageable
        // chunks. We can do this because we're already in a transaction.
        if (uuids.size > MAX_SQL_VARIABLES) {
            return deleteObjects(
                uuids.subList(0, MAX_SQL_VARIABLES),
                db
            ).onSuccessTask { task: Task<Void?>? ->
                deleteObjects(
                    uuids.subList(
                        MAX_SQL_VARIABLES, uuids.size
                    ), db
                )
            }
        }
        val placeholders = arrayOfNulls<String>(uuids.size)
        for (i in placeholders.indices) {
            placeholders[i] = "?"
        }
        val where =
            OfflineSQLiteOpenHelper.KEY_UUID + " IN (" + TextUtils.join(",", placeholders) + ")"
        // dynamic args
        val args = uuids.toTypedArray()
        return db.deleteAsync(OfflineSQLiteOpenHelper.TABLE_OBJECTS, where, args)
    }

    /**
     * Takes an object that has been fetched from the database before and updates it with whatever
     * data is in memory. This will only be used when data comes back from the server after a fetch or
     * a save.
     */
    /* package */
    fun updateDataForObjectAsync(`object`: ParseObject): Task<Void> {
        var fetched: Task<ParseObject>?
        // Make sure the object is fetched.
        synchronized(lock) {
            fetched = fetchedObjects[`object`]
            if (fetched == null) {
                return Task.forError(
                    IllegalStateException(
                        "An object cannot be updated if it wasn't fetched."
                    )
                )
            }
        }
        return fetched!!.continueWithTask { task: Task<ParseObject> ->
            if (task.isFaulted) {
                // Catch CACHE_MISS
                if (task.error is ParseException
                    && (task.error as ParseException).code == ParseException.CACHE_MISS
                ) {
                    return@continueWithTask Task.forResult<Void?>(null)
                }
                return@continueWithTask task.makeVoid()
            }
            helper.writableDatabaseAsync.continueWithTask { task14: Task<ParseSQLiteDatabase> ->
                val db = task14.result
                db.beginTransactionAsync().onSuccessTask { task13: Task<Void?>? ->
                    updateDataForObjectAsync(
                        `object`,
                        db
                    ).onSuccessTask { task12: Task<Void?>? -> db.setTransactionSuccessfulAsync() }
                        .continueWithTask { task1: Task<Void>? ->
                            db.endTransactionAsync()
                            db.closeAsync()
                            task1
                        }
                }
            }
        }
    }

    private fun updateDataForObjectAsync(
        `object`: ParseObject,
        db: ParseSQLiteDatabase
    ): Task<Void?> {
        // Make sure the object has a UUID.
        var uuidTask: Task<String>?
        synchronized(lock) {
            uuidTask = objectToUuidMap[`object`]
            if (uuidTask == null) {
                // It was fetched, but it has no UUID. That must mean it isn't actually in the database.
                return Task.forResult(null)
            }
        }
        return uuidTask!!.onSuccessTask { task: Task<String> ->
            val uuid = task.result
            updateDataForObjectAsync(uuid, `object`, db)
        }
    }

    private fun updateDataForObjectAsync(
        uuid: String,
        `object`: ParseObject,
        db: ParseSQLiteDatabase
    ): Task<Void?> {
        // Now actually encode the object as JSON.
        val encoder = OfflineEncoder(db)
        val json = `object`.toRest(encoder)
        return encoder.whenFinished().onSuccessTask { task: Task<Void?>? ->
            // Put the JSON in the database.
            val className = `object`.className
            val objectId = `object`.objectId
            val isDeletingEventually = json.getInt(ParseObject.KEY_IS_DELETING_EVENTUALLY)
            val values = ContentValues()
            values.put(OfflineSQLiteOpenHelper.KEY_CLASS_NAME, className)
            values.put(OfflineSQLiteOpenHelper.KEY_JSON, json.toString())
            if (objectId != null) {
                values.put(OfflineSQLiteOpenHelper.KEY_OBJECT_ID, objectId)
            }
            values.put(OfflineSQLiteOpenHelper.KEY_IS_DELETING_EVENTUALLY, isDeletingEventually)
            val where = OfflineSQLiteOpenHelper.KEY_UUID + " = ?"
            val args = arrayOf(uuid)
            db.updateAsync(OfflineSQLiteOpenHelper.TABLE_OBJECTS, values, where, args).makeVoid()
        }
    }

    /* package */
    fun deleteDataForObjectAsync(`object`: ParseObject): Task<Void> {
        return helper.writableDatabaseAsync.continueWithTask { task: Task<ParseSQLiteDatabase> ->
            val db = task.result
            db.beginTransactionAsync().onSuccessTask { task13: Task<Void?>? ->
                deleteDataForObjectAsync(
                    `object`,
                    db
                ).onSuccessTask { task12: Task<Void?>? -> db.setTransactionSuccessfulAsync() }
                    .continueWithTask { task1: Task<Void>? ->
                        db.endTransactionAsync()
                        db.closeAsync()
                        task1
                    }
            }
        }
    }

    private fun deleteDataForObjectAsync(
        `object`: ParseObject,
        db: ParseSQLiteDatabase
    ): Task<Void?> {
        val uuid = Capture<String>()

        // Make sure the object has a UUID.
        var uuidTask: Task<String>?
        synchronized(lock) {
            uuidTask = objectToUuidMap[`object`]
            if (uuidTask == null) {
                // It was fetched, but it has no UUID. That must mean it isn't actually in the database.
                return Task.forResult(null)
            }
        }
        uuidTask = uuidTask!!.onSuccessTask { task: Task<String> ->
            uuid.set(task.result)
            task
        }

        // If the object was the root of a pin, unpin it.
        val unpinTask = uuidTask!!.onSuccessTask {
            // Find all the roots for this object.
            val select = arrayOf(OfflineSQLiteOpenHelper.KEY_KEY)
            val where = OfflineSQLiteOpenHelper.KEY_UUID + "=?"
            val args = arrayOf(uuid.get())
            db.queryAsync(OfflineSQLiteOpenHelper.TABLE_DEPENDENCIES, select, where, args)
        }.onSuccessTask { task: Task<Cursor> ->
            // Try to unpin this object from the pin label if it's a root of the ParsePin.
            val cursor = task.result
            val uuids: MutableList<String> = ArrayList()
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                uuids.add(cursor.getString(0))
                cursor.moveToNext()
            }
            cursor.close()
            val tasks: MutableList<Task<Void?>> = ArrayList()
            for (uuid1 in uuids) {
                val unpinTask1 = getPointerAsync<ParseObject?>(
                    uuid1,
                    db
                ).onSuccessTask { task12: Task<ParseObject?> ->
                    val pin = task12.result as ParsePin
                    fetchLocallyAsync(pin, db)
                }.continueWithTask { task1: Task<ParsePin> ->
                    val pin = task1.result
                    val modified = pin.objects
                    if (modified == null || !modified.contains(`object`)) {
                        return@continueWithTask task1.makeVoid()
                    }
                    modified.remove(`object`)
                    if (modified.size == 0) {
                        return@continueWithTask unpinAsync(uuid1, db)
                    }
                    pin.objects = modified
                    saveLocallyAsync(pin, true, db)
                }
                tasks.add(unpinTask1)
            }
            Task.whenAll(tasks)
        }

        // Delete the object from the Local Datastore in case it wasn't the root of a pin.
        return unpinTask.onSuccessTask { task: Task<Void>? ->
            val where = OfflineSQLiteOpenHelper.KEY_UUID + "=?"
            val args = arrayOf(uuid.get())
            db.deleteAsync(OfflineSQLiteOpenHelper.TABLE_DEPENDENCIES, where, args)
        }.onSuccessTask { task: Task<Void>? ->
            val where = OfflineSQLiteOpenHelper.KEY_UUID + "=?"
            val args = arrayOf(uuid.get())
            db.deleteAsync(OfflineSQLiteOpenHelper.TABLE_OBJECTS, where, args)
        }.onSuccessTask { task: Task<Void?>? ->
            synchronized(lock) {
                // Clean up
                //TODO (grantland): we should probably clean up uuidToObjectMap and objectToUuidMap, but
                // getting the uuid requires a task and things might get a little funky...
                fetchedObjects.remove(`object`)
            }
            task
        }
    }

    private fun getParsePin(name: String, db: ParseSQLiteDatabase): Task<ParsePin> {
        val query = ParseQuery.State.Builder(
            ParsePin::class.java
        )
            .whereEqualTo(ParsePin.KEY_NAME, name)
            .build()

        /* We need to call directly to the OfflineStore since we don't want/need a user to query for
         * ParsePins
         */return findAsync(query, null, null, db).onSuccess { task: Task<List<ParsePin>?> ->
            var pin: ParsePin? = null
            if (task.result != null && task.result!!.isNotEmpty()) {
                pin = task.result!![0]
            }

            //TODO (grantland): What do we do if there are more than 1 result?
            if (pin == null) {
                pin = create(ParsePin::class.java)
                pin.name = name
            }
            pin
        }
    }

    //region ParsePin
    /* package */
    fun <T : ParseObject> pinAllObjectsAsync(
        name: String,
        objects: List<T>,
        includeChildren: Boolean
    ): Task<Void> {
        return runWithManagedTransaction(object : SQLiteDatabaseCallable<Task<Void?>> {
            override fun call(db: ParseSQLiteDatabase): Task<Void?> {
                return pinAllObjectsAsync(
                    name,
                    objects,
                    includeChildren,
                    db
                )
            }
        })
    }

    private fun <T : ParseObject> pinAllObjectsAsync(
        name: String,
        objects: List<T>?,
        includeChildren: Boolean,
        db: ParseSQLiteDatabase
    ): Task<Void?> {
        return if (objects == null || objects.isEmpty()) {
            Task.forResult(null)
        } else getParsePin(name, db).onSuccessTask { task: Task<ParsePin> ->
            val pin = task.result

            //TODO (grantland): change to use relations. currently the related PO are only getting saved
            // offline as pointers.
//        ParseRelation<ParseObject> relation = pin.getRelation(KEY_OBJECTS);
//        relation.add(object);

            // Hack to store collections in a pin
            var modified = pin.objects
            if (modified == null) {
                modified = ArrayList<ParseObject>(objects)
            } else {
                for (`object` in objects) {
                    if (!modified.contains(`object`)) {
                        modified.add(`object`)
                    }
                }
            }
            pin.objects = modified
            if (includeChildren) {
                return@onSuccessTask saveLocallyAsync(pin, true, db)
            }
            saveLocallyAsync(pin, pin.objects, db)
        }
    }

    /* package */
    fun <T : ParseObject> unpinAllObjectsAsync(
        name: String,
        objects: List<T>
    ): Task<Void> {
        return runWithManagedTransaction(object : SQLiteDatabaseCallable<Task<Void?>> {
            override fun call(db: ParseSQLiteDatabase): Task<Void?> {
                return unpinAllObjectsAsync(
                    name,
                    objects,
                    db
                )
            }
        })
    }

    private fun <T : ParseObject?> unpinAllObjectsAsync(
        name: String,
        objects: List<T>?,
        db: ParseSQLiteDatabase
    ): Task<Void?> {
        return if (objects == null || objects.isEmpty()) {
            Task.forResult(null)
        } else getParsePin(name, db).onSuccessTask { task: Task<ParsePin> ->
            val pin = task.result

            //TODO (grantland): change to use relations. currently the related PO are only getting saved
            // offline as pointers.
//        ParseRelation<ParseObject> relation = pin.getRelation(KEY_OBJECTS);
//        relation.remove(object);

            // Hack to store collections in a pin
            val modified = pin.objects
                ?: // Unpin a pin that doesn't exist. Wat?
                return@onSuccessTask Task.forResult<Void?>(null)
            modified.removeAll(objects)
            if (modified.size == 0) {
                return@onSuccessTask unpinAsync(pin, db)
            }
            pin.objects = modified
            saveLocallyAsync(pin, true, db)
        }
    }

    /* package */
    fun unpinAllObjectsAsync(name: String): Task<Void> {
        return runWithManagedTransaction(object : SQLiteDatabaseCallable<Task<Void?>> {
            override fun call(db: ParseSQLiteDatabase): Task<Void?> {
                return unpinAllObjectsAsync(
                    name,
                    db
                )
            }
        })
    }

    private fun unpinAllObjectsAsync(name: String, db: ParseSQLiteDatabase): Task<Void?> {
        return getParsePin(name, db).continueWithTask { task: Task<ParsePin> ->
            if (task.isFaulted) {
                return@continueWithTask task.makeVoid()
            }
            val pin = task.result
            unpinAsync(pin, db)
        }
    }

    /* package */
    open fun <T : ParseObject> findFromPinAsync(
        name: String?,
        state: ParseQuery.State<T>,
        user: ParseUser?
    ): Task<List<T>>? {
        return runWithManagedConnection(object : SQLiteDatabaseCallable<Task<List<T>>> {
            override fun call(db: ParseSQLiteDatabase): Task<List<T>> {
                return findFromPinAsync(
                    name,
                    state,
                    user,
                    db
                )
            }
        })
    }

    private fun <T : ParseObject> findFromPinAsync(
        name: String?,
        state: ParseQuery.State<T>,
        user: ParseUser?,
        db: ParseSQLiteDatabase
    ): Task<List<T>> {
        val task: Task<ParsePin> = name?.let { getParsePin(it, db) } ?: Task.forResult(null)
        return task.onSuccessTask { task1: Task<ParsePin> ->
            val pin = task1.result
            findAsync(state, user, pin, false, db)
        }
    }

    /* package */
    open fun <T : ParseObject> countFromPinAsync(
        name: String?,
        state: ParseQuery.State<T>,
        user: ParseUser?
    ): Task<Int?>? {
        return runWithManagedConnection(object : SQLiteDatabaseCallable<Task<Int?>> {
            override fun call(db: ParseSQLiteDatabase): Task<Int?> {
                return countFromPinAsync(
                    name,
                    state,
                    user,
                    db
                )
            }
        })
    }

    private fun <T : ParseObject> countFromPinAsync(
        name: String?,
        state: ParseQuery.State<T>,
        user: ParseUser?,
        db: ParseSQLiteDatabase
    ): Task<Int?> {
        val task: Task<ParsePin> = name?.let { getParsePin(it, db) } ?: Task.forResult(null)
        return task.onSuccessTask { task12: Task<ParsePin> ->
            val pin = task12.result
            findAsync(
                state,
                user,
                pin,
                true,
                db
            ).onSuccess { task1: Task<List<T>?> -> task1.result!!.size }
        }
    }

    /**
     * This should be called by the ParseObject constructor notify the store that there is an object
     * with this className and objectId.
     */
    /* package */
    fun registerNewObject(`object`: ParseObject) {
        synchronized(lock) {
            val objectId = `object`.objectId
            if (objectId != null) {
                val className = `object`.className
                val classNameAndObjectId = Pair.create(className, objectId)
                classNameAndObjectIdToObjectMap.put(classNameAndObjectId, `object`)
            }
        }
    }

    //endregion
    //region Single Instance
    /* package */
    fun unregisterObject(`object`: ParseObject) {
        synchronized(lock) {
            val objectId = `object`.objectId
            if (objectId != null) {
                classNameAndObjectIdToObjectMap.remove(Pair.create(`object`.className, objectId))
            }
        }
    }

    /**
     * This should only ever be called from ParseObject.createWithoutData().
     *
     * @return a pair of ParseObject and Boolean. The ParseObject is the object. The Boolean is true
     * iff the object was newly created.
     */
    /* package */
    fun getObject(className: String?, objectId: String?): ParseObject? {
        checkNotNull(objectId) { "objectId cannot be null." }
        val classNameAndObjectId = Pair.create(className, objectId)
        // This lock should never be held by anyone doing disk or database access.
        synchronized(lock) { return classNameAndObjectIdToObjectMap[classNameAndObjectId] }
    }

    /**
     * When an object is finished saving, it gets an objectId. Then it should call this method to
     * clean up the bookeeping around ids.
     */
    /* package */
    fun updateObjectId(`object`: ParseObject, oldObjectId: String?, newObjectId: String?) {
        if (oldObjectId != null) {
            if (oldObjectId == newObjectId) {
                return
            }
            /*
             * Special case for re-saving installation if it was deleted on the server
             * @see ParseInstallation#saveAsync(String, Task)
             */if (`object` is ParseInstallation
                && newObjectId == null
            ) {
                synchronized(lock) {
                    classNameAndObjectIdToObjectMap.remove(
                        Pair.create(
                            `object`.className,
                            oldObjectId
                        )
                    )
                }
                return
            } else {
                throw RuntimeException("objectIds cannot be changed in offline mode.")
            }
        }
        val className = `object`.className
        val classNameAndNewObjectId = Pair.create(className, newObjectId)
        synchronized(lock) {

            // See if there's already an entry for the new object id.
            val existing = classNameAndObjectIdToObjectMap[classNameAndNewObjectId]
            if (existing != null && existing !== `object`) {
                throw RuntimeException(
                    "Attempted to change an objectId to one that's "
                            + "already known to the Offline Store."
                )
            }

            // Okay, all clear to add the new reference.
            classNameAndObjectIdToObjectMap.put(classNameAndNewObjectId, `object`)
        }
    }

    /**
     * Wraps SQLite operations with a managed SQLite connection.
     */
    private fun <T> runWithManagedConnection(callable: SQLiteDatabaseCallable<Task<T>>): Task<T> {
        return helper.writableDatabaseAsync.onSuccessTask { task: Task<ParseSQLiteDatabase> ->
            val db = task.result
            callable.call(db).continueWithTask { task1: Task<T>? ->
                db.closeAsync()
                task1
            }
        }
    }

    /**
     * Wraps SQLite operations with a managed SQLite connection and transaction.
     */
    private fun runWithManagedTransaction(callable: SQLiteDatabaseCallable<Task<Void?>>): Task<Void> {
        return helper.writableDatabaseAsync.onSuccessTask { task: Task<ParseSQLiteDatabase> ->
            val db = task.result
            db.beginTransactionAsync().onSuccessTask {
                callable.call(db)
                    .onSuccessTask { db.setTransactionSuccessfulAsync() }
                    .continueWithTask { task1: Task<Void>? ->
                        db.endTransactionAsync()
                        db.closeAsync()
                        task1
                    }
            }
        }
    }
    //endregion
    /**
     * Clears all in-memory caches so that data must be retrieved from disk.
     */
    fun simulateReboot() {
        synchronized(lock) {
            uuidToObjectMap.clear()
            objectToUuidMap.clear()
            classNameAndObjectIdToObjectMap.clear()
            fetchedObjects.clear()
        }
    }

    /**
     * Clears the database on disk.
     */
    fun clearDatabase(context: Context?) {
        helper.clearDatabase(context)
    }

    private interface SQLiteDatabaseCallable<T> {
        fun call(db: ParseSQLiteDatabase): T
    }
    /*
     * Methods for testing.
     */
    /**
     * Extends the normal JSON -> ParseObject decoding to also deal with placeholders for new objects
     * that have been saved offline.
     */
    private class OfflineDecoder(  // A map of UUID -> Task that will be finished once the given ParseObject is loaded.
        // The Tasks should all be finished before decode is called.
        private val offlineObjects: Map<String, Task<ParseObject>>
    ) : ParseDecoder() {
        override fun decode(`object`: Any): Any? {
            // If we see an offline id, make sure to decode it.
            if (`object` is JSONObject
                && `object`.optString("__type") == "OfflineObject"
            ) {
                val uuid = `object`.optString("uuid")
                return offlineObjects[uuid]!!.result
            }

            /*
             * Embedded objects can't show up here, because we never stored them that way offline.
             */return super.decode(`object`)
        }
    }

    /**
     * An encoder that can encode objects that are available offline. After using this encoder, you
     * must call whenFinished() and wait for its result to be finished before the results of the
     * encoding will be valid.
     */
    private inner class OfflineEncoder
    /**
     * Creates an encoder.
     *
     * @param db A database connection to use.
     */(private val db: ParseSQLiteDatabase) : ParseEncoder() {
        private val tasksLock = Any()
        private val tasks = ArrayList<Task<Void?>>()

        /**
         * The results of encoding an object with this encoder will not be valid until the task returned
         * by this method is finished.
         */
        fun whenFinished(): Task<Void?> {
            return Task.whenAll(tasks).continueWithTask { ignore: Task<Void?>? ->
                synchronized(tasksLock) {

                    // It might be better to return an aggregate error here.
                    for (task in tasks) {
                        if (task.isFaulted || task.isCancelled) {
                            return@continueWithTask task
                        }
                    }
                    tasks.clear()
                    return@continueWithTask Task.forResult<Void?>(null)
                }
            }
        }

        /**
         * Implements an encoding strategy for Parse Objects that uses offline ids when necessary.
         */
        override fun encodeRelatedObject(`object`: ParseObject): JSONObject {
            return try {
                if (`object`.objectId != null) {
                    val result = JSONObject()
                    result.put("__type", "Pointer")
                    result.put("objectId", `object`.objectId)
                    result.put("className", `object`.className)
                    return result
                }
                val result = JSONObject()
                result.put("__type", "OfflineObject")
                synchronized(tasksLock) {
                    tasks.add(getOrCreateUUIDAsync(`object`, db).onSuccess { task: Task<String> ->
                        result.put("uuid", task.result)
                        null
                    })
                }
                result
            } catch (e: JSONException) {
                // This can literally never happen.
                throw RuntimeException(e)
            }
        }
    }

    companion object {
        /**
         * SQLite has a max of 999 SQL variables in a single statement.
         */
        private const val MAX_SQL_VARIABLES = 999
    }
}