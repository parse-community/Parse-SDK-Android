/*
* Copyright (c) 2015-present, Parse, LLC.
* All rights reserved.
*
* This source code is licensed under the BSD-style license found in the
* LICENSE file in the root directory of this source tree. An additional grant
* of patent rights can be found in the PATENTS file in the same directory.
*/
package com.parse

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.parse.PLog.w
import com.parse.ParseEncoder.Companion.isValidType
import com.parse.TaskQueue.Companion.waitFor
import com.parse.boltsinternal.Capture
import com.parse.boltsinternal.Continuation
import com.parse.boltsinternal.Task
import com.parse.boltsinternal.TaskCompletionSource
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Lock
import kotlin.collections.Collection
import kotlin.collections.Iterator
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableCollection
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.listOf
import kotlin.collections.set
import kotlin.collections.toTypedArray

/**
 * The `ParseObject` is a local representation of data that can be saved and retrieved from
 * the Parse cloud.
 *
 *
 * The basic workflow for creating new data is to construct a new `ParseObject`, use
 * [.put] to fill it with data, and then use [.saveInBackground] to
 * persist to the cloud.
 *
 *
 * The basic workflow for accessing existing data is to use a [ParseQuery] to specify which
 * existing data to retrieve.
 */
open class ParseObject(theClassName: String) : Parcelable {
    val mutex = Any()
    private val taskQueue = TaskQueue()
    private val operationSetQueue: LinkedList<ParseOperationSet>

    // Cached State
    private val estimatedData: MutableMap<String, Any?>
    private val saveEvent = ParseMulticastDelegate<ParseObject>()
    @JvmField
    var localId: String? = null
    @JvmField
    var isDeleted = false
    @JvmField
    var isDeleting // Since delete ops are queued, we don't need a counter.
            = false

    //TODO (grantland): Derive this off the EventuallyPins as opposed to +/- count.
    var isDeletingEventually = 0
    internal var state: State = State.newBuilder(theClassName).build()
        get() {synchronized(mutex) { return field }}
        set(value) {synchronized(mutex) { setState(value, true) }}
    private var ldsEnabledWhenParceling = false

    /**
     * The base class constructor to call in subclasses. Uses the class name specified with the
     * [ParseClassName] annotation on the subclass.
     */
    protected constructor() : this(AUTO_CLASS_NAME)

    internal open fun newStateBuilder(className: String): State.Init<*> {
        return State.Builder(className)
    }

    /**
     * Updates the current state of this object as well as updates our in memory cached state.
     *
     * @param newState The new state.
     */
    private fun setState(newState: State, notifyIfObjectIdChanges: Boolean) {
        synchronized(mutex) {
            val oldObjectId = state.objectId()
            val newObjectId = newState.objectId()
            state = newState
            if (notifyIfObjectIdChanges && !ParseTextUtils.equals(oldObjectId, newObjectId)) {
                notifyObjectIdChanged(oldObjectId, newObjectId)
            }
            rebuildEstimatedData()
        }
    }

    /**
     * Accessor to the class name.
     */
    val className: String?
        get() {
            synchronized(mutex) { return state.className() }
        }

    /**
     * This reports time as the server sees it, so that if you make changes to a `ParseObject`, then
     * wait a while, and then call [.save], the updated time will be the time of the
     * [.save] call rather than the time the object was changed locally.
     *
     * @return The last time this object was updated on the server.
     */
    val updatedAt: Date?
        get() {
            val updatedAt = state.updatedAt()
            return if (updatedAt > 0) Date(updatedAt) else null
        }

    /**
     * This reports time as the server sees it, so that if you create a `ParseObject`, then wait a
     * while, and then call [.save], the creation time will be the time of the first
     * [.save] call rather than the time the object was created locally.
     *
     * @return The first time this object was saved on the server.
     */
    val createdAt: Date?
        get() {
            val createdAt = state.createdAt()
            return if (createdAt > 0) Date(createdAt) else null
        }

    /**
     * Returns a set view of the keys contained in this object. This does not include createdAt,
     * updatedAt, authData, or objectId. It does include things like username and ACL.
     */
    fun keySet(): Set<String?> {
        synchronized(mutex) { return Collections.unmodifiableSet(estimatedData.keys) }
    }

    /**
     * Copies all of the operations that have been performed on another object since its last save
     * onto this one.
     */
    fun copyChangesFrom(other: ParseObject) {
        synchronized(mutex) {
            val operations = other.operationSetQueue.first
            for (key in operations.keys) {
                performOperation(key, operations[key])
            }
        }
    }

    fun mergeFromObject(other: ParseObject) {
        synchronized(mutex) {

            // If they point to the same instance, we don't need to merge.
            if (this === other) {
                return
            }
            val copy = other.state.newBuilder<State.Init<*>>().build<State>()

            // We don't want to notify if an objectId changed here since we utilize this method to merge
            // an anonymous current user with a new ParseUser instance that's calling signUp(). This
            // doesn't make any sense and we should probably remove that code in ParseUser.
            // Otherwise, there shouldn't be any objectId changes here since this method is only otherwise
            // used in fetchAll.
            setState(copy, false)
        }
    }

    /**
     * Clears changes to this object's `key` made since the last call to [.save] or
     * [.saveInBackground].
     *
     * @param key The `key` to revert changes for.
     */
    fun revert(key: String) {
        synchronized(mutex) {
            if (isDirty(key)) {
                currentOperations().remove(key)
                rebuildEstimatedData()
            }
        }
    }

    /**
     * Clears any changes to this object made since the last call to [.save] or
     * [.saveInBackground].
     */
    fun revert() {
        synchronized(mutex) {
            if (isDirty) {
                currentOperations().clear()
                rebuildEstimatedData()
            }
        }
    }

    /**
     * Deep traversal on this object to grab a copy of any object referenced by this object. These
     * instances may have already been fetched, and we don't want to lose their data when refreshing
     * or saving.
     *
     * @return the map mapping from objectId to `ParseObject` which has been fetched.
     */
    private fun collectFetchedObjects(): Map<String?, ParseObject> {
        val fetchedObjects: MutableMap<String?, ParseObject> = HashMap()
        val traverser: ParseTraverser = object : ParseTraverser() {
            override fun visit(`object`: Any): Boolean {
                if (`object` is ParseObject) {
                    val state = `object`.state
                    if (state.objectId() != null && state.isComplete) {
                        fetchedObjects[state.objectId()] = `object`
                    }
                }
                return true
            }
        }
        traverser.traverse(estimatedData)
        return fetchedObjects
    }

    /**
     * Helper method called by [.fromJSONPayload]
     *
     *
     * The method helps webhooks implementation to build Parse object from raw JSON payload.
     * It is different from [.mergeFromServer]
     * as the method saves the key value pairs (other than className, objectId, updatedAt and
     * createdAt) in the operation queue rather than the server data. It also handles
     * [ParseFieldOperations] differently.
     *
     * @param json    : JSON object to be converted to Parse object
     * @param decoder : Decoder to be used for Decoding JSON
     */
    fun build(json: JSONObject, decoder: ParseDecoder) {
        try {
            val builder = State.Builder(state)
                .isComplete(true)
            builder.clear()
            val keys: Iterator<*> = json.keys()
            while (keys.hasNext()) {
                val key = keys.next() as String
                /*
        __className:  Used by fromJSONPayload, should be stripped out by the time it gets here...
         */if (key == KEY_CLASS_NAME) {
                    continue
                }
                if (key == KEY_OBJECT_ID) {
                    val newObjectId = json.getString(key)
                    builder.objectId(newObjectId)
                    continue
                }
                if (key == KEY_CREATED_AT) {
                    builder.createdAt(ParseDateFormat.getInstance().parse(json.getString(key)))
                    continue
                }
                if (key == KEY_UPDATED_AT) {
                    builder.updatedAt(ParseDateFormat.getInstance().parse(json.getString(key)))
                    continue
                }
                val value = json[key]
                val decodedObject = decoder.decode(value)
                if (decodedObject is ParseFieldOperation) {
                    performOperation(key, decodedObject as ParseFieldOperation?)
                } else {
                    put(key, decodedObject!!)
                }
            }
            state = builder.build()
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Merges from JSON in REST format.
     * Updates this object with data from the server.
     *
     * @see .toJSONObjectForSaving
     */
    internal fun mergeFromServer(
        state: State?, json: JSONObject, decoder: ParseDecoder, completeData: Boolean
    ): State {
        return try {
            // If server data is complete, consider this object to be fetched.
            val builder = state!!.newBuilder<State.Init<*>>()
            if (completeData) {
                builder.clear()
            }
            builder.isComplete(state.isComplete || completeData)
            val keys: Iterator<*> = json.keys()
            while (keys.hasNext()) {
                val key = keys.next() as String
                /*
                __type:       Returned by queries and cloud functions to designate body is a ParseObject
                __className:  Used by fromJSON, should be stripped out by the time it gets here...
                 */
                if (key == "__type" || key == KEY_CLASS_NAME) {
                    continue
                }
                if (key == KEY_OBJECT_ID) {
                    val newObjectId = json.getString(key)
                    builder.objectId(newObjectId)
                    continue
                }
                if (key == KEY_CREATED_AT) {
                    builder.createdAt(ParseDateFormat.getInstance().parse(json.getString(key)))
                    continue
                }
                if (key == KEY_UPDATED_AT) {
                    builder.updatedAt(ParseDateFormat.getInstance().parse(json.getString(key)))
                    continue
                }
                if (key == KEY_ACL) {
                    val acl = ParseACL.createACLFromJSONObject(json.getJSONObject(key), decoder)
                    builder.put(KEY_ACL, acl)
                    continue
                }
                if (key == KEY_SELECTED_KEYS) {
                    val safeKeys = json.getJSONArray(key)
                    if (safeKeys.length() > 0) {
                        val set: MutableCollection<String> = HashSet()
                        for (i in 0 until safeKeys.length()) {
                            // Don't add nested keys.
                            var safeKey = safeKeys.getString(i)
                            if (safeKey.contains(".")) safeKey =
                                safeKey.split("\\.").toTypedArray()[0]
                            set.add(safeKey)
                        }
                        builder.availableKeys(set.toSet())
                    }
                    continue
                }
                val value = json[key]
                if (value is JSONObject && json.has(KEY_SELECTED_KEYS)) {
                    // This might be a ParseObject. Pass selected keys to understand if it is complete.
                    val selectedKeys = json.getJSONArray(KEY_SELECTED_KEYS)
                    val nestedKeys = JSONArray()
                    for (i in 0 until selectedKeys.length()) {
                        val nestedKey = selectedKeys.getString(i)
                        if (nestedKey.startsWith("$key.")) nestedKeys.put(nestedKey.substring(key.length + 1))
                    }
                    if (nestedKeys.length() > 0) {
                        value.put(KEY_SELECTED_KEYS, nestedKeys)
                    }
                }
                val decodedObject = decoder.decode(value)
                builder.put(key, decodedObject)
            }
            builder.build()
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Convert to REST JSON for persisting in LDS.
     *
     * @see .mergeREST
     */
    fun toRest(encoder: ParseEncoder): JSONObject {
        var state: State?
        var operationSetQueueCopy: MutableList<ParseOperationSet>
        synchronized(mutex) {

            // mutex needed to lock access to state and operationSetQueue and operationSetQueue & children
            // are mutable
            state = this.state

            // operationSetQueue is a List of Lists, so we'll need custom copying logic
            val operationSetQueueSize = operationSetQueue.size
            operationSetQueueCopy = ArrayList(operationSetQueueSize)
            for (i in 0 until operationSetQueueSize) {
                val original = operationSetQueue[i]
                val copy = ParseOperationSet(original)
                operationSetQueueCopy.add(copy)
            }
        }
        return toRest(state, operationSetQueueCopy, encoder)
    }

    internal fun toRest(
        state: State?, operationSetQueue: List<ParseOperationSet>, objectEncoder: ParseEncoder
    ): JSONObject {
        // Public data goes in dataJSON; special fields go in objectJSON.
        val json = JSONObject()
        try {
            // REST JSON (State)
            json.put(KEY_CLASS_NAME, state!!.className())
            if (state.objectId() != null) {
                json.put(KEY_OBJECT_ID, state.objectId())
            }
            if (state.createdAt() > 0) {
                json.put(
                    KEY_CREATED_AT,
                    ParseDateFormat.getInstance().format(Date(state.createdAt()))
                )
            }
            if (state.updatedAt() > 0) {
                json.put(
                    KEY_UPDATED_AT,
                    ParseDateFormat.getInstance().format(Date(state.updatedAt()))
                )
            }
            for (key in state.keySet()) {
                val value = state[key]
                json.put(key, objectEncoder.encode(value))
            }

            // Internal JSON
            //TODO(klimt): We'll need to rip all this stuff out and put it somewhere else if we start
            // using the REST api and want to send data to Parse.
            json.put(KEY_COMPLETE, state.isComplete)
            json.put(KEY_IS_DELETING_EVENTUALLY, isDeletingEventually)
            val availableKeys = JSONArray(state.availableKeys())
            json.put(KEY_SELECTED_KEYS, availableKeys)

            // Operation Set Queue
            val operations = JSONArray()
            for (operationSet in operationSetQueue) {
                operations.put(operationSet.toRest(objectEncoder))
            }
            json.put(KEY_OPERATIONS, operations)
        } catch (e: JSONException) {
            throw RuntimeException("could not serialize object to JSON")
        }
        return json
    }

    /**
     * Merge with REST JSON from LDS.
     *
     * @see .toRest
     */
    internal fun mergeREST(state: State, json: JSONObject, decoder: ParseDecoder) {
        val saveEventuallyOperationSets = ArrayList<ParseOperationSet>()
        synchronized(mutex) {
            try {
                val isComplete = json.getBoolean(KEY_COMPLETE)
                isDeletingEventually = ParseJSONUtils.getInt(
                    json, listOf(
                        KEY_IS_DELETING_EVENTUALLY,
                        KEY_IS_DELETING_EVENTUALLY_OLD
                    )
                )
                val operations = json.getJSONArray(KEY_OPERATIONS)
                run {
                    val newerOperations = currentOperations()
                    operationSetQueue.clear()

                    // Add and enqueue any saveEventually operations, roll forward any other operation sets
                    // (operation sets here are generally failed/incomplete saves).
                    var current: ParseOperationSet? = null
                    for (i in 0 until operations.length()) {
                        val operationSetJSON = operations.getJSONObject(i)
                        val operationSet = ParseOperationSet.fromRest(operationSetJSON, decoder)
                        if (operationSet.isSaveEventually) {
                            if (current != null) {
                                operationSetQueue.add(current)
                                current = null
                            }
                            saveEventuallyOperationSets.add(operationSet)
                            operationSetQueue.add(operationSet)
                            continue
                        }
                        if (current != null) {
                            operationSet.mergeFrom(current)
                        }
                        current = operationSet
                    }
                    if (current != null) {
                        operationSetQueue.add(current)
                    }

                    // Merge the changes that were previously in memory into the updated object.
                    currentOperations().mergeFrom(newerOperations)
                }

                // We only want to merge server data if we our updatedAt is null (we're unsaved or from
                // #createWithoutData) or if the JSON's updatedAt is newer than ours.
                var mergeServerData = false
                if (state.updatedAt() < 0) {
                    mergeServerData = true
                } else if (json.has(KEY_UPDATED_AT)) {
                    val otherUpdatedAt = ParseDateFormat.getInstance().parse(
                        json.getString(
                            KEY_UPDATED_AT
                        )
                    )
                    if (Date(state.updatedAt()) < otherUpdatedAt) {
                        mergeServerData = true
                    }
                }
                if (mergeServerData) {
                    // Clean up internal json keys
                    val mergeJSON = ParseJSONUtils.create(
                        json, listOf(
                            KEY_COMPLETE,
                            KEY_IS_DELETING_EVENTUALLY,
                            KEY_IS_DELETING_EVENTUALLY_OLD,
                            KEY_OPERATIONS
                        )
                    )
                    val newState = mergeFromServer(state, mergeJSON, decoder, isComplete)
                    this.state = newState
                }
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
        }

        // We cannot modify the taskQueue inside synchronized (mutex).
        for (operationSet in saveEventuallyOperationSets) {
            enqueueSaveEventuallyOperationAsync(operationSet)
        }
    }

    private fun hasDirtyChildren(): Boolean {
        synchronized(mutex) {

            // We only need to consider the currently estimated children here,
            // because they're the only ones that might need to be saved in a
            // subsequent call to save, which is the meaning of "dirtiness".
            val unsavedChildren: MutableList<ParseObject> = ArrayList()
            collectDirtyChildren(estimatedData, unsavedChildren, null)
            return unsavedChildren.size > 0
        }
    }

    /**
     * Whether any key-value pair in this object (or its children) has been added/updated/removed and
     * not saved yet.
     *
     * @return Whether this object has been altered and not saved yet.
     */
    val isDirty: Boolean
        get() = this.isDirty(true)

    fun isDirty(considerChildren: Boolean): Boolean {
        synchronized(mutex) { return isDeleted || objectId == null || hasChanges() || considerChildren && hasDirtyChildren() }
    }

    fun hasChanges(): Boolean {
        synchronized(mutex) { return currentOperations().size > 0 }
    }

    /**
     * Returns `true` if this `ParseObject` has operations in operationSetQueue that
     * haven't been completed yet, `false` if there are no operations in the operationSetQueue.
     */
    fun hasOutstandingOperations(): Boolean {
        synchronized(mutex) {
            // > 1 since 1 is for unsaved changes.
            return operationSetQueue.size > 1
        }
    }

    /**
     * Whether a value associated with a key has been added/updated/removed and not saved yet.
     *
     * @param key The key to check for
     * @return Whether this key has been altered and not saved yet.
     */
    fun isDirty(key: String): Boolean {
        synchronized(mutex) { return currentOperations().containsKey(key) }
    }
    /**
     * Accessor to the object id. An object id is assigned as soon as an object is saved to the
     * server. The combination of a className and an objectId uniquely identifies an object in your
     * application.
     *
     * @return The object id.
     */// We don't need to use setState since it doesn't affect our cached state.
    /**
     * Setter for the object id. In general you do not need to use this. However, in some cases this
     * can be convenient. For example, if you are serializing a `ParseObject` yourself and wish
     * to recreate it, you can use this to recreate the `ParseObject` exactly.
     */
    val objectId: String?
        get() {
            synchronized(mutex) { return state.objectId() }
        }

    /**
     * Returns the localId, which is used internally for serializing relations to objects that don't
     * yet have an objectId.
     */
    fun getOrCreateLocalId(): String {
        synchronized(mutex) {
            if (localId == null) {
                check(state.objectId() == null) { "Attempted to get a localId for an object with an objectId." }
                localId = localIdManager.createLocalId()
            }
            return localId!!
        }
    }

    // Sets the objectId without marking dirty.
    private fun notifyObjectIdChanged(oldObjectId: String?, newObjectId: String?) {
        synchronized(mutex) {

            // The offline store may throw if this object already had a different objectId.
            val store = Parse.getLocalDatastore()
            store?.updateObjectId(this, oldObjectId, newObjectId)
            if (localId != null) {
                localIdManager.setObjectId(localId!!, newObjectId)
                localId = null
            }
        }
    }

    private fun currentSaveEventuallyCommand(
        operations: ParseOperationSet, objectEncoder: ParseEncoder, sessionToken: String
    ): ParseRESTObjectCommand {
        val state = this.state

        /*
         * Get the JSON representation of the object, and use some of the information to construct the
         * command.
         */
        val objectJSON = toJSONObjectForSaving(state, operations, objectEncoder)
        return ParseRESTObjectCommand.saveObjectCommand(
            state,
            objectJSON,
            sessionToken
        )
    }

    /**
     * Converts a `ParseObject` to a JSON representation for saving to Parse.
     *
     *
     * <pre>
     * {
     * data: { // objectId plus any ParseFieldOperations },
     * classname: class name for the object
     * }
    </pre> *
     *
     *
     * updatedAt and createdAt are not included. only dirty keys are represented in the data.
     *
     * @see .mergeFromServer
     */
    // Currently only used by saveEventually
    private fun <T : State?> toJSONObjectForSaving(
        state: T, operations: ParseOperationSet, objectEncoder: ParseEncoder
    ): JSONObject {
        val objectJSON = JSONObject()
        try {
            // Serialize the data
            for (key in operations.keys) {
                val operation = operations[key]
                objectJSON.put(key, objectEncoder.encode(operation))

                // TODO(grantland): Use cached value from hashedObjects if it's a set operation.
            }
            if (state!!.objectId() != null) {
                objectJSON.put(KEY_OBJECT_ID, state.objectId())
            }
        } catch (e: JSONException) {
            throw RuntimeException("could not serialize object to JSON")
        }
        return objectJSON
    }

    /**
     * Handles the result of `save`.
     *
     *
     * Should be called on success or failure.
     */
    // TODO(grantland): Remove once we convert saveEventually and ParseUser.signUp/resolveLaziness
    // to controllers
    internal fun handleSaveResultAsync(
        result: JSONObject?, operationsBeforeSave: ParseOperationSet
    ): Task<Void?> {
        var newState: State? = null
        if (result != null) { // Success
            synchronized(mutex) {
                val fetchedObjects = collectFetchedObjects()
                val decoder: ParseDecoder = KnownParseObjectDecoder(fetchedObjects)
                newState = ParseObjectCoder.get()
                    .decode(state.newBuilder<State.Init<*>>().clear(), result, decoder)
                    .isComplete(false)
                    .build()
            }
        }
        return handleSaveResultAsync(newState, operationsBeforeSave)
    }

    /**
     * Handles the result of `save`.
     *
     *
     * Should be called on success or failure.
     */
    internal open fun handleSaveResultAsync(
        result: State?, operationsBeforeSave: ParseOperationSet
    ): Task<Void?> {
        var task = Task.forResult<Void?>(null)

        /*
         * If this object is in the offline store, then we need to make sure that we pull in any dirty
         * changes it may have before merging the server data into it.
         */
        val store = Parse.getLocalDatastore()
        if (store != null) {
            task = task.onSuccessTask {
                store.fetchLocallyAsync(this@ParseObject).makeVoid()
            }
        }
        val success = result != null
        synchronized(mutex) {

            // Find operationsBeforeSave in the queue so that we can remove it and move to the next
            // operation set.
            val opIterator =
                operationSetQueue.listIterator(operationSetQueue.indexOf(operationsBeforeSave))
            opIterator.next()
            opIterator.remove()
            if (!success) {
                // Merge the data from the failed save into the next save.
                val nextOperation = opIterator.next()
                nextOperation.mergeFrom(operationsBeforeSave)
                if (store != null) {
                    task = task.continueWithTask { task14: Task<Void?> ->
                        if (task14.isFaulted) {
                            return@continueWithTask Task.forResult<Void?>(null)
                        } else {
                            return@continueWithTask store.updateDataForObjectAsync(this@ParseObject)
                        }
                    }
                }
                return task
            }
        }

        // fetchLocallyAsync will return an error if this object isn't in the LDS yet and that's ok
        task = task.continueWith {
            synchronized(mutex) {
                val newState: State = if (result!!.isComplete) {
                    // Result is complete, so just replace
                    result
                } else {
                    // Result is incomplete, so we'll need to apply it to the current state
                    state.newBuilder<State.Init<*>>()
                        .apply(operationsBeforeSave)
                        .apply(result)
                        .build()
                }
                state = newState
            }
            null
        }
        if (store != null) {
            task =
                task.onSuccessTask { store.updateDataForObjectAsync(this@ParseObject) }
        }
        task = task.onSuccess {
            saveEvent.invoke(this@ParseObject, null)
            null
        }
        return task
    }

    internal fun startSave(): ParseOperationSet {
        synchronized(mutex) {
            val currentOperations = currentOperations()
            operationSetQueue.addLast(ParseOperationSet())
            return currentOperations
        }
    }

    open fun validateSave() {
        // do nothing
    }

    /**
     * Saves this object to the server. Typically, you should use [.saveInBackground] instead of
     * this, unless you are managing your own threading.
     *
     * @throws ParseException Throws an exception if the server is inaccessible.
     */
    @Throws(ParseException::class)
    fun save() {
        ParseTaskUtils.wait(saveInBackground())
    }

    /**
     * Saves this object to the server in a background thread. This is preferable to using [.save],
     * unless your code is already running from a background thread.
     *
     * @return A [Task] that is resolved when the save completes.
     */
    fun saveInBackground(): Task<Void?> {
        return ParseUser.getCurrentUserAsync().onSuccessTask { task: Task<ParseUser?> ->
            val current = task.result
                ?: return@onSuccessTask Task.forResult<String?>(null)
            if (!current.isLazy) {
                return@onSuccessTask Task.forResult(current.sessionToken)
            }

            // The current user is lazy/unresolved. If it is attached to us via ACL, we'll need to
            // resolve/save it before proceeding.
            if (!isDataAvailable(KEY_ACL)) {
                return@onSuccessTask Task.forResult<String?>(null)
            }
            val acl = getACL(false)
                ?: return@onSuccessTask Task.forResult<String?>(null)
            val user = acl.unresolvedUser
            if (user == null || !user.isCurrentUser) {
                return@onSuccessTask Task.forResult<String?>(null)
            }
            user.saveAsync(null).onSuccess {
                check(!acl.hasUnresolvedUser()) {
                    ("ACL has an unresolved ParseUser. "
                            + "Save or sign up before attempting to serialize the ACL.")
                }
                user.sessionToken
            }
        }.onSuccessTask { task: Task<String> ->
            val sessionToken = task.result
            saveAsync(sessionToken)
        }
    }

    fun saveAsync(sessionToken: String?): Task<Void?> {
        return taskQueue.enqueue { toAwait: Task<Void?>? ->
            saveAsync(
                sessionToken,
                toAwait
            )
        }
    }

    open fun saveAsync(sessionToken: String?, toAwait: Task<Void?>?): Task<Void?>? {
        if (!isDirty) {
            return Task.forResult(null)
        }
        val operations: ParseOperationSet
        synchronized(mutex) {
            updateBeforeSave()
            validateSave()
            operations = startSave()
        }
        var task: Task<Void?>
        synchronized(mutex) {
            // Recursively save children

            /*
             * TODO(klimt): Why is this estimatedData and not... I mean, what if a child is
             * removed after save is called, but before the unresolved user gets resolved? It
             * won't get saved.
             */task = deepSaveAsync(estimatedData, sessionToken)
        }
        return task.onSuccessTask(
            waitFor(toAwait!!)
        ).onSuccessTask {
            val fetchedObjects = collectFetchedObjects()
            val decoder: ParseDecoder = KnownParseObjectDecoder(fetchedObjects)
            objectController.saveAsync(state, operations, sessionToken, decoder)
        }.continueWithTask { saveTask: Task<State?> ->
            val result = saveTask.result
            handleSaveResultAsync(result, operations).continueWithTask { task1: Task<Void?> ->
                if (task1.isFaulted || task1.isCancelled) {
                    return@continueWithTask task1
                }
                saveTask.makeVoid()
            }
        }
    }

    // Currently only used by ParsePinningEventuallyQueue for saveEventually due to the limitation in
    // ParseCommandCache that it can only return JSONObject result.
    internal fun saveAsync(
        client: ParseHttpClient?,
        operationSet: ParseOperationSet,
        sessionToken: String
    ): Task<JSONObject?> {
        val command: ParseRESTCommand =
            currentSaveEventuallyCommand(operationSet, PointerEncoder.get(), sessionToken)
        return command.executeAsync(client!!)
    }

    /**
     * Saves this object to the server in a background thread. This is preferable to using [.save],
     * unless your code is already running from a background thread.
     *
     * @param callback `callback.done(e)` is called when the save completes.
     */
    fun saveInBackground(callback: SaveCallback?) {
        ParseTaskUtils.callbackOnMainThreadAsync(saveInBackground(), callback)
    }

    @Throws(ParseException::class)
    open fun validateSaveEventually() {
        // do nothing
    }

    /**
     * Saves this object to the server at some unspecified time in the future, even if Parse is
     * currently inaccessible. Use this when you may not have a solid network connection, and don't
     * need to know when the save completes. If there is some problem with the object such that it
     * can't be saved, it will be silently discarded. Objects saved with this method will be stored
     * locally in an on-disk cache until they can be delivered to Parse. They will be sent immediately
     * if possible. Otherwise, they will be sent the next time a network connection is available.
     * Objects saved this way will persist even after the app is closed, in which case they will be
     * sent the next time the app is opened. If more than 10MB of data is waiting to be sent,
     * subsequent calls to `#saveEventually()` or [.deleteEventually]  will cause old
     * saves to be silently  discarded until the connection can be re-established, and the queued
     * objects can be saved.
     *
     * @param callback - A callback which will be called if the save completes before the app exits.
     */
    fun saveEventually(callback: SaveCallback?) {
        ParseTaskUtils.callbackOnMainThreadAsync(saveEventually(), callback)
    }

    /**
     * Saves this object to the server at some unspecified time in the future, even if Parse is
     * currently inaccessible. Use this when you may not have a solid network connection, and don't
     * need to know when the save completes. If there is some problem with the object such that it
     * can't be saved, it will be silently discarded. Objects saved with this method will be stored
     * locally in an on-disk cache until they can be delivered to Parse. They will be sent immediately
     * if possible. Otherwise, they will be sent the next time a network connection is available.
     * Objects saved this way will persist even after the app is closed, in which case they will be
     * sent the next time the app is opened. If more than 10MB of data is waiting to be sent,
     * subsequent calls to `#saveEventually()` or [.deleteEventually]  will cause old
     * saves to be silently  discarded until the connection can be re-established, and the queued
     * objects can be saved.
     *
     * @return A [Task] that is resolved when the save completes.
     */
    fun saveEventually(): Task<Void?> {
        if (!isDirty) {
            Parse.getEventuallyQueue().fakeObjectUpdate()
            return Task.forResult(null)
        }
        val operationSet: ParseOperationSet
        val command: ParseRESTCommand
        val runEventuallyTask: Task<JSONObject>
        synchronized(mutex) {
            updateBeforeSave()
            try {
                validateSaveEventually()
            } catch (e: ParseException) {
                return Task.forError(e)
            }

            // TODO(klimt): Once we allow multiple saves on an object, this
            // should be collecting dirty children from the estimate based on
            // whatever data is going to be sent by this saveEventually, which
            // won't necessarily be the current estimatedData. We should resolve
            // this when the multiple save code is added.
            val unsavedChildren: MutableList<ParseObject> = ArrayList()
            collectDirtyChildren(estimatedData, unsavedChildren, null)
            var localId: String? = null
            if (objectId == null) {
                localId = getOrCreateLocalId()
            }
            operationSet = startSave()
            operationSet.isSaveEventually = true

            //TODO (grantland): Convert to async
            val sessionToken = ParseUser.getCurrentSessionToken()

            // See [1]
            command = currentSaveEventuallyCommand(
                operationSet, PointerOrLocalIdEncoder.get(),
                sessionToken
            )

            // TODO: Make this logic make sense once we have deepSaveEventually
            command.localId = localId

            // Mark the command with a UUID so that we can match it up later.
            command.operationSetUUID = operationSet.uuid

            // Ensure local ids are retained before saveEventually-ing children
            command.retainLocalIds()
            for (`object` in unsavedChildren) {
                `object`.saveEventually()
            }
        }

        // We cannot modify the taskQueue inside synchronized (mutex).
        val cache = Parse.getEventuallyQueue()
        runEventuallyTask = cache.enqueueEventuallyAsync(command, this@ParseObject)
        enqueueSaveEventuallyOperationAsync(operationSet)

        // Release the extra retained local ids.
        command.releaseLocalIds()
        val handleSaveResultTask: Task<Void?>
        handleSaveResultTask = if (Parse.isLocalDatastoreEnabled()) {
            // ParsePinningEventuallyQueue calls handleSaveEventuallyResultAsync directly.
            runEventuallyTask.makeVoid()
        } else {
            runEventuallyTask.onSuccessTask { task: Task<JSONObject?> ->
                val json = task.result
                handleSaveEventuallyResultAsync(json, operationSet)
            }
        }
        return handleSaveResultTask
    }

    /**
     * Enqueues the saveEventually ParseOperationSet in [.taskQueue].
     */
    private fun enqueueSaveEventuallyOperationAsync(operationSet: ParseOperationSet) {
        check(operationSet.isSaveEventually) { "This should only be used to enqueue saveEventually operation sets" }
        taskQueue.enqueue { toAwait: Task<Void?> ->
            toAwait.continueWithTask {
                val cache = Parse.getEventuallyQueue()
                cache.waitForOperationSetAndEventuallyPin(operationSet, null)?.makeVoid()
            }
        }
    }

    /**
     * Handles the result of `saveEventually`.
     *
     *
     * In addition to normal save handling, this also notifies the saveEventually test helper.
     *
     *
     * Should be called on success or failure.
     */
    private fun handleSaveEventuallyResultAsync(
        json: JSONObject?, operationSet: ParseOperationSet
    ): Task<Void?> {
        val success = json != null
        val handleSaveResultTask = handleSaveResultAsync(json, operationSet)
        return handleSaveResultTask.onSuccessTask { task: Task<Void?>? ->
            if (success) {
                Parse.getEventuallyQueue()
                    .notifyTestHelper(ParseEventuallyQueue.TestHelper.OBJECT_UPDATED)
            }
            task
        }
    }

    /**
     * Called by [.saveInBackground] and [.saveEventually]
     * and guaranteed to be thread-safe. Subclasses can override this method to do any custom updates
     * before an object gets saved.
     */
    internal open fun updateBeforeSave() {
        // do nothing
    }

    /**
     * Deletes this object from the server at some unspecified time in the future, even if Parse is
     * currently inaccessible. Use this when you may not have a solid network connection, and don't
     * need to know when the delete completes. If there is some problem with the object such that it
     * can't be deleted, the request will be silently discarded. Delete requests made with this method
     * will be stored locally in an on-disk cache until they can be transmitted to Parse. They will be
     * sent immediately if possible. Otherwise, they will be sent the next time a network connection
     * is available. Delete instructions saved this way will persist even after the app is closed, in
     * which case they will be sent the next time the app is opened. If more than 10MB of commands are
     * waiting to be sent, subsequent calls to `#deleteEventually()` or
     * [.saveEventually] will cause old instructions to be silently discarded until the
     * connection can be re-established, and the queued objects can be saved.
     *
     * @param callback - A callback which will be called if the delete completes before the app exits.
     */
    fun deleteEventually(callback: DeleteCallback?) {
        ParseTaskUtils.callbackOnMainThreadAsync(deleteEventually(), callback)
    }

    /**
     * Deletes this object from the server at some unspecified time in the future, even if Parse is
     * currently inaccessible. Use this when you may not have a solid network connection, and don't
     * need to know when the delete completes. If there is some problem with the object such that it
     * can't be deleted, the request will be silently discarded. Delete requests made with this method
     * will be stored locally in an on-disk cache until they can be transmitted to Parse. They will be
     * sent immediately if possible. Otherwise, they will be sent the next time a network connection
     * is available. Delete instructions saved this way will persist even after the app is closed, in
     * which case they will be sent the next time the app is opened. If more than 10MB of commands are
     * waiting to be sent, subsequent calls to `#deleteEventually()` or
     * [.saveEventually] will cause old instructions to be silently discarded until the
     * connection can be re-established, and the queued objects can be saved.
     *
     * @return A [Task] that is resolved when the delete completes.
     */
    fun deleteEventually(): Task<Void?> {
        val command: ParseRESTCommand
        val runEventuallyTask: Task<JSONObject>
        synchronized(mutex) {
            validateDelete()
            isDeletingEventually += 1
            var localId: String? = null
            if (objectId == null) {
                localId = getOrCreateLocalId()
            }

            // TODO(grantland): Convert to async
            val sessionToken = ParseUser.getCurrentSessionToken()

            // See [1]
            command = ParseRESTObjectCommand.deleteObjectCommand(
                state, sessionToken
            )
            command.localId = localId
            runEventuallyTask =
                Parse.getEventuallyQueue().enqueueEventuallyAsync(command, this@ParseObject)
        }
        return if (Parse.isLocalDatastoreEnabled()) {
            // ParsePinningEventuallyQueue calls handleDeleteEventuallyResultAsync directly.
            runEventuallyTask.makeVoid()
        } else {
            runEventuallyTask.onSuccessTask { handleDeleteEventuallyResultAsync() }
        }
    }

    /**
     * Handles the result of `deleteEventually`.
     *
     *
     * Should only be called on success.
     */
    fun handleDeleteEventuallyResultAsync(): Task<Void?> {
        synchronized(mutex) { isDeletingEventually -= 1 }
        val handleDeleteResultTask = handleDeleteResultAsync()
        return handleDeleteResultTask.onSuccessTask { task: Task<Void?>? ->
            Parse.getEventuallyQueue()
                .notifyTestHelper(ParseEventuallyQueue.TestHelper.OBJECT_REMOVED)
            task
        }
    }

    /**
     * Handles the result of `fetch`.
     *
     *
     * Should only be called on success.
     */
    internal open fun handleFetchResultAsync(result: State): Task<Void?>? {
        var task = Task.forResult<Void?>(null)

        /*
         * If this object is in the offline store, then we need to make sure that we pull in any dirty
         * changes it may have before merging the server data into it.
         */
        val store = Parse.getLocalDatastore()
        if (store != null) {
            task = task.onSuccessTask {
                store.fetchLocallyAsync(this@ParseObject).makeVoid()
            }
                .continueWithTask { task14: Task<Void?> ->
                    // Catch CACHE_MISS
                    if (task14.error is ParseException
                        && (task14.error as ParseException).code == ParseException.CACHE_MISS
                    ) {
                        return@continueWithTask null
                    }
                    task14
                }
        }
        task = task.onSuccessTask {
            synchronized(mutex) {
                val newState: State = if (result.isComplete) {
                    // Result is complete, so just replace
                    result
                } else {
                    // Result is incomplete, so we'll need to apply it to the current state
                    state.newBuilder<State.Init<*>>().apply(result).build()
                }
                state = newState
            }
            null
        }
        if (store != null) {
            task =
                task.onSuccessTask { store.updateDataForObjectAsync(this@ParseObject) }
                    .continueWithTask { task: Task<Void?> ->
                        // Catch CACHE_MISS
                        if (task.error is ParseException
                            && (task.error as ParseException).code == ParseException.CACHE_MISS
                        ) {
                            return@continueWithTask null
                        }
                        task
                    }
        }
        return task
    }

    /**
     * Fetches this object with the data from the server. Call this whenever you want the state of the
     * object to reflect exactly what is on the server.
     *
     * @return The `ParseObject` that was fetched.
     * @throws ParseException Throws an exception if the server is inaccessible.
     */
    @Throws(ParseException::class)
    open fun <T : ParseObject?> fetch(): T {
        return ParseTaskUtils.wait(this.fetchInBackground())
    }

    open fun <T : ParseObject?> fetchAsync(
        sessionToken: String?, toAwait: Task<Void?>
    ): Task<T>? {
        return toAwait.onSuccessTask {
            var fetchedObjects: Map<String?, ParseObject>
            synchronized(mutex) {
                fetchedObjects = collectFetchedObjects()
            }
            val decoder: ParseDecoder = KnownParseObjectDecoder(fetchedObjects)
            objectController.fetchAsync(state, sessionToken, decoder)
        }.onSuccessTask { task: Task<State> ->
            val result = task.result
            handleFetchResultAsync(result)
        }.onSuccess { this@ParseObject as T }
    }

    /**
     * Fetches this object with the data from the server in a background thread. This is preferable to
     * using fetch(), unless your code is already running from a background thread.
     *
     * @return A [Task] that is resolved when fetch completes.
     */
    fun <T : ParseObject?> fetchInBackground(): Task<T> {
        return ParseUser.getCurrentSessionTokenAsync()
            .onSuccessTask { task: Task<String?> ->
                val sessionToken = task.result
                taskQueue.enqueue { toAwait: Task<Void?> ->
                    fetchAsync(
                        sessionToken,
                        toAwait
                    )
                }
            }
    }

    /**
     * Fetches this object with the data from the server in a background thread. This is preferable to
     * using fetch(), unless your code is already running from a background thread.
     *
     * @param callback `callback.done(object, e)` is called when the fetch completes.
     */
    fun <T : ParseObject?> fetchInBackground(callback: GetCallback<T>?) {
        ParseTaskUtils.callbackOnMainThreadAsync(this.fetchInBackground(), callback)
    }

    /**
     * If this `ParseObject` has not been fetched (i.e. [.isDataAvailable] returns `false`),
     * fetches this object with the data from the server in a background thread. This is preferable to
     * using [.fetchIfNeeded], unless your code is already running from a background thread.
     *
     * @return A [Task] that is resolved when fetch completes.
     */
    fun <T : ParseObject?> fetchIfNeededInBackground(): Task<T> {
        return if (isDataAvailable()) {
            Task.forResult(this as T)
        } else ParseUser.getCurrentSessionTokenAsync()
            .onSuccessTask { task: Task<String?> ->
                val sessionToken = task.result
                taskQueue.enqueue { toAwait: Task<Void?> ->
                    if (isDataAvailable()) {
                        return@enqueue Task.forResult(this@ParseObject as T)
                    }
                    fetchAsync(sessionToken, toAwait)
                }
            }
    }

    /**
     * If this `ParseObject` has not been fetched (i.e. [.isDataAvailable] returns `false`),
     * fetches this object with the data from the server.
     *
     * @return The fetched `ParseObject`.
     * @throws ParseException Throws an exception if the server is inaccessible.
     */
    @Throws(ParseException::class)
    open fun <T : ParseObject?> fetchIfNeeded(): T {
        return ParseTaskUtils.wait(this.fetchIfNeededInBackground())
    }

    /**
     * If this `ParseObject` has not been fetched (i.e. [.isDataAvailable] returns `false`),
     * fetches this object with the data from the server in a background thread. This is preferable to
     * using [.fetchIfNeeded], unless your code is already running from a background thread.
     *
     * @param callback `callback.done(object, e)` is called when the fetch completes.
     */
    fun <T : ParseObject?> fetchIfNeededInBackground(callback: GetCallback<T>?) {
        ParseTaskUtils.callbackOnMainThreadAsync(this.fetchIfNeededInBackground(), callback)
    }

    // Validates the delete method
    open fun validateDelete() {
        // do nothing
    }

    private fun deleteAsync(sessionToken: String?, toAwait: Task<Void?>): Task<Void?> {
        validateDelete()
        return toAwait.onSuccessTask { task: Task<Void?> ->
            isDeleting = true
            if (state.objectId() == null) {
                return@onSuccessTask task.cast<Void>() // no reason to call delete since it doesn't exist
            }
            deleteAsync(sessionToken)
        }.onSuccessTask { task: Task<Void?>? -> handleDeleteResultAsync() }
            .continueWith { task: Task<Void?> ->
                isDeleting = false
                if (task.isFaulted) {
                    throw task.error
                }
                null
            }
    }

    //TODO (grantland): I'm not sure we want direct access to this. All access to `delete` should
    // enqueue on the taskQueue...
    fun deleteAsync(sessionToken: String?): Task<Void?> {
        return objectController.deleteAsync(state, sessionToken)
    }

    /**
     * Handles the result of `delete`.
     *
     *
     * Should only be called on success.
     */
    fun handleDeleteResultAsync(): Task<Void?> {
        var task = Task.forResult<Void?>(null)
        synchronized(mutex) { isDeleted = true }
        val store = Parse.getLocalDatastore()
        if (store != null) {
            task = task.continueWithTask {
                synchronized(mutex) {
                    if (isDeleted) {
                        store.unregisterObject(this@ParseObject)
                        return@continueWithTask store.deleteDataForObjectAsync(this@ParseObject)
                    } else {
                        return@continueWithTask store.updateDataForObjectAsync(this@ParseObject)
                    }
                }
            }
        }
        return task
    }

    /**
     * Deletes this object on the server in a background thread. This is preferable to using
     * [.delete], unless your code is already running from a background thread.
     *
     * @return A [Task] that is resolved when delete completes.
     */
    fun deleteInBackground(): Task<Void?> {
        return ParseUser.getCurrentSessionTokenAsync().onSuccessTask { task: Task<String?> ->
            val sessionToken = task.result
            taskQueue.enqueue { toAwait: Task<Void?> ->
                deleteAsync(
                    sessionToken,
                    toAwait
                )
            }
        }
    }

    /**
     * Deletes this object on the server. This does not delete or destroy the object locally.
     *
     * @throws ParseException Throws an error if the object does not exist or if the internet fails.
     */
    @Throws(ParseException::class)
    fun delete() {
        ParseTaskUtils.wait(deleteInBackground())
    }

    /**
     * Deletes this object on the server in a background thread. This is preferable to using
     * [.delete], unless your code is already running from a background thread.
     *
     * @param callback `callback.done(e)` is called when the save completes.
     */
    fun deleteInBackground(callback: DeleteCallback?) {
        ParseTaskUtils.callbackOnMainThreadAsync(deleteInBackground(), callback)
    }

    /**
     * Returns `true` if this object can be serialized for saving.
     */
    private fun canBeSerialized(): Boolean {
        synchronized(mutex) {
            val result = Capture(true)

            // This method is only used for batching sets of objects for saveAll
            // and when saving children automatically. Since it's only used to
            // determine whether or not save should be called on them, it only
            // needs to examine their current values, so we use estimatedData.
            object : ParseTraverser() {
                override fun visit(value: Any): Boolean {
                    if (value is ParseFile) {
                        if (value.isDirty) {
                            result.set(false)
                        }
                    }
                    if (value is ParseObject) {
                        if (value.objectId == null) {
                            result.set(false)
                        }
                    }

                    // Continue to traverse only if it can still be serialized.
                    return result.get()
                }
            }.setYieldRoot(false).setTraverseParseObjects(true).traverse(this)
            return result.get()
        }
    }

    /**
     * Return the operations that will be sent in the next call to save.
     */
    private fun currentOperations(): ParseOperationSet {
        synchronized(mutex) { return operationSetQueue.last }
    }

    /**
     * Updates the estimated values in the map based on the given set of ParseFieldOperations.
     */
    private fun applyOperations(operations: ParseOperationSet, map: MutableMap<String, Any?>) {
        for (key in operations.keys) {
            val operation = operations[key]
            val oldValue = map[key]
            val newValue = operation!!.apply(oldValue, key)
            if (newValue != null) {
                map[key] = newValue
            } else {
                map.remove(key)
            }
        }
    }

    /**
     * Regenerates the estimatedData map from the serverData and operations.
     */
    private fun rebuildEstimatedData() {
        synchronized(mutex) {
            estimatedData.clear()
            for (key in state.keySet()) {
                estimatedData[key] = state[key]
            }
            for (operations in operationSetQueue) {
                applyOperations(operations, estimatedData)
            }
        }
    }

    fun markAllFieldsDirty() {
        synchronized(mutex) {
            for (key in state.keySet()) {
                performPut(key, state[key]!!)
            }
        }
    }

    /**
     * performOperation() is like [.put] but instead of just taking a new value,
     * it takes a ParseFieldOperation that modifies the value.
     */
    internal fun performOperation(key: String, operation: ParseFieldOperation?) {
        synchronized(mutex) {
            val oldValue = estimatedData[key]
            val newValue = operation!!.apply(oldValue, key)
            if (newValue != null) {
                estimatedData[key] = newValue
            } else {
                estimatedData.remove(key)
            }
            val oldOperation = currentOperations()[key]
            val newOperation = operation.mergeWithPrevious(oldOperation)
            currentOperations().put(key, newOperation)
        }
    }

    /**
     * Add a key-value pair to this object. It is recommended to name keys in
     * `camelCaseLikeThis`.
     *
     * @param key   Keys must be alphanumerical plus underscore, and start with a letter.
     * @param value Values may be numerical, [String], [JSONObject], [JSONArray],
     * [JSONObject.NULL], or other `ParseObject`s. value may not be `null`.
     */
    open fun put(key: String, value: Any) {
        checkKeyIsMutable(key)
        performPut(key, value)
    }

    fun performPut(key: String, value: Any) {
        lateinit var valueToAdd: Any

        if (value is JSONObject) {
            val decoder = ParseDecoder.get()
            valueToAdd = decoder.convertJSONObjectToMap(value)
        } else if (value is JSONArray) {
            val decoder = ParseDecoder.get()
            valueToAdd = decoder.convertJSONArrayToList(value)
        }
        require(isValidType(valueToAdd)) { "invalid type for value: " + value.javaClass.toString() }
        performOperation(key, ParseSetOperation(valueToAdd))
    }

    /**
     * Whether this object has a particular key. Same as [.containsKey].
     *
     * @param key The key to check for
     * @return Whether this object contains the key
     */
    fun has(key: String): Boolean {
        return containsKey(key)
    }
    /**
     * Atomically increments the given key by the given number.
     *
     * @param key    The key to increment.
     * @param amount The amount to increment by.
     */
    /**
     * Atomically increments the given key by 1.
     *
     * @param key The key to increment.
     */
    @JvmOverloads
    fun increment(key: String, amount: Number = 1) {
        val operation = ParseIncrementOperation(amount)
        performOperation(key, operation)
    }

    /**
     * Atomically adds an object to the end of the array associated with a given key.
     *
     * @param key   The key.
     * @param value The object to add.
     */
    fun add(key: String, value: Any) {
        this.addAll(key, listOf(value))
    }

    /**
     * Atomically adds the objects contained in a `Collection` to the end of the array
     * associated with a given key.
     *
     * @param key    The key.
     * @param values The objects to add.
     */
    fun addAll(key: String, values: Collection<*>?) {
        val operation = ParseAddOperation(values)
        performOperation(key, operation)
    }

    /**
     * Atomically adds an object to the array associated with a given key, only if it is not already
     * present in the array. The position of the insert is not guaranteed.
     *
     * @param key   The key.
     * @param value The object to add.
     */
    fun addUnique(key: String, value: Any) {
        addAllUnique(key, listOf(value))
    }

    /**
     * Atomically adds the objects contained in a `Collection` to the array associated with a
     * given key, only adding elements which are not already present in the array. The position of the
     * insert is not guaranteed.
     *
     * @param key    The key.
     * @param values The objects to add.
     */
    fun addAllUnique(key: String, values: Collection<*>?) {
        val operation = ParseAddUniqueOperation(values)
        performOperation(key, operation)
    }

    /**
     * Removes a key from this object's data if it exists.
     *
     * @param key The key to remove.
     */
    open fun remove(key: String) {
        checkKeyIsMutable(key)
        performRemove(key)
    }

    fun performRemove(key: String) {
        synchronized(mutex) {
            val `object` = get(key)
            if (`object` != null) {
                performOperation(key, ParseDeleteOperation.getInstance())
            }
        }
    }

    /**
     * Atomically removes all instances of the objects contained in a `Collection` from the
     * array associated with a given key. To maintain consistency with the Java Collection API, there
     * is no method removing all instances of a single object. Instead, you can call
     * `parseObject.removeAll(key, Arrays.asList(value))`.
     *
     * @param key    The key.
     * @param values The objects to remove.
     */
    fun removeAll(key: String, values: Collection<*>?) {
        checkKeyIsMutable(key)
        val operation = ParseRemoveOperation(values)
        performOperation(key, operation)
    }

    private fun checkKeyIsMutable(key: String) {
        require(isKeyMutable(key)) {
            ("Cannot modify `" + key
                    + "` property of an " + className + " object.")
        }
    }

    internal open fun isKeyMutable(key: String?): Boolean {
        return true
    }

    /**
     * Whether this object has a particular key. Same as [.has].
     *
     * @param key The key to check for
     * @return Whether this object contains the key
     */
    fun containsKey(key: String): Boolean {
        synchronized(mutex) { return estimatedData.containsKey(key) }
    }

    /**
     * Access a [String] value.
     *
     * @param key The key to access the value for.
     * @return `null` if there is no such key or if it is not a [String].
     */
    fun getString(key: String): String? {
        synchronized(mutex) {
            checkGetAccess(key)
            val value = estimatedData[key]
            return if (value !is String) {
                null
            } else value
        }
    }

    /**
     * Access a `byte[]` value.
     *
     * @param key The key to access the value for.
     * @return `null` if there is no such key or if it is not a `byte[]`.
     */
    fun getBytes(key: String): ByteArray? {
        synchronized(mutex) {
            checkGetAccess(key)
            val value = estimatedData[key]
            return if (value !is ByteArray) {
                null
            } else value
        }
    }

    /**
     * Access a [Number] value.
     *
     * @param key The key to access the value for.
     * @return `null` if there is no such key or if it is not a [Number].
     */
    fun getNumber(key: String): Number? {
        synchronized(mutex) {
            checkGetAccess(key)
            val value = estimatedData[key]
            return if (value !is Number) {
                null
            } else value
        }
    }

    /**
     * Access a [JSONArray] value.
     *
     * @param key The key to access the value for.
     * @return `null` if there is no such key or if it is not a [JSONArray].
     */
    fun getJSONArray(key: String): JSONArray? {
        synchronized(mutex) {
            checkGetAccess(key)
            var value = estimatedData[key]
            if (value is List<*>) {
                value = PointerOrLocalIdEncoder.get().encode(value)
            }
            return if (value !is JSONArray) {
                null
            } else value
        }
    }

    /**
     * Access a [List] value.
     *
     * @param key The key to access the value for
     * @return `null` if there is no such key or if the value can't be converted to a
     * [List].
     */
    fun <T> getList(key: String?): List<T>? {
        synchronized(mutex) {
            val value = estimatedData[key] as? List<*> ?: return null
            return value as List<T>
        }
    }

    /**
     * Access a [Map] value
     *
     * @param key The key to access the value for
     * @return `null` if there is no such key or if the value can't be converted to a
     * [Map].
     */
    fun <V> getMap(key: String?): Map<String, V>? {
        synchronized(mutex) {
            val value = estimatedData[key] as? Map<*, *> ?: return null
            return value as Map<String, V>
        }
    }

    /**
     * Access a [JSONObject] value.
     *
     * @param key The key to access the value for.
     * @return `null` if there is no such key or if it is not a [JSONObject].
     */
    fun getJSONObject(key: String): JSONObject? {
        synchronized(mutex) {
            checkGetAccess(key)
            var value = estimatedData[key]
            if (value is Map<*, *>) {
                value = PointerOrLocalIdEncoder.get().encode(value)
            }
            return if (value !is JSONObject) {
                null
            } else value
        }
    }

    /**
     * Access an `int` value.
     *
     * @param key The key to access the value for.
     * @return `0` if there is no such key or if it is not a `int`.
     */
    fun getInt(key: String): Int {
        val number = getNumber(key) ?: return 0
        return number.toInt()
    }

    /**
     * Access a `double` value.
     *
     * @param key The key to access the value for.
     * @return `0` if there is no such key or if it is not a `double`.
     */
    fun getDouble(key: String): Double {
        val number = getNumber(key) ?: return 0.0
        return number.toDouble()
    }

    /**
     * Access a `long` value.
     *
     * @param key The key to access the value for.
     * @return `0` if there is no such key or if it is not a `long`.
     */
    fun getLong(key: String): Long {
        val number = getNumber(key) ?: return 0
        return number.toLong()
    }

    /**
     * Access a `boolean` value.
     *
     * @param key The key to access the value for.
     * @return `false` if there is no such key or if it is not a `boolean`.
     */
    fun getBoolean(key: String): Boolean {
        synchronized(mutex) {
            checkGetAccess(key)
            val value = estimatedData[key]
            return if (value !is Boolean) {
                false
            } else value
        }
    }

    /**
     * Access a [Date] value.
     *
     * @param key The key to access the value for.
     * @return `null` if there is no such key or if it is not a [Date].
     */
    fun getDate(key: String): Date? {
        synchronized(mutex) {
            checkGetAccess(key)
            val value = estimatedData[key]
            return if (value !is Date) {
                null
            } else value
        }
    }

    /**
     * Access a `ParseObject` value. This function will not perform a network request. Unless the
     * `ParseObject` has been downloaded (e.g. by a [ParseQuery.include] or by calling
     * [.fetchIfNeeded] or [.fetch]), [.isDataAvailable] will return
     * `false`.
     *
     * @param key The key to access the value for.
     * @return `null` if there is no such key or if it is not a `ParseObject`.
     */
    fun getParseObject(key: String): ParseObject? {
        val value = get(key)
        return if (value !is ParseObject) {
            null
        } else value
    }

    /**
     * Access a [ParseUser] value. This function will not perform a network request. Unless the
     * `ParseObject` has been downloaded (e.g. by a [ParseQuery.include] or by calling
     * [.fetchIfNeeded] or [.fetch]), [.isDataAvailable] will return
     * `false`.
     *
     * @param key The key to access the value for.
     * @return `null` if there is no such key or if the value is not a [ParseUser].
     */
    fun getParseUser(key: String): ParseUser? {
        val value = get(key)
        return if (value !is ParseUser) {
            null
        } else value
    }

    /**
     * Access a [ParseFile] value. This function will not perform a network request. Unless the
     * [ParseFile] has been downloaded (e.g. by calling [ParseFile.getData]),
     * [ParseFile.isDataAvailable] will return `false`.
     *
     * @param key The key to access the value for.
     * @return `null` if there is no such key or if it is not a [ParseFile].
     */
    fun getParseFile(key: String): ParseFile? {
        val value = get(key)
        return if (value !is ParseFile) {
            null
        } else value
    }

    /**
     * Access a [ParseGeoPoint] value.
     *
     * @param key The key to access the value for
     * @return `null` if there is no such key or if it is not a [ParseGeoPoint].
     */
    fun getParseGeoPoint(key: String): ParseGeoPoint? {
        synchronized(mutex) {
            checkGetAccess(key)
            val value = estimatedData[key]
            return if (value !is ParseGeoPoint) {
                null
            } else value
        }
    }

    /**
     * Access a [ParsePolygon] value.
     *
     * @param key The key to access the value for
     * @return `null` if there is no such key or if it is not a [ParsePolygon].
     */
    fun getParsePolygon(key: String): ParsePolygon? {
        synchronized(mutex) {
            checkGetAccess(key)
            val value = estimatedData[key]
            return if (value !is ParsePolygon) {
                null
            } else value
        }
    }

    /**
     * Access the [ParseACL] governing this object.
     */
    fun getACL(): ParseACL? {
        return getACL(true)
    }

    /**
     * Set the [ParseACL] governing this object.
     */
    fun setACL(acl: ParseACL) {
        put(KEY_ACL, acl)
    }

    private fun getACL(mayCopy: Boolean): ParseACL? {
        synchronized(mutex) {
            checkGetAccess(KEY_ACL)
            val acl = (estimatedData[KEY_ACL] ?: return null) as? ParseACL
                ?: throw RuntimeException("only ACLs can be stored in the ACL key")
            if (mayCopy && acl.isShared) {
                val copy = ParseACL(acl)
                estimatedData[KEY_ACL] = copy
                return copy
            }
            return acl
        }
    }

    /**
     * Gets whether the `ParseObject` has been fetched.
     *
     * @return `true` if the `ParseObject` is new or has been fetched or refreshed. `false`
     * otherwise.
     */
    fun isDataAvailable(): Boolean {
        synchronized(mutex) { return state.isComplete }
    }

    /**
     * Gets whether the `ParseObject` specified key has been fetched.
     * This means the property can be accessed safely.
     *
     * @return `true` if the `ParseObject` key is new or has been fetched or refreshed. `false`
     * otherwise.
     */
    fun isDataAvailable(key: String): Boolean {
        synchronized(mutex) {
            // Fallback to estimatedData to include dirty changes.
            return isDataAvailable() || state.availableKeys()
                .contains(key) || estimatedData.containsKey(key)
        }
    }

    /**
     * Access or create a [ParseRelation] value for a key
     *
     * @param key The key to access the relation for.
     * @return the ParseRelation object if the relation already exists for the key or can be created
     * for this key.
     */
    fun <T : ParseObject?> getRelation(key: String): ParseRelation<T> {
        synchronized(mutex) {

            // All the sanity checking is done when add or remove is called on the relation.
            val value = estimatedData[key]
            return if (value is ParseRelation<*>) {
                val relation = value as ParseRelation<T>
                relation.ensureParentAndKey(this, key)
                relation
            } else {
                val relation = ParseRelation<T>(this, key)
                /*
                 * We put the relation into the estimated data so that we'll get the same instance later,
                 * which may have known objects cached. If we rebuildEstimatedData, then this relation will
                 * be lost, and we'll get a new one. That's okay, because any cached objects it knows about
                 * must be replayable from the operations in the queue. If there were any objects in this
                 * relation that weren't still in the queue, then they would be in the copy of the
                 * ParseRelation that's in the serverData, so we would have gotten that instance instead.
                 */estimatedData[key] = relation
                relation
            }
        }
    }

    /**
     * Access a value. In most cases it is more convenient to use a helper function such as
     * [.getString] or [.getInt].
     *
     * @param key The key to access the value for.
     * @return `null` if there is no such key.
     */
    operator fun get(key: String): Any? {
        synchronized(mutex) {
            if (key == KEY_ACL) {
                return getACL()
            }
            checkGetAccess(key)
            val value = estimatedData[key]

            // A relation may be deserialized without a parent or key.
            // Either way, make sure it's consistent.
            if (value is ParseRelation<*>) {
                value.ensureParentAndKey(this, key)
            }
            return value
        }
    }

    private fun checkGetAccess(key: String) {
        check(isDataAvailable(key)) { "ParseObject has no data for '$key'. Call fetchIfNeeded() to get the data." }
    }

    fun hasSameId(other: ParseObject): Boolean {
        synchronized(mutex) { return className != null && objectId != null && className == other.className && objectId == other.objectId }
    }

    fun registerSaveListener(callback: GetCallback<ParseObject>?) {
        synchronized(mutex) { saveEvent.subscribe(callback) }
    }

    fun unregisterSaveListener(callback: GetCallback<ParseObject>?) {
        synchronized(mutex) { saveEvent.unsubscribe(callback) }
    }

    /**
     * Called when a non-pointer is being created to allow additional initialization to occur.
     */
    internal open fun setDefaultValues() {
        if (needsDefaultACL() && ParseACL.getDefaultACL() != null) {
            setACL(ParseACL.getDefaultACL())
        }
    }

    /**
     * Determines whether this object should get a default ACL. Override in subclasses to turn off
     * default ACLs.
     */
    internal open fun needsDefaultACL(): Boolean {
        return true
    }

    /**
     * Loads data from the local datastore into this object, if it has not been fetched from the
     * server already. If the object is not stored in the local datastore, this method with do
     * nothing.
     */
    open fun <T : ParseObject?> fetchFromLocalDatastoreAsync(): Task<T>? {
        check(Parse.isLocalDatastoreEnabled()) {
            "Method requires Local Datastore. " +
                    "Please refer to `Parse#enableLocalDatastore(Context)`."
        }
        return Parse.getLocalDatastore().fetchLocallyAsync(this as T)
    }

    /**
     * Loads data from the local datastore into this object, if it has not been fetched from the
     * server already. If the object is not stored in the local datastore, this method with do
     * nothing.
     */
    fun <T : ParseObject?> fetchFromLocalDatastoreInBackground(callback: GetCallback<T>?) {
        ParseTaskUtils.callbackOnMainThreadAsync(fetchFromLocalDatastoreAsync(), callback)
    }

    /**
     * Loads data from the local datastore into this object, if it has not been fetched from the
     * server already. If the object is not stored in the local datastore, this method with throw a
     * CACHE_MISS exception.
     *
     * @throws ParseException exception if fails
     */
    @Throws(ParseException::class)
    fun fetchFromLocalDatastore() {
        ParseTaskUtils.wait(fetchFromLocalDatastoreAsync())
    }

    /**
     * Stores the object and every object it points to in the local datastore, recursively. If those
     * other objects have not been fetched from Parse, they will not be stored. However, if they have
     * changed data, all of the changes will be retained. To get the objects back later, you can use
     * [ParseQuery.fromLocalDatastore], or you can create an unfetched pointer with
     * [.createWithoutData] and then call [.fetchFromLocalDatastore] on
     * it.
     *
     * @param callback the callback
     * @see .unpinInBackground
     */
    fun pinInBackground(name: String = DEFAULT_PIN, callback: SaveCallback?) {
        ParseTaskUtils.callbackOnMainThreadAsync(pinInBackground(name), callback)
    }

    /**
     * Stores the object and every object it points to in the local datastore, recursively. If those
     * other objects have not been fetched from Parse, they will not be stored. However, if they have
     * changed data, all of the changes will be retained. To get the objects back later, you can use
     * [ParseQuery.fromLocalDatastore], or you can create an unfetched pointer with
     * [.createWithoutData] and then call [.fetchFromLocalDatastore] on
     * it.
     *
     * @return A [Task] that is resolved when pinning completes.
     * @see .unpinInBackground
     */
    fun pinInBackground(name: String = DEFAULT_PIN): Task<Void> {
        return pinAllInBackground(name, listOf(this))
    }

    fun pinInBackground(name: String = DEFAULT_PIN, includeAllChildren: Boolean): Task<Void> {
        return pinAllInBackground(name, listOf(this), includeAllChildren)
    }

    /**
     * Stores the object and every object it points to in the local datastore, recursively. If those
     * other objects have not been fetched from Parse, they will not be stored. However, if they have
     * changed data, all of the changes will be retained. To get the objects back later, you can use
     * [ParseQuery.fromLocalDatastore], or you can create an unfetched pointer with
     * [.createWithoutData] and then call [.fetchFromLocalDatastore] on
     * it.
     *
     * @throws ParseException exception if fails
     * @see .unpin
     */
    @Throws(ParseException::class)
    fun pin(name: String = DEFAULT_PIN) {
        ParseTaskUtils.wait(pinInBackground(name))
    }

    /**
     * Stores the object and every object it points to in the local datastore, recursively. If those
     * other objects have not been fetched from Parse, they will not be stored. However, if they have
     * changed data, all of the changes will be retained. To get the objects back later, you can use
     * [ParseQuery.fromLocalDatastore], or you can create an unfetched pointer with
     * [.createWithoutData] and then call [.fetchFromLocalDatastore] on
     * it.
     *
     * @param callback the callback
     * @see .unpinInBackground
     * @see .DEFAULT_PIN
     */
    fun pinInBackground(callback: SaveCallback?) {
        ParseTaskUtils.callbackOnMainThreadAsync(pinInBackground(), callback)
    }

    /**
     * Stores the object and every object it points to in the local datastore, recursively. If those
     * other objects have not been fetched from Parse, they will not be stored. However, if they have
     * changed data, all of the changes will be retained. To get the objects back later, you can use
     * [ParseQuery.fromLocalDatastore], or you can create an unfetched pointer with
     * [.createWithoutData] and then call [.fetchFromLocalDatastore] on
     * it.
     *
     * @return A [Task] that is resolved when pinning completes.
     * @see .unpinInBackground
     * @see .DEFAULT_PIN
     */
    fun pinInBackground(): Task<Void> {
        return pinAllInBackground(DEFAULT_PIN, listOf(this))
    }

    /**
     * Stores the object and every object it points to in the local datastore, recursively. If those
     * other objects have not been fetched from Parse, they will not be stored. However, if they have
     * changed data, all of the changes will be retained. To get the objects back later, you can use
     * [ParseQuery.fromLocalDatastore], or you can create an unfetched pointer with
     * [.createWithoutData] and then call [.fetchFromLocalDatastore] on
     * it.
     *
     * @throws ParseException exception if fails
     * @see .unpin
     * @see .DEFAULT_PIN
     */
    @Throws(ParseException::class)
    fun pin() {
        ParseTaskUtils.wait(pinInBackground())
    }

    /**
     * Removes the object and every object it points to in the local datastore, recursively.
     *
     * @param callback the callback
     * @see .pinInBackground
     */
    fun unpinInBackground(name: String = DEFAULT_PIN, callback: DeleteCallback?) {
        ParseTaskUtils.callbackOnMainThreadAsync(unpinInBackground(name), callback)
    }

    /**
     * Removes the object and every object it points to in the local datastore, recursively.
     *
     * @return A [Task] that is resolved when unpinning completes.
     * @see .pinInBackground
     */
    fun unpinInBackground(name: String = DEFAULT_PIN): Task<Void> {
        return unpinAllInBackground(name, listOf(this))
    }

    /**
     * Removes the object and every object it points to in the local datastore, recursively.
     *
     * @see .pin
     */
    @Throws(ParseException::class)
    fun unpin(name: String = DEFAULT_PIN) {
        ParseTaskUtils.wait(unpinInBackground(name))
    }

    /**
     * Removes the object and every object it points to in the local datastore, recursively.
     *
     * @param callback the callback
     * @see .pinInBackground
     * @see .DEFAULT_PIN
     */
    fun unpinInBackground(callback: DeleteCallback?) {
        ParseTaskUtils.callbackOnMainThreadAsync(unpinInBackground(), callback)
    }

    /**
     * Removes the object and every object it points to in the local datastore, recursively.
     *
     * @return A [Task] that is resolved when unpinning completes.
     * @see .pinInBackground
     * @see .DEFAULT_PIN
     */
    fun unpinInBackground(): Task<Void> {
        return unpinAllInBackground(DEFAULT_PIN, listOf(this))
    }

    /**
     * Removes the object and every object it points to in the local datastore, recursively.
     *
     * @see .pin
     * @see .DEFAULT_PIN
     */
    @Throws(ParseException::class)
    fun unpin() {
        ParseTaskUtils.wait(unpinInBackground())
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        writeToParcel(dest, ParseObjectParcelEncoder(this))
    }

    fun writeToParcel(dest: Parcel, encoder: ParseParcelEncoder) {
        synchronized(mutex) {

            // Developer warnings.
            ldsEnabledWhenParceling = Parse.isLocalDatastoreEnabled()
            val saving = hasOutstandingOperations()
            val deleting = isDeleting || isDeletingEventually > 0
            if (saving) {
                w(
                    TAG,
                    "About to parcel a ParseObject while a save / saveEventually operation is " +
                            "going on. If recovered from LDS, the unparceled object will be internally updated when " +
                            "these tasks end. If not, it will act as if these tasks have failed. This means that " +
                            "the subsequent call to save() will update again the same keys, and this is dangerous " +
                            "for certain operations, like increment(). To avoid inconsistencies, wait for operations " +
                            "to end before parceling."
                )
            }
            if (deleting) {
                w(
                    TAG,
                    "About to parcel a ParseObject while a delete / deleteEventually operation is " +
                            "going on. If recovered from LDS, the unparceled object will be internally updated when " +
                            "these tasks end. If not, it will assume it's not deleted, and might incorrectly " +
                            "return false for isDirty(). To avoid inconsistencies, wait for operations to end " +
                            "before parceling."
                )
            }
            // Write className and id first, regardless of state.
            dest.writeString(className)
            val objectId = objectId
            dest.writeByte(if (objectId != null) 1.toByte() else 0)
            if (objectId != null) dest.writeString(objectId)
            // Write state and other members
            state.writeToParcel(dest, encoder)
            dest.writeByte(if (localId != null) 1.toByte() else 0)
            if (localId != null) dest.writeString(localId)
            dest.writeByte(if (isDeleted) 1.toByte() else 0)
            // Care about dirty changes and ongoing tasks.
            var set: ParseOperationSet
            if (hasOutstandingOperations()) {
                // There's more than one set. Squash the queue, creating copies
                // to preserve the original queue when LDS is enabled.
                set = ParseOperationSet()
                for (operationSet in operationSetQueue) {
                    val copy = ParseOperationSet(operationSet)
                    copy.mergeFrom(set)
                    set = copy
                }
            } else {
                set = operationSetQueue.last
            }
            set.isSaveEventually = false
            set.toParcel(dest, encoder)
            // Pass a Bundle to subclasses.
            val bundle = Bundle()
            onSaveInstanceState(bundle)
            dest.writeBundle(bundle)
        }
    }

    /**
     * Called when parceling this ParseObject.
     * Subclasses can put values into the provided [Bundle] and receive them later
     * [.onRestoreInstanceState]. Note that internal fields are already parceled by
     * the framework.
     *
     * @param outState Bundle to host extra values
     */
    protected open fun onSaveInstanceState(outState: Bundle?) {}

    /**
     * Called when unparceling this ParseObject.
     * Subclasses can read values from the provided [Bundle] that were previously put
     * during [.onSaveInstanceState]. At this point the internal state is already
     * recovered.
     *
     * @param savedState Bundle to read the values from
     */
    protected open fun onRestoreInstanceState(savedState: Bundle?) {}
    open class State {
        private val className: String?
        private val objectId: String?
        private val createdAt: Long
        private val updatedAt: Long
        private val serverData: Map<String, Any>
        private val availableKeys: Set<String>
        val isComplete: Boolean

        constructor(builder: Init<*>) {
            className = builder.className
            objectId = builder.objectId
            createdAt = builder.createdAt
            updatedAt = if (builder.updatedAt > 0) builder.updatedAt else createdAt
            serverData = Collections.unmodifiableMap(HashMap(builder.serverData.toMap()))
            isComplete = builder.isComplete
            availableKeys = builder.availableKeys.toSet()
        }

        private constructor(parcel: Parcel, clazz: String?, decoder: ParseParcelDecoder) {
            className = clazz // Already read
            objectId = if (parcel.readByte().toInt() == 1) parcel.readString() else null
            createdAt = parcel.readLong()
            val updated = parcel.readLong()
            updatedAt = if (updated > 0) updated else createdAt
            val size = parcel.readInt()
            val map = HashMap<String, Any>()
            for (i in 0 until size) {
                val key = parcel.readString()!!
                val obj = decoder.decode(parcel)!!
                map[key] = obj
            }
            serverData = map.toMap()
            isComplete = parcel.readByte().toInt() == 1
            val available: List<String> = ArrayList()
            parcel.readStringList(available)
            availableKeys = HashSet(available)
        }

        open fun <T : Init<*>?> newBuilder(): T {
            return Builder(this) as T
        }

        fun className(): String? {
            return className
        }

        fun objectId(): String? {
            return objectId
        }

        fun createdAt(): Long {
            return createdAt
        }

        fun updatedAt(): Long {
            return updatedAt
        }

        operator fun get(key: String): Any? {
            return serverData[key]
        }

        fun keySet(): Set<String> {
            return serverData.keys
        }

        // Available keys for this object. With respect to keySet(), this includes also keys that are
        // undefined in the server, but that should be accessed without throwing.
        // These extra keys come e.g. from ParseQuery.selectKeys(). Selected keys must be available to
        // get() methods even if undefined, for consistency with complete objects.
        // For a complete object, this set is equal to keySet().
        fun availableKeys(): Set<String> {
            return availableKeys
        }

        open fun writeToParcel(dest: Parcel, encoder: ParseParcelEncoder) {
            dest.writeString(className)
            dest.writeByte(if (objectId != null) 1.toByte() else 0)
            if (objectId != null) {
                dest.writeString(objectId)
            }
            dest.writeLong(createdAt)
            dest.writeLong(updatedAt)
            dest.writeInt(serverData.size)
            val keys = serverData.keys
            for (key in keys) {
                dest.writeString(key)
                encoder.encode(serverData[key], dest)
            }
            dest.writeByte(if (isComplete) 1.toByte() else 0)
            dest.writeStringList(ArrayList(availableKeys))
        }

        override fun toString(): String {
            return String.format(
                Locale.US, "%s@%s[" +
                        "className=%s, objectId=%s, createdAt=%d, updatedAt=%d, isComplete=%s, " +
                        "serverData=%s, availableKeys=%s]",
                javaClass.name,
                Integer.toHexString(hashCode()),
                className,
                objectId,
                createdAt,
                updatedAt,
                isComplete,
                serverData,
                availableKeys
            )
        }

        abstract class Init<T : Init<T>> {
            @JvmField
            val serverData: MutableMap<String, Any?> = HashMap()
            val className: String?
            var objectId: String? = null
            var createdAt: Long = -1
            var updatedAt: Long = -1
            var isComplete = false
            var availableKeys: MutableSet<String> = HashSet()

            constructor(className: String?) {
                this.className = className
            }

            constructor(state: State) {
                className = state.className()
                objectId = state.objectId()
                createdAt = state.createdAt()
                updatedAt = state.updatedAt()
                availableKeys = Collections.synchronizedSet(
                    HashSet(
                        state.availableKeys()
                    )
                )
                for (key in state.keySet()) {
                    serverData[key] = state[key]
                    availableKeys.add(key)
                }
                isComplete = state.isComplete
            }

            abstract fun self(): T
            abstract fun <S : State?> build(): S
            fun objectId(objectId: String?): T {
                this.objectId = objectId
                return self()
            }

            fun createdAt(createdAt: Date): T {
                this.createdAt = createdAt.time
                return self()
            }

            fun createdAt(createdAt: Long): T {
                this.createdAt = createdAt
                return self()
            }

            fun updatedAt(updatedAt: Date): T {
                this.updatedAt = updatedAt.time
                return self()
            }

            fun updatedAt(updatedAt: Long): T {
                this.updatedAt = updatedAt
                return self()
            }

            fun isComplete(complete: Boolean): T {
                isComplete = complete
                return self()
            }

            fun put(key: String, value: Any?): T {
                serverData[key] = value
                availableKeys.add(key)
                return self()
            }

            fun remove(key: String): T {
                serverData.remove(key)
                return self()
            }

            fun availableKeys(keys: Set<String> = emptySet()): T {
                availableKeys.addAll(keys)
                return self()
            }

            fun clear(): T {
                objectId = null
                createdAt = -1
                updatedAt = -1
                isComplete = false
                serverData.clear()
                availableKeys.clear()
                return self()
            }

            /**
             * Applies a `State` on top of this `Builder` instance.
             *
             * @param other The `State` to apply over this instance.
             * @return A new `Builder` instance.
             */
            open fun apply(other: State): T {
                if (other.objectId() != null) {
                    objectId(other.objectId())
                }
                if (other.createdAt() > 0) {
                    createdAt(other.createdAt())
                }
                if (other.updatedAt() > 0) {
                    updatedAt(other.updatedAt())
                }
                isComplete(isComplete || other.isComplete)
                for (key in other.keySet()) {
                    put(key, other[key])
                }
                availableKeys(other.availableKeys())
                return self()
            }

            fun apply(operations: ParseOperationSet): T {
                for (key in operations.keys) {
                    val operation = operations[key]
                    val oldValue = serverData[key]
                    val newValue = operation!!.apply(oldValue, key)
                    newValue?.let { put(key, it) } ?: remove(key)
                }
                return self()
            }
        }

        internal class Builder : Init<Builder> {
            constructor(className: String) : super(className)
            constructor(state: State) : super(state)

            override fun self(): Builder {
                return this
            }

            override fun <S : State?> build(): S {
                return State(this) as S
            }
        }

        companion object {
            @JvmStatic
            fun newBuilder(className: String): Init<*> {
                return if ("_User" == className) {
                    ParseUser.State.Builder()
                } else Builder(className)
            }

            @JvmStatic
            internal fun createFromParcel(source: Parcel, decoder: ParseParcelDecoder): State {
                val className = source.readString()
                return if ("_User" == className) {
                    ParseUser.State(source, className, decoder)
                } else State(source, className, decoder)
            }
        }
    }

    companion object {
        /**
         * Default name for pinning if not specified.
         *
         * @see .pin
         * @see .unpin
         */
        const val DEFAULT_PIN = "_default"

        /*
  REST JSON Keys
  */
        const val KEY_OBJECT_ID = "objectId"
        const val KEY_CREATED_AT = "createdAt"
        const val KEY_UPDATED_AT = "updatedAt"
        const val KEY_IS_DELETING_EVENTUALLY = "__isDeletingEventually"
        private const val AUTO_CLASS_NAME = "_Automatic"
        private const val TAG = "ParseObject"
        private const val KEY_CLASS_NAME = "className"
        private const val KEY_ACL = "ACL"

        /*
  Internal JSON Keys - Used to store internal data when persisting {@code ParseObject}s locally.
  */
        private const val KEY_COMPLETE = "__complete"
        private const val KEY_OPERATIONS = "__operations"

        // Array of keys selected when querying for the object. Helps decoding nested {@code ParseObject}s
        // correctly, and helps constructing the {@code State.availableKeys()} set.
        private const val KEY_SELECTED_KEYS = "__selectedKeys"

        // Because Grantland messed up naming this... We'll only try to read from this for backward
        // compat, but I think we can be safe to assume any deleteEventuallys from long ago are obsolete
        // and not check after a while
        private const val KEY_IS_DELETING_EVENTUALLY_OLD = "isDeletingEventually"
        private val isCreatingPointerForObjectId: ThreadLocal<String?> =
            object : ThreadLocal<String?>() {
                override fun initialValue(): String? {
                    return null
                }
            }

        /*
     * This is used only so that we can pass it to createWithoutData as the objectId to make it create
     * an un-fetched pointer that has no objectId. This is useful only in the context of the offline
     * store, where you can have an un-fetched pointer for an object that can later be fetched from the
     * store.
     */
        private const val NEW_OFFLINE_OBJECT_ID_PLACEHOLDER = "*** Offline Object ***"
        @JvmField
        val CREATOR: Parcelable.Creator<ParseObject> = object : Parcelable.Creator<ParseObject> {
            override fun createFromParcel(source: Parcel): ParseObject {
                return createFromParcel(source, ParseObjectParcelDecoder())
            }

            override fun newArray(size: Int): Array<ParseObject?> {
                return arrayOfNulls(size)
            }
        }
        private val objectController: ParseObjectController
            get() = ParseCorePlugins.getInstance().objectController
        private val localIdManager: LocalIdManager
            get() = ParseCorePlugins.getInstance().localIdManager
        private val subclassingController: ParseObjectSubclassingController
            get() = ParseCorePlugins.getInstance().subclassingController

        /**
         * Creates a new `ParseObject` based upon a class name. If the class name is a special type
         * (e.g. for `ParseUser`), then the appropriate type of `ParseObject` is returned.
         *
         * @param className The class of object to create.
         * @return A new `ParseObject` for the given class name.
         */
        fun create(className: String?): ParseObject {
            return subclassingController.newInstance(className)
        }

        /**
         * Creates a new `ParseObject` based upon a subclass type. Note that the object will be
         * created based upon the [ParseClassName] of the given subclass type. For example, calling
         * create(ParseUser.class) may create an instance of a custom subclass of `ParseUser`.
         *
         * @param subclass The class of object to create.
         * @return A new `ParseObject` based upon the class name of the given subclass type.
         */
        @JvmStatic
        fun <T : ParseObject?> create(subclass: Class<T>?): T {
            return create(subclassingController.getClassName(subclass)) as T
        }

        /**
         * Creates a reference to an existing `ParseObject` for use in creating associations between
         * `ParseObject`s. Calling [.isDataAvailable] on this object will return
         * `false` until [.fetchIfNeeded] or [.fetch] has been called. No network
         * request will be made.
         *
         * @param className The object's class.
         * @param objectId  The object id for the referenced object.
         * @return A `ParseObject` without data.
         */
        fun createWithoutData(className: String?, objectId: String?): ParseObject? {
            val store = Parse.getLocalDatastore()
            return try {
                if (objectId == null) {
                    isCreatingPointerForObjectId.set(NEW_OFFLINE_OBJECT_ID_PLACEHOLDER)
                } else {
                    isCreatingPointerForObjectId.set(objectId)
                }
                var `object`: ParseObject? = null
                if (store != null && objectId != null) {
                    `object` = store.getObject(className, objectId)
                }
                if (`object` == null) {
                    `object` = create(className)
                    check(!`object`.hasChanges()) {
                        ("A ParseObject subclass default constructor must not make changes "
                                + "to the object that cause it to be dirty.")
                    }
                }
                `object`
            } catch (e: RuntimeException) {
                throw e
            } catch (e: Exception) {
                throw RuntimeException("Failed to create instance of subclass.", e)
            } finally {
                isCreatingPointerForObjectId.set(null)
            }
        }

        /**
         * Creates a reference to an existing `ParseObject` for use in creating associations between
         * `ParseObject`s. Calling [.isDataAvailable] on this object will return
         * `false` until  [.fetchIfNeeded] or [.fetch] has been called. No network
         * request will be made.
         *
         * @param subclass The `ParseObject` subclass to create.
         * @param objectId The object id for the referenced object.
         * @return A `ParseObject` without data.
         */
        fun <T : ParseObject?> createWithoutData(subclass: Class<T>?, objectId: String?): T? {
            return createWithoutData(subclassingController.getClassName(subclass), objectId) as T?
        }

        /**
         * Registers a custom subclass type with the Parse SDK, enabling strong-typing of those
         * `ParseObject`s whenever they appear. Subclasses must specify the [ParseClassName]
         * annotation and have a default constructor.
         *
         * @param subclass The subclass type to register.
         */
        @JvmStatic
        fun registerSubclass(subclass: Class<out ParseObject?>?) {
            subclassingController.registerSubclass(subclass)
        }

        /* package for tests */
        @JvmStatic
        fun unregisterSubclass(subclass: Class<out ParseObject?>?) {
            subclassingController.unregisterSubclass(subclass)
        }

        /**
         * Adds a task to the queue for all of the given objects.
         */
        fun <T> enqueueForAll(
            objects: List<ParseObject>,
            taskStart: Continuation<Void?, Task<T?>?>
        ): Task<T?>? {
            // The task that will be complete when all of the child queues indicate they're ready to start.
            val readyToStart = TaskCompletionSource<Void?>()

            // First, we need to lock the mutex for the queue for every object. We have to hold this
            // from at least when taskStart() is called to when obj.taskQueue enqueue is called, so
            // that saves actually get executed in the order they were setup by taskStart().
            // The locks have to be sorted so that we always acquire them in the same order.
            // Otherwise, there's some risk of deadlock.
            val locks: MutableList<Lock> = ArrayList(objects.size)
            for (obj in objects) {
                locks.add(obj.taskQueue.lock)
            }
            val lock = LockSet(locks)
            lock.lock()
            return try {
                // The task produced by TaskStart
                val fullTask: Task<T?>? = try {
                    // By running this immediately, we allow everything prior to toAwait to run before waiting
                    // for all of the queues on all of the objects.
                    taskStart.then(readyToStart.task)
                } catch (e: RuntimeException) {
                    throw e
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }

                // Add fullTask to each of the objects' queues.
                val childTasks: MutableList<Task<Void>> = ArrayList()
                for (obj in objects) {
                    obj.taskQueue.enqueue { task: Task<Void> ->
                        childTasks.add(task)
                        fullTask
                    }
                }

                // When all of the objects' queues are ready, signal fullTask that it's ready to go on.
                Task.whenAll(childTasks)
                    .continueWith({
                        readyToStart.setResult(null)
                        null
                    })
                fullTask
            } finally {
                lock.unlock()
            }
        }

        /**
         * Converts a `ParseObject.State` to a `ParseObject`.
         *
         * @param state The `ParseObject.State` to convert from.
         * @return A `ParseObject` instance.
         */
        @JvmStatic
        internal fun <T : ParseObject?> from(state: State): T {
            val `object` = createWithoutData(state.className(), state.objectId()) as T?
            synchronized(`object`!!.mutex) {
                val newState: State = if (state.isComplete) {
                    state
                } else {
                    `object`.state.newBuilder<State.Init<*>>().apply(state)
                        .build()
                }
                `object`.state = newState
            }
            return `object`
        }

        /**
         * Creates a new `ParseObject` based on data from the Parse server.
         *
         * @param json             The object's data.
         * @param defaultClassName The className of the object, if none is in the JSON.
         * @param decoder          Delegate for knowing how to decode the values in the JSON.
         * @param selectedKeys     Set of keys selected when quering for this object. If none, the object is assumed to
         * be complete, i.e. this is all the data for the object on the server.
         */
        fun <T : ParseObject?> fromJSON(
            json: JSONObject, defaultClassName: String?,
            decoder: ParseDecoder,
            selectedKeys: Set<String?>?
        ): T {
            if (selectedKeys != null && selectedKeys.isNotEmpty()) {
                val keys = JSONArray(selectedKeys)
                try {
                    json.put(KEY_SELECTED_KEYS, keys)
                } catch (e: JSONException) {
                    throw RuntimeException(e)
                }
            }
            return fromJSON(json, defaultClassName, decoder)!!
        }

        /**
         * Creates a new `ParseObject` based on data from the Parse server.
         *
         * @param json             The object's data. It is assumed to be complete, unless the JSON has the
         * [.KEY_SELECTED_KEYS] key.
         * @param defaultClassName The className of the object, if none is in the JSON.
         * @param decoder          Delegate for knowing how to decode the values in the JSON.
         */
        fun <T : ParseObject?> fromJSON(
            json: JSONObject, defaultClassName: String?,
            decoder: ParseDecoder
        ): T? {
            val className = json.optString(KEY_CLASS_NAME, defaultClassName)
                ?: return null
            val objectId = json.optString(KEY_OBJECT_ID, null)
            val isComplete = !json.has(KEY_SELECTED_KEYS)
            val `object` = createWithoutData(className, objectId) as T?
            val newState =
                `object`!!.mergeFromServer(`object`.state, json, decoder, isComplete)
            `object`.state = newState
            return `object`
        }
        //region Getter/Setter helper methods
        /**
         * Method used by parse server webhooks implementation to convert raw JSON to Parse Object
         *
         *
         * Method is used by parse server webhooks implementation to create a
         * new `ParseObject` from the incoming json payload. The method is different from
         * [.fromJSON] ()} in that it calls
         * [.build] which populates operation queue
         * rather then the server data from the incoming JSON, as at external server the incoming
         * JSON may not represent the actual server data. Also it handles
         * [ParseFieldOperations] separately.
         *
         * @param json    The object's data.
         * @param decoder Delegate for knowing how to decode the values in the JSON.
         */
        @JvmStatic
        fun <T : ParseObject?> fromJSONPayload(
            json: JSONObject, decoder: ParseDecoder
        ): T? {
            val className = json.optString(KEY_CLASS_NAME)
            if (className == null || ParseTextUtils.isEmpty(className)) {
                return null
            }
            val objectId = json.optString(KEY_OBJECT_ID, null)
            val `object` = createWithoutData(className, objectId) as T?
            `object`!!.build(json, decoder)
            return `object`
        }

        /**
         * This deletes all of the objects from the given List.
         */
        private fun <T : ParseObject> deleteAllAsync(
            objects: List<T>, sessionToken: String
        ): Task<Void?>? {
            if (objects.isEmpty()) {
                return Task.forResult(null)
            }

            // Create a list of unique objects based on objectIds
            val objectCount = objects.size
            val uniqueObjects: MutableList<ParseObject> = ArrayList(objectCount)
            val idSet = HashSet<String?>()
            for (i in 0 until objectCount) {
                val obj: ParseObject = objects[i]
                if (!idSet.contains(obj.objectId)) {
                    idSet.add(obj.objectId)
                    uniqueObjects.add(obj)
                }
            }
            return enqueueForAll<Void?>(uniqueObjects) { toAwait: Task<Void?> ->
                deleteAllAsync(
                    uniqueObjects,
                    sessionToken,
                    toAwait
                )
            }
        }

        private fun <T : ParseObject> deleteAllAsync(
            uniqueObjects: List<T>, sessionToken: String, toAwait: Task<Void?>
        ): Task<Void?> {
            return toAwait.continueWithTask {
                val objectCount = uniqueObjects.size
                val states: MutableList<State?> = ArrayList(objectCount)
                for (i in 0 until objectCount) {
                    val `object`: ParseObject = uniqueObjects[i]
                    `object`.validateDelete()
                    states.add(`object`.state)
                }
                val batchTasks = objectController.deleteAllAsync(states, sessionToken)
                val tasks: MutableList<Task<Void>> = ArrayList(objectCount)
                for (i in 0 until objectCount) {
                    val batchTask = batchTasks[i]
                    val `object` = uniqueObjects[i]
                    tasks.add(batchTask.onSuccessTask { batchTask1: Task<Void>? ->
                        `object`.handleDeleteResultAsync()
                            .continueWithTask { batchTask1 }
                    })
                }
                Task.whenAll(tasks)
            }
        }

        /**
         * Deletes each object in the provided list. This is faster than deleting each object individually
         * because it batches the requests.
         *
         * @param objects The objects to delete.
         * @throws ParseException Throws an exception if the server returns an error or is inaccessible.
         */
        @Throws(ParseException::class)
        fun <T : ParseObject> deleteAll(objects: List<T>) {
            ParseTaskUtils.wait(deleteAllInBackground(objects))
        }

        /**
         * Deletes each object in the provided list. This is faster than deleting each object individually
         * because it batches the requests.
         *
         * @param objects  The objects to delete.
         * @param callback The callback method to execute when completed.
         */
        fun <T : ParseObject> deleteAllInBackground(objects: List<T>, callback: DeleteCallback?) {
            ParseTaskUtils.callbackOnMainThreadAsync(deleteAllInBackground(objects), callback)
        }

        /**
         * Deletes each object in the provided list. This is faster than deleting each object individually
         * because it batches the requests.
         *
         * @param objects The objects to delete.
         * @return A [Task] that is resolved when deleteAll completes.
         */
        fun <T : ParseObject> deleteAllInBackground(objects: List<T>): Task<Void?> {
            return ParseUser.getCurrentSessionTokenAsync().onSuccessTask { task: Task<String> ->
                val sessionToken = task.result
                deleteAllAsync(objects, sessionToken)
            }
        }
        /**
         * Finds all of the objects that are reachable from child, including child itself, and adds them
         * to the given mutable array. It traverses arrays and json objects.
         *
         * @param node           An kind object to search for children.
         * @param dirtyChildren  The array to collect the `ParseObject`s into.
         * @param dirtyFiles     The array to collect the [ParseFile]s into.
         * @param alreadySeen    The set of all objects that have already been seen.
         * @param alreadySeenNew The set of new objects that have already been seen since the last existing object.
         */
        //endregion
        /**
         * Helper version of collectDirtyChildren so that callers don't have to add the internally used
         * parameters.
         */
        private fun collectDirtyChildren(
            node: Any,
            dirtyChildren: MutableCollection<ParseObject>?,
            dirtyFiles: MutableCollection<ParseFile>?,
            alreadySeen: MutableSet<ParseObject> =
                HashSet(),
            alreadySeenNew: MutableSet<ParseObject> =
                HashSet()
        ) {
            object : ParseTraverser() {
                override fun visit(node: Any): Boolean {
                    // If it's a file, then add it to the list if it's dirty.
                    if (node is ParseFile) {
                        if (dirtyFiles == null) {
                            return true
                        }
                        val file = node
                        if (file.url == null) {
                            dirtyFiles.add(file)
                        }
                        return true
                    }

                    // If it's anything other than a file, then just continue;
                    if (node !is ParseObject) {
                        return true
                    }
                    if (dirtyChildren == null) {
                        return true
                    }

                    // For files, we need to handle recursion manually to find cycles of new objects.
                    val `object` = node
                    var seen = alreadySeen
                    var seenNew = alreadySeenNew

                    // Check for cycles of new objects. Any such cycle means it will be
                    // impossible to save this collection of objects, so throw an exception.
                    if (`object`.objectId != null) {
                        seenNew = HashSet()
                    } else {
                        if (seenNew.contains(`object`)) {
                            throw RuntimeException("Found a circular dependency while saving.")
                        }
                        seenNew = HashSet(seenNew)
                        seenNew.add(`object`)
                    }

                    // Check for cycles of any object. If this occurs, then there's no
                    // problem, but we shouldn't recurse any deeper, because it would be
                    // an infinite recursion.
                    if (seen.contains(`object`)) {
                        return true
                    }
                    seen = HashSet(seen)
                    seen.add(`object`)

                    // Recurse into this object's children looking for dirty children.
                    // We only need to look at the child object's current estimated data,
                    // because that's the only data that might need to be saved now.
                    collectDirtyChildren(
                        `object`.estimatedData,
                        dirtyChildren,
                        dirtyFiles,
                        seen,
                        seenNew
                    )
                    if (`object`.isDirty(false)) {
                        dirtyChildren.add(`object`)
                    }
                    return true
                }
            }.setYieldRoot(true).traverse(node)
        }

        /**
         * This saves all of the objects and files reachable from the given object. It does its work in
         * multiple waves, saving as many as possible in each wave. If there's ever an error, it just
         * gives up, sets error, and returns NO.
         */
        private fun deepSaveAsync(`object`: Any, sessionToken: String?): Task<Void?> {
            val objects: MutableSet<ParseObject> = HashSet()
            val files: MutableSet<ParseFile> = HashSet()
            collectDirtyChildren(`object`, objects, files)

            // This has to happen separately from everything else because ParseUser.save() is
            // special-cased to work for lazy users, but new users can't be created by
            // ParseMultiCommand's regular save.
            val users: MutableSet<ParseUser> = HashSet()
            for (o in objects) {
                if (o is ParseUser) {
                    if (o.isLazy) {
                        users.add(o)
                    }
                }
            }
            objects.removeAll(users)

            // objects will need to wait for files to be complete since they may be nested children.
            val filesComplete = AtomicBoolean(false)
            var tasks: MutableList<Task<Void?>?> = ArrayList()
            for (file in files) {
                tasks.add(file.saveAsync(sessionToken, null, null))
            }
            val filesTask = Task.whenAll(tasks).continueWith<Void?> { task: Task<Void?>? ->
                filesComplete.set(true)
                null
            }

            // objects will need to wait for users to be complete since they may be nested children.
            val usersComplete = AtomicBoolean(false)
            tasks = ArrayList()
            for (user in users) {
                tasks.add(user.saveAsync(sessionToken))
            }
            val usersTask = Task.whenAll(tasks).continueWith<Void?> { task: Task<Void?>? ->
                usersComplete.set(true)
                null
            }
            val remaining = Capture<Set<ParseObject>>(objects)
            val objectsTask = Task.forResult<Any?>(null).continueWhile(
                { remaining.get().isNotEmpty() }) {
                // Partition the objects into two sets: those that can be save immediately,
                // and those that rely on other objects to be created first.
                val current: MutableList<ParseObject> = ArrayList()
                val nextBatch: MutableSet<ParseObject> = HashSet()
                for (obj in remaining.get()) {
                    if (obj.canBeSerialized()) {
                        current.add(obj)
                    } else {
                        nextBatch.add(obj)
                    }
                }
                remaining.set(nextBatch)
                if (current.size == 0 && filesComplete.get() && usersComplete.get()) {
                    // We do cycle-detection when building the list of objects passed to this function, so
                    // this should never get called. But we should check for it anyway, so that we get an
                    // exception instead of an infinite loop.
                    throw RuntimeException("Unable to save a ParseObject with a relation to a cycle.")
                }

                // Package all save commands together
                if (current.size == 0) {
                    return@continueWhile Task.forResult<Void?>(null)
                }
                enqueueForAll<Void?>(current) { toAwait: Task<Void?> ->
                    saveAllAsync(
                        current,
                        sessionToken,
                        toAwait
                    )
                }
            }
            return Task.whenAll(Arrays.asList(filesTask, usersTask, objectsTask))
        }

        private fun <T : ParseObject> saveAllAsync(
            uniqueObjects: List<T>, sessionToken: String?, toAwait: Task<Void?>
        ): Task<Void?> {
            return toAwait.continueWithTask {
                val objectCount = uniqueObjects.size
                val states: MutableList<State?> = ArrayList(objectCount)
                val operationsList: MutableList<ParseOperationSet> = ArrayList(objectCount)
                val decoders: MutableList<ParseDecoder> = ArrayList(objectCount)
                for (i in 0 until objectCount) {
                    val `object`: ParseObject = uniqueObjects[i]
                    `object`.updateBeforeSave()
                    `object`.validateSave()
                    states.add(`object`.state)
                    operationsList.add(`object`.startSave())
                    val fetchedObjects = `object`.collectFetchedObjects()
                    decoders.add(KnownParseObjectDecoder(fetchedObjects))
                }
                val batchTasks = objectController.saveAllAsync(
                    states, operationsList, sessionToken, decoders
                )
                val tasks: MutableList<Task<Void?>> = ArrayList(objectCount)
                for (i in 0 until objectCount) {
                    val batchTask = batchTasks[i]
                    val `object` = uniqueObjects[i]
                    val operations = operationsList[i]
                    tasks.add(batchTask.continueWithTask { batchTask1: Task<State> ->
                        val result = batchTask1.result // will be null on failure
                        `object`.handleSaveResultAsync(result, operations)
                            .continueWithTask { task1: Task<Void?> ->
                                if (task1.isFaulted || task1.isCancelled) {
                                    return@continueWithTask task1
                                }
                                batchTask1.makeVoid()
                            }
                    })
                }
                Task.whenAll(tasks)
            }
        }

        /**
         * Saves each object in the provided list. This is faster than saving each object individually
         * because it batches the requests.
         *
         * @param objects The objects to save.
         * @throws ParseException Throws an exception if the server returns an error or is inaccessible.
         */
        @Throws(ParseException::class)
        fun <T : ParseObject> saveAll(objects: List<T>) {
            ParseTaskUtils.wait(saveAllInBackground(objects))
        }

        /**
         * Saves each object in the provided list to the server in a background thread. This is preferable
         * to using saveAll, unless your code is already running from a background thread.
         *
         * @param objects  The objects to save.
         * @param callback `callback.done(e)` is called when the save completes.
         */
        fun <T : ParseObject> saveAllInBackground(objects: List<T>, callback: SaveCallback?) {
            ParseTaskUtils.callbackOnMainThreadAsync(saveAllInBackground(objects), callback)
        }

        /**
         * Saves each object in the provided list to the server in a background thread. This is preferable
         * to using saveAll, unless your code is already running from a background thread.
         *
         * @param objects The objects to save.
         * @return A [Task] that is resolved when saveAll completes.
         */
        fun <T : ParseObject> saveAllInBackground(objects: List<T>): Task<Void?> {
            return ParseUser.getCurrentUserAsync().onSuccessTask { task: Task<ParseUser?> ->
                val current = task.result
                    ?: return@onSuccessTask Task.forResult<String?>(null)
                if (!current.isLazy) {
                    return@onSuccessTask Task.forResult(current.sessionToken)
                }

                // The current user is lazy/unresolved. If it is attached to any of the objects via ACL,
                // we'll need to resolve/save it before proceeding.
                for (`object` in objects) {
                    if (!`object`.isDataAvailable(KEY_ACL)) {
                        continue
                    }
                    val acl = `object`.getACL(false) ?: continue
                    val user = acl.unresolvedUser
                    if (user != null && user.isCurrentUser) {
                        // We only need to find one, since there's only one current user.
                        return@onSuccessTask user.saveAsync(null).onSuccess { task1: Task<Void?>? ->
                            check(!acl.hasUnresolvedUser()) {
                                ("ACL has an unresolved ParseUser. "
                                        + "Save or sign up before attempting to serialize the ACL.")
                            }
                            user.sessionToken
                        }
                    }
                }
                Task.forResult(null)
            }.onSuccessTask { task: Task<String?> ->
                val sessionToken = task.result
                deepSaveAsync(objects, sessionToken)
            }
        }

        /**
         * Fetches all the objects that don't have data in the provided list in the background.
         *
         * @param objects The list of objects to fetch.
         * @return A [Task] that is resolved when fetchAllIfNeeded completes.
         */
        fun <T : ParseObject> fetchAllIfNeededInBackground(
            objects: List<T>
        ): Task<List<T>?> {
            return fetchAllAsync(objects, true)
        }

        /**
         * Fetches all the objects that don't have data in the provided list.
         *
         * @param objects The list of objects to fetch.
         * @return The list passed in for convenience.
         * @throws ParseException Throws an exception if the server returns an error or is inaccessible.
         */
        @Throws(ParseException::class)
        fun <T : ParseObject> fetchAllIfNeeded(objects: List<T>): List<T> {
            return ParseTaskUtils.wait(fetchAllIfNeededInBackground(objects))!!
        }
        //region LDS-processing methods.
        /**
         * Fetches all the objects that don't have data in the provided list in the background.
         *
         * @param objects  The list of objects to fetch.
         * @param callback `callback.done(result, e)` is called when the fetch completes.
         */
        fun <T : ParseObject> fetchAllIfNeededInBackground(
            objects: List<T>,
            callback: FindCallback<T>?
        ) {
            ParseTaskUtils.callbackOnMainThreadAsync(
                fetchAllIfNeededInBackground(objects),
                callback
            )
        }

        private fun <T : ParseObject> fetchAllAsync(
            objects: List<T>, onlyIfNeeded: Boolean
        ): Task<List<T>?> {
            return ParseUser.getCurrentUserAsync()
                .onSuccessTask { task: Task<ParseUser?> ->
                    val user = task.result
                    enqueueForAll(
                        objects
                    ) { task: Task<Void?> ->
                        fetchAllAsync(
                            objects,
                            user,
                            onlyIfNeeded,
                            task
                        )
                    }
                }
        }

        /**
         * @param onlyIfNeeded If enabled, will only fetch if the object has an objectId and
         * !isDataAvailable, otherwise it requires objectIds and will fetch regardless
         * of data availability.
         */
        // TODO(grantland): Convert to ParseUser.State
        private fun <T : ParseObject?> fetchAllAsync(
            objects: List<T>, user: ParseUser?, onlyIfNeeded: Boolean, toAwait: Task<Void?>
        ): Task<List<T>?> {
            if (objects.isEmpty()) {
                return Task.forResult(objects)
            }
            val objectIds: MutableList<String?> = ArrayList()
            var className: String? = null
            for (`object` in objects) {
                if (onlyIfNeeded && `object`!!.isDataAvailable()) {
                    continue
                }
                require(!(className != null && `object`!!.className != className)) { "All objects should have the same class" }
                className = `object`!!.className
                val objectId = `object`.objectId
                if (objectId != null) {
                    objectIds.add(`object`.objectId)
                } else require(onlyIfNeeded) { "All objects must exist on the server" }
            }
            if (objectIds.size == 0) {
                return Task.forResult(objects)
            }
            val query = ParseQuery.getQuery<T>(className!!)
                .whereContainedIn(KEY_OBJECT_ID, objectIds)
                .setLimit(objectIds.size)
            return toAwait.continueWithTask {
                query.findAsync(
                    query.builder.build(),
                    user,
                    null
                )
            }
                .onSuccess { task: Task<List<T>> ->
                    val resultMap: MutableMap<String?, T> = HashMap()
                    for (o in task.result) {
                        resultMap[o!!.objectId] = o
                    }
                    for (`object` in objects) {
                        if (onlyIfNeeded && `object`!!.isDataAvailable()) {
                            continue
                        }
                        val newObject = resultMap[`object`!!.objectId]
                            ?: throw ParseException(
                                ParseException.OBJECT_NOT_FOUND,
                                "Object id " + `object`.objectId + " does not exist"
                            )
                        if (!Parse.isLocalDatastoreEnabled()) {
                            // We only need to merge if LDS is disabled, since single instance will do the merging
                            // for us.
                            `object`.mergeFromObject(newObject)
                        }
                    }
                    objects
                }
        }
        //endregion
        /**
         * Fetches all the objects in the provided list in the background.
         *
         * @param objects The list of objects to fetch.
         * @return A [Task] that is resolved when fetch completes.
         */
        fun <T : ParseObject> fetchAllInBackground(objects: List<T>): Task<List<T>?> {
            return fetchAllAsync(objects, false)
        }

        /**
         * Fetches all the objects in the provided list.
         *
         * @param objects The list of objects to fetch.
         * @return The list passed in.
         * @throws ParseException Throws an exception if the server returns an error or is inaccessible.
         */
        @Throws(ParseException::class)
        fun <T : ParseObject> fetchAll(objects: List<T>): List<T> {
            return ParseTaskUtils.wait(fetchAllInBackground(objects))!!
        }

        /**
         * Fetches all the objects in the provided list in the background.
         *
         * @param objects  The list of objects to fetch.
         * @param callback `callback.done(result, e)` is called when the fetch completes.
         */
        fun <T : ParseObject> fetchAllInBackground(
            objects: List<T>,
            callback: FindCallback<T>?
        ) {
            ParseTaskUtils.callbackOnMainThreadAsync(fetchAllInBackground(objects), callback)
        }

        /**
         * Registers the Parse-provided `ParseObject` subclasses. Do this here in a real method rather than
         * as part of a static initializer because doing this in a static initializer can lead to
         * deadlocks
         */
        @JvmStatic
        fun registerParseSubclasses() {
            registerSubclass(ParseUser::class.java)
            registerSubclass(ParseRole::class.java)
            registerSubclass(ParseInstallation::class.java)
            registerSubclass(ParseSession::class.java)
            registerSubclass(ParsePin::class.java)
            registerSubclass(EventuallyPin::class.java)
        }

        @JvmStatic
        fun unregisterParseSubclasses() {
            unregisterSubclass(ParseUser::class.java)
            unregisterSubclass(ParseRole::class.java)
            unregisterSubclass(ParseInstallation::class.java)
            unregisterSubclass(ParseSession::class.java)
            unregisterSubclass(ParsePin::class.java)
            unregisterSubclass(EventuallyPin::class.java)
        }

        /**
         * Stores the objects and every object they point to in the local datastore, recursively. If
         * those other objects have not been fetched from Parse, they will not be stored. However, if they
         * have changed data, all of the changes will be retained. To get the objects back later, you can
         * use [ParseQuery.fromLocalDatastore], or you can create an unfetched pointer with
         * [.createWithoutData] and then call [.fetchFromLocalDatastore] on it.
         *
         * @param name     the name
         * @param objects  the objects to be pinned
         * @param callback the callback
         * @see .unpinAllInBackground
         */
        fun <T : ParseObject> pinAllInBackground(
            name: String = DEFAULT_PIN,
            objects: List<T>, callback: SaveCallback?
        ) {
            ParseTaskUtils.callbackOnMainThreadAsync(pinAllInBackground(name, objects), callback)
        }

        /**
         * Stores the objects and every object they point to in the local datastore, recursively. If
         * those other objects have not been fetched from Parse, they will not be stored. However, if they
         * have changed data, all of the changes will be retained. To get the objects back later, you can
         * use [ParseQuery.fromLocalDatastore], or you can create an unfetched pointer with
         * [.createWithoutData] and then call [.fetchFromLocalDatastore] on it.
         *
         * @param name    the name
         * @param objects the objects to be pinned
         * @return A [Task] that is resolved when pinning all completes.
         * @see .unpinAllInBackground
         */
        fun <T : ParseObject> pinAllInBackground(
            name: String = DEFAULT_PIN,
            objects: List<T>
        ): Task<Void> {
            return pinAllInBackground(name, objects, true)
        }

        private fun <T : ParseObject> pinAllInBackground(
            name: String?,
            objects: List<T>, includeAllChildren: Boolean
        ): Task<Void> {
            check(Parse.isLocalDatastoreEnabled()) {
                "Method requires Local Datastore. " +
                        "Please refer to `Parse#enableLocalDatastore(Context)`."
            }
            var task = Task.forResult<Void?>(null)

            // Resolve and persist unresolved users attached via ACL, similarly how we do in saveAsync
            for (`object` in objects) {
                task = task.onSuccessTask {
                    if (!`object`.isDataAvailable(KEY_ACL)) {
                        return@onSuccessTask Task.forResult<Void?>(null)
                    }
                    val acl = `object`.getACL(false)
                        ?: return@onSuccessTask Task.forResult<Void?>(null)
                    val user = acl.unresolvedUser
                    if (user == null || !user.isCurrentUser) {
                        return@onSuccessTask Task.forResult<Void?>(null)
                    }
                    ParseUser.pinCurrentUserIfNeededAsync(user)
                }
            }
            return task.onSuccessTask {
                Parse.getLocalDatastore().pinAllObjectsAsync(
                    name ?: DEFAULT_PIN,
                    objects,
                    includeAllChildren
                )
            }.onSuccessTask { task1: Task<Void>? ->
                // Hack to emulate persisting current user on disk after a save like in ParseUser#saveAsync
                // Note: This does not persist current user if it's a child object of `objects`, it probably
                // should, but we can't unless we do something similar to #deepSaveAsync.
                if (ParseCorePlugins.PIN_CURRENT_USER == name) {
                    return@onSuccessTask task1
                }
                for (`object` in objects) {
                    if (`object` is ParseUser) {
                        val user = `object` as ParseUser
                        if (user.isCurrentUser) {
                            return@onSuccessTask ParseUser.pinCurrentUserIfNeededAsync(user)
                        }
                    }
                }
                task1
            }
        }

        /**
         * Stores the objects and every object they point to in the local datastore, recursively. If
         * those other objects have not been fetched from Parse, they will not be stored. However, if they
         * have changed data, all of the changes will be retained. To get the objects back later, you can
         * use [ParseQuery.fromLocalDatastore], or you can create an unfetched pointer with
         * [.createWithoutData] and then call [.fetchFromLocalDatastore] on it.
         * [.fetchFromLocalDatastore] on it.
         *
         * @param name    the name
         * @param objects the objects to be pinned
         * @throws ParseException exception if fails
         * @see .unpinAll
         */
        @Throws(ParseException::class)
        fun <T : ParseObject> pinAll(
            name: String = DEFAULT_PIN,
            objects: List<T>
        ) {
            ParseTaskUtils.wait(pinAllInBackground(name, objects))
        }

        /**
         * Stores the objects and every object they point to in the local datastore, recursively. If
         * those other objects have not been fetched from Parse, they will not be stored. However, if they
         * have changed data, all of the changes will be retained. To get the objects back later, you can
         * use [ParseQuery.fromLocalDatastore], or you can create an unfetched pointer with
         * [.createWithoutData] and then call [.fetchFromLocalDatastore] on it.
         *
         * @param objects  the objects to be pinned
         * @param callback the callback
         * @see .unpinAllInBackground
         * @see .DEFAULT_PIN
         */
        fun <T : ParseObject> pinAllInBackground(
            objects: List<T>,
            callback: SaveCallback?
        ) {
            ParseTaskUtils.callbackOnMainThreadAsync(
                pinAllInBackground(DEFAULT_PIN, objects),
                callback
            )
        }

        /**
         * Stores the objects and every object they point to in the local datastore, recursively. If
         * those other objects have not been fetched from Parse, they will not be stored. However, if they
         * have changed data, all of the changes will be retained. To get the objects back later, you can
         * use [ParseQuery.fromLocalDatastore], or you can create an unfetched pointer with
         * [.createWithoutData] and then call [.fetchFromLocalDatastore] on it.
         *
         * @param objects the objects to be pinned
         * @return A [Task] that is resolved when pinning all completes.
         * @see .unpinAllInBackground
         * @see .DEFAULT_PIN
         */
        fun <T : ParseObject> pinAllInBackground(objects: List<T>): Task<Void> {
            return pinAllInBackground(DEFAULT_PIN, objects)
        }

        /**
         * Stores the objects and every object they point to in the local datastore, recursively. If
         * those other objects have not been fetched from Parse, they will not be stored. However, if they
         * have changed data, all of the changes will be retained. To get the objects back later, you can
         * use [ParseQuery.fromLocalDatastore], or you can create an unfetched pointer with
         * [.createWithoutData] and then call [.fetchFromLocalDatastore] on it.
         *
         * @param objects the objects to be pinned
         * @throws ParseException exception if fails
         * @see .unpinAll
         * @see .DEFAULT_PIN
         */
        @Throws(ParseException::class)
        fun <T : ParseObject> pinAll(objects: List<T>) {
            ParseTaskUtils.wait(pinAllInBackground(DEFAULT_PIN, objects))
        }

        /**
         * Removes the objects and every object they point to in the local datastore, recursively.
         *
         * @param name     the name
         * @param objects  the objects
         * @param callback the callback
         * @see .pinAllInBackground
         */
        fun <T : ParseObject> unpinAllInBackground(
            name: String = DEFAULT_PIN, objects: List<T>?,
            callback: DeleteCallback?
        ) {
            ParseTaskUtils.callbackOnMainThreadAsync(unpinAllInBackground(name, objects), callback)
        }

        /**
         * Removes the objects and every object they point to in the local datastore, recursively.
         *
         * @param name    the name
         * @param objects the objects
         * @return A [Task] that is resolved when unpinning all completes.
         * @see .pinAllInBackground
         */
        fun <T : ParseObject> unpinAllInBackground(
            name: String = DEFAULT_PIN,
            objects: List<T>?
        ): Task<Void> {
            check(Parse.isLocalDatastoreEnabled()) {
                "Method requires Local Datastore. " +
                        "Please refer to `Parse#enableLocalDatastore(Context)`."
            }
            return Parse.getLocalDatastore().unpinAllObjectsAsync(name, objects)
        }

        /**
         * Removes the objects and every object they point to in the local datastore, recursively.
         *
         * @param name    the name
         * @param objects the objects
         * @throws ParseException exception if fails
         * @see .pinAll
         */
        @Throws(ParseException::class)
        fun <T : ParseObject> unpinAll(
            name: String = DEFAULT_PIN,
            objects: List<T>?
        ) {
            ParseTaskUtils.wait(unpinAllInBackground(name, objects))
        }

        /**
         * Removes the objects and every object they point to in the local datastore, recursively.
         *
         * @param objects  the objects
         * @param callback the callback
         * @see .pinAllInBackground
         * @see .DEFAULT_PIN
         */
        fun <T : ParseObject> unpinAllInBackground(
            objects: List<T>?,
            callback: DeleteCallback?
        ) {
            ParseTaskUtils.callbackOnMainThreadAsync(
                unpinAllInBackground(DEFAULT_PIN, objects),
                callback
            )
        }

        /**
         * Removes the objects and every object they point to in the local datastore, recursively.
         *
         * @param objects the objects
         * @return A [Task] that is resolved when unpinning all completes.
         * @see .pinAllInBackground
         * @see .DEFAULT_PIN
         */
        fun <T : ParseObject> unpinAllInBackground(objects: List<T>?): Task<Void> {
            return unpinAllInBackground(DEFAULT_PIN, objects)
        }

        /**
         * Removes the objects and every object they point to in the local datastore, recursively.
         *
         * @param objects the objects
         * @throws ParseException exception if fails
         * @see .pinAll
         * @see .DEFAULT_PIN
         */
        @Throws(ParseException::class)
        fun <T : ParseObject> unpinAll(objects: List<T>?) {
            ParseTaskUtils.wait(unpinAllInBackground(DEFAULT_PIN, objects))
        }

        /**
         * Removes the objects and every object they point to in the local datastore, recursively.
         *
         * @param name     the name
         * @param callback the callback
         * @see .pinAll
         */
        fun unpinAllInBackground(name: String?, callback: DeleteCallback?) {
            ParseTaskUtils.callbackOnMainThreadAsync(unpinAllInBackground(name), callback)
        }
        /**
         * Removes the objects and every object they point to in the local datastore, recursively.
         *
         * @param name the name
         * @return A [Task] that is resolved when unpinning all completes.
         * @see .pinAll
         */
        /**
         * Removes the objects and every object they point to in the local datastore, recursively.
         *
         * @return A [Task] that is resolved when unpinning all completes.
         * @see .pinAllInBackground
         * @see .DEFAULT_PIN
         */
        @JvmOverloads
        fun unpinAllInBackground(name: String? = DEFAULT_PIN): Task<Void> {
            check(Parse.isLocalDatastoreEnabled()) {
                "Method requires Local Datastore. " +
                        "Please refer to `Parse#enableLocalDatastore(Context)`."
            }
            return Parse.getLocalDatastore().unpinAllObjectsAsync(name)
        }

        /**
         * Removes the objects and every object they point to in the local datastore, recursively.
         *
         * @param name the name
         * @throws ParseException exception if fails
         * @see .pinAll
         */
        @Throws(ParseException::class)
        fun unpinAll(name: String?) {
            ParseTaskUtils.wait(unpinAllInBackground(name))
        }

        /**
         * Removes the objects and every object they point to in the local datastore, recursively.
         *
         * @param callback the callback
         * @see .pinAllInBackground
         * @see .DEFAULT_PIN
         */
        fun unpinAllInBackground(callback: DeleteCallback?) {
            ParseTaskUtils.callbackOnMainThreadAsync(unpinAllInBackground(), callback)
        }

        /**
         * Removes the objects and every object they point to in the local datastore, recursively.
         *
         * @throws ParseException exception if fails
         * @see .pinAll
         * @see .DEFAULT_PIN
         */
        @Throws(ParseException::class)
        fun unpinAll() {
            ParseTaskUtils.wait(unpinAllInBackground())
        }

        @JvmStatic
        internal fun createFromParcel(source: Parcel, decoder: ParseParcelDecoder): ParseObject {
            val className = source.readString()
            val objectId = if (source.readByte().toInt() == 1) source.readString() else null
            // Create empty object (might be the same instance if LDS is enabled)
            // and pass to decoder before unparceling child objects in State
            val `object` = createWithoutData(className, objectId)
            if (decoder is ParseObjectParcelDecoder) {
                decoder.addKnownObject(`object`)
            }
            val state = State.createFromParcel(source, decoder)
            `object`!!.state = state
            if (source.readByte().toInt() == 1) `object`.localId = source.readString()
            if (source.readByte().toInt() == 1) `object`.isDeleted = true
            // If object.ldsEnabledWhenParceling is true, we got this from OfflineStore.
            // There is no need to restore operations in that case.
            val restoreOperations = !`object`.ldsEnabledWhenParceling
            val set = ParseOperationSet.fromParcel(source, decoder)
            if (restoreOperations) {
                for (key in set.keys) {
                    val op = set[key]
                    `object`.performOperation(key, op) // Update ops and estimatedData
                }
            }
            val bundle = source.readBundle(ParseObject::class.java.classLoader)
            `object`.onRestoreInstanceState(bundle)
            return `object`
        }
    }

    /**
     * Constructs a new `ParseObject` with no data in it. A `ParseObject` constructed in
     * this way will not have an objectId and will not persist to the database until [.save]
     * is called.
     *
     *
     * Class names must be alphanumerical plus underscore, and start with a letter. It is recommended
     * to name classes in `PascalCaseLikeThis`.
     *
     * @param theClassName The className for this `ParseObject`.
     */
    init {
        // We use a ThreadLocal rather than passing a parameter so that createWithoutData can do the
        // right thing with subclasses. It's ugly and terrible, but it does provide the development
        // experience we generally want, so... yeah. Sorry to whomever has to deal with this in the
        // future. I pinky-swear we won't make a habit of this -- you believe me, don't you?
        var theClassName = theClassName
        val objectIdForPointer = isCreatingPointerForObjectId.get()
        requireNotNull(theClassName) { "You must specify a Parse class name when creating a new ParseObject." }
        if (AUTO_CLASS_NAME == theClassName) {
            theClassName = subclassingController.getClassName(javaClass)
        }

        // If this is supposed to be created by a factory but wasn't, throw an exception.
        require(
            subclassingController.isSubclassValid(
                theClassName,
                javaClass
            )
        ) { "You must create this type of ParseObject using ParseObject.create() or the proper subclass." }
        operationSetQueue = LinkedList()
        operationSetQueue.add(ParseOperationSet())
        estimatedData = HashMap()
        val builder = newStateBuilder(theClassName)
        // When called from new, assume hasData for the whole object is true.
        if (objectIdForPointer == null) {
            setDefaultValues()
            builder.isComplete(true)
        } else {
            if (objectIdForPointer != NEW_OFFLINE_OBJECT_ID_PLACEHOLDER) {
                builder.objectId(objectIdForPointer)
            }
            builder.isComplete(false)
        }
        // This is a new untouched object, we don't need cache rebuilding, etc.
        state = builder.build()
        val store = Parse.getLocalDatastore()
        store?.registerNewObject(this)
    }
}