/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import bolts.Capture;
import bolts.Continuation;
import bolts.Task;
import bolts.TaskCompletionSource;

/**
 * The {@code ParseObject} is a local representation of data that can be saved and retrieved from
 * the Parse cloud.
 * <p/>
 * The basic workflow for creating new data is to construct a new {@code ParseObject}, use
 * {@link #put(String, Object)} to fill it with data, and then use {@link #saveInBackground()} to
 * persist to the cloud.
 * <p/>
 * The basic workflow for accessing existing data is to use a {@link ParseQuery} to specify which
 * existing data to retrieve.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class ParseObject implements Parcelable {
    /**
     * Default name for pinning if not specified.
     *
     * @see #pin()
     * @see #unpin()
     */
    public static final String DEFAULT_PIN = "_default";
    static final String KEY_IS_DELETING_EVENTUALLY = "__isDeletingEventually";
    private static final String AUTO_CLASS_NAME = "_Automatic";
    private static final String TAG = "ParseObject";
    /*
  REST JSON Keys
  */
    public static final String KEY_OBJECT_ID = "objectId";
    public static final String KEY_CREATED_AT = "createdAt";
    public static final String KEY_UPDATED_AT = "updatedAt";
    private static final String KEY_CLASS_NAME = "className";
    private static final String KEY_ACL = "ACL";
    /*
  Internal JSON Keys - Used to store internal data when persisting {@code ParseObject}s locally.
  */
    private static final String KEY_COMPLETE = "__complete";
    private static final String KEY_OPERATIONS = "__operations";
    // Array of keys selected when querying for the object. Helps decoding nested {@code ParseObject}s
    // correctly, and helps constructing the {@code State.availableKeys()} set.
    private static final String KEY_SELECTED_KEYS = "__selectedKeys";
    // Because Grantland messed up naming this... We'll only try to read from this for backward
    // compat, but I think we can be safe to assume any deleteEventuallys from long ago are obsolete
    // and not check after a while
    private static final String KEY_IS_DELETING_EVENTUALLY_OLD = "isDeletingEventually";
    private static final ThreadLocal<String> isCreatingPointerForObjectId =
            new ThreadLocal<String>() {
                @Override
                protected String initialValue() {
                    return null;
                }
            };
    /*
     * This is used only so that we can pass it to createWithoutData as the objectId to make it create
     * an un-fetched pointer that has no objectId. This is useful only in the context of the offline
     * store, where you can have an un-fetched pointer for an object that can later be fetched from the
     * store.
     */
    private static final String NEW_OFFLINE_OBJECT_ID_PLACEHOLDER =
            "*** Offline Object ***";
    public final static Creator<ParseObject> CREATOR = new Creator<ParseObject>() {
        @Override
        public ParseObject createFromParcel(Parcel source) {
            return ParseObject.createFromParcel(source, new ParseObjectParcelDecoder());
        }

        @Override
        public ParseObject[] newArray(int size) {
            return new ParseObject[size];
        }
    };
    final Object mutex = new Object();
    final TaskQueue taskQueue = new TaskQueue();
    final LinkedList<ParseOperationSet> operationSetQueue;
    // Cached State
    private final Map<String, Object> estimatedData;
    private final ParseMulticastDelegate<ParseObject> saveEvent = new ParseMulticastDelegate<>();
    String localId;
    boolean isDeleted;
    boolean isDeleting; // Since delete ops are queued, we don't need a counter.
    //TODO (grantland): Derive this off the EventuallyPins as opposed to +/- count.
    int isDeletingEventually;
    private State state;
    private boolean ldsEnabledWhenParceling;

    /**
     * The base class constructor to call in subclasses. Uses the class name specified with the
     * {@link ParseClassName} annotation on the subclass.
     */
    protected ParseObject() {
        this(AUTO_CLASS_NAME);
    }

    /**
     * Constructs a new {@code ParseObject} with no data in it. A {@code ParseObject} constructed in
     * this way will not have an objectId and will not persist to the database until {@link #save()}
     * is called.
     * <p>
     * Class names must be alphanumerical plus underscore, and start with a letter. It is recommended
     * to name classes in <code>PascalCaseLikeThis</code>.
     *
     * @param theClassName The className for this {@code ParseObject}.
     */
    public ParseObject(String theClassName) {
        // We use a ThreadLocal rather than passing a parameter so that createWithoutData can do the
        // right thing with subclasses. It's ugly and terrible, but it does provide the development
        // experience we generally want, so... yeah. Sorry to whomever has to deal with this in the
        // future. I pinky-swear we won't make a habit of this -- you believe me, don't you?
        String objectIdForPointer = isCreatingPointerForObjectId.get();

        if (theClassName == null) {
            throw new IllegalArgumentException(
                    "You must specify a Parse class name when creating a new ParseObject.");
        }
        if (AUTO_CLASS_NAME.equals(theClassName)) {
            theClassName = getSubclassingController().getClassName(getClass());
        }

        // If this is supposed to be created by a factory but wasn't, throw an exception.
        if (!getSubclassingController().isSubclassValid(theClassName, getClass())) {
            throw new IllegalArgumentException(
                    "You must create this type of ParseObject using ParseObject.create() or the proper subclass.");
        }

        operationSetQueue = new LinkedList<>();
        operationSetQueue.add(new ParseOperationSet());
        estimatedData = new HashMap<>();

        State.Init<?> builder = newStateBuilder(theClassName);
        // When called from new, assume hasData for the whole object is true.
        if (objectIdForPointer == null) {
            setDefaultValues();
            builder.isComplete(true);
        } else {
            if (!objectIdForPointer.equals(NEW_OFFLINE_OBJECT_ID_PLACEHOLDER)) {
                builder.objectId(objectIdForPointer);
            }
            builder.isComplete(false);
        }
        // This is a new untouched object, we don't need cache rebuilding, etc.
        state = builder.build();

        OfflineStore store = Parse.getLocalDatastore();
        if (store != null) {
            store.registerNewObject(this);
        }
    }

    private static ParseObjectController getObjectController() {
        return ParseCorePlugins.getInstance().getObjectController();
    }

    private static LocalIdManager getLocalIdManager() {
        return ParseCorePlugins.getInstance().getLocalIdManager();
    }

    private static ParseObjectSubclassingController getSubclassingController() {
        return ParseCorePlugins.getInstance().getSubclassingController();
    }

    /**
     * Creates a new {@code ParseObject} based upon a class name. If the class name is a special type
     * (e.g. for {@code ParseUser}), then the appropriate type of {@code ParseObject} is returned.
     *
     * @param className The class of object to create.
     * @return A new {@code ParseObject} for the given class name.
     */
    public static ParseObject create(String className) {
        return getSubclassingController().newInstance(className);
    }

    /**
     * Creates a new {@code ParseObject} based upon a subclass type. Note that the object will be
     * created based upon the {@link ParseClassName} of the given subclass type. For example, calling
     * create(ParseUser.class) may create an instance of a custom subclass of {@code ParseUser}.
     *
     * @param subclass The class of object to create.
     * @return A new {@code ParseObject} based upon the class name of the given subclass type.
     */
    @SuppressWarnings("unchecked")
    public static <T extends ParseObject> T create(Class<T> subclass) {
        return (T) create(getSubclassingController().getClassName(subclass));
    }

    /**
     * Creates a reference to an existing {@code ParseObject} for use in creating associations between
     * {@code ParseObject}s. Calling {@link #isDataAvailable()} on this object will return
     * {@code false} until {@link #fetchIfNeeded()} or {@link #fetch()} has been called. No network
     * request will be made.
     *
     * @param className The object's class.
     * @param objectId  The object id for the referenced object.
     * @return A {@code ParseObject} without data.
     */
    public static ParseObject createWithoutData(String className, String objectId) {
        OfflineStore store = Parse.getLocalDatastore();
        try {
            if (objectId == null) {
                isCreatingPointerForObjectId.set(NEW_OFFLINE_OBJECT_ID_PLACEHOLDER);
            } else {
                isCreatingPointerForObjectId.set(objectId);
            }
            ParseObject object = null;
            if (store != null && objectId != null) {
                object = store.getObject(className, objectId);
            }

            if (object == null) {
                object = create(className);
                if (object.hasChanges()) {
                    throw new IllegalStateException(
                            "A ParseObject subclass default constructor must not make changes "
                                    + "to the object that cause it to be dirty."
                    );
                }
            }

            return object;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of subclass.", e);
        } finally {
            isCreatingPointerForObjectId.set(null);
        }
    }

    /**
     * Creates a reference to an existing {@code ParseObject} for use in creating associations between
     * {@code ParseObject}s. Calling {@link #isDataAvailable()} on this object will return
     * {@code false} until  {@link #fetchIfNeeded()} or {@link #fetch()} has been called. No network
     * request will be made.
     *
     * @param subclass The {@code ParseObject} subclass to create.
     * @param objectId The object id for the referenced object.
     * @return A {@code ParseObject} without data.
     */
    @SuppressWarnings({"unused", "unchecked"})
    public static <T extends ParseObject> T createWithoutData(Class<T> subclass, String objectId) {
        return (T) createWithoutData(getSubclassingController().getClassName(subclass), objectId);
    }

    /**
     * Registers a custom subclass type with the Parse SDK, enabling strong-typing of those
     * {@code ParseObject}s whenever they appear. Subclasses must specify the {@link ParseClassName}
     * annotation and have a default constructor.
     *
     * @param subclass The subclass type to register.
     */
    public static void registerSubclass(Class<? extends ParseObject> subclass) {
        getSubclassingController().registerSubclass(subclass);
    }

    /* package for tests */
    static void unregisterSubclass(Class<? extends ParseObject> subclass) {
        getSubclassingController().unregisterSubclass(subclass);
    }

    /**
     * Adds a task to the queue for all of the given objects.
     */
    static <T> Task<T> enqueueForAll(final List<? extends ParseObject> objects,
                                     Continuation<Void, Task<T>> taskStart) {
        // The task that will be complete when all of the child queues indicate they're ready to start.
        final TaskCompletionSource<Void> readyToStart = new TaskCompletionSource<>();

        // First, we need to lock the mutex for the queue for every object. We have to hold this
        // from at least when taskStart() is called to when obj.taskQueue enqueue is called, so
        // that saves actually get executed in the order they were setup by taskStart().
        // The locks have to be sorted so that we always acquire them in the same order.
        // Otherwise, there's some risk of deadlock.
        List<Lock> locks = new ArrayList<>(objects.size());
        for (ParseObject obj : objects) {
            locks.add(obj.taskQueue.getLock());
        }
        LockSet lock = new LockSet(locks);

        lock.lock();
        try {
            // The task produced by TaskStart
            final Task<T> fullTask;
            try {
                // By running this immediately, we allow everything prior to toAwait to run before waiting
                // for all of the queues on all of the objects.
                fullTask = taskStart.then(readyToStart.getTask());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // Add fullTask to each of the objects' queues.
            final List<Task<Void>> childTasks = new ArrayList<>();
            for (ParseObject obj : objects) {
                obj.taskQueue.enqueue(new Continuation<Void, Task<T>>() {
                    @Override
                    public Task<T> then(Task<Void> task) {
                        childTasks.add(task);
                        return fullTask;
                    }
                });
            }

            // When all of the objects' queues are ready, signal fullTask that it's ready to go on.
            Task.whenAll(childTasks).continueWith(new Continuation<Void, Void>() {
                @Override
                public Void then(Task<Void> task) {
                    readyToStart.setResult(null);
                    return null;
                }
            });
            return fullTask;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Converts a {@code ParseObject.State} to a {@code ParseObject}.
     *
     * @param state The {@code ParseObject.State} to convert from.
     * @return A {@code ParseObject} instance.
     */
    static <T extends ParseObject> T from(ParseObject.State state) {
        @SuppressWarnings("unchecked")
        T object = (T) ParseObject.createWithoutData(state.className(), state.objectId());
        synchronized (object.mutex) {
            State newState;
            if (state.isComplete()) {
                newState = state;
            } else {
                newState = object.getState().newBuilder().apply(state).build();
            }
            object.setState(newState);
        }
        return object;
    }

    /**
     * Creates a new {@code ParseObject} based on data from the Parse server.
     *
     * @param json             The object's data.
     * @param defaultClassName The className of the object, if none is in the JSON.
     * @param decoder          Delegate for knowing how to decode the values in the JSON.
     * @param selectedKeys     Set of keys selected when quering for this object. If none, the object is assumed to
     *                         be complete, i.e. this is all the data for the object on the server.
     */
    public static <T extends ParseObject> T fromJSON(JSONObject json, String defaultClassName,
                                                     ParseDecoder decoder,
                                                     Set<String> selectedKeys) {
        if (selectedKeys != null && !selectedKeys.isEmpty()) {
            JSONArray keys = new JSONArray(selectedKeys);
            try {
                json.put(KEY_SELECTED_KEYS, keys);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        return fromJSON(json, defaultClassName, decoder);
    }

    /**
     * Creates a new {@code ParseObject} based on data from the Parse server.
     *
     * @param json             The object's data. It is assumed to be complete, unless the JSON has the
     *                         {@link #KEY_SELECTED_KEYS} key.
     * @param defaultClassName The className of the object, if none is in the JSON.
     * @param decoder          Delegate for knowing how to decode the values in the JSON.
     */
    public static <T extends ParseObject> T fromJSON(JSONObject json, String defaultClassName,
                                                     ParseDecoder decoder) {
        String className = json.optString(KEY_CLASS_NAME, defaultClassName);
        if (className == null) {
            return null;
        }
        String objectId = json.optString(KEY_OBJECT_ID, null);
        boolean isComplete = !json.has(KEY_SELECTED_KEYS);
        @SuppressWarnings("unchecked")
        T object = (T) ParseObject.createWithoutData(className, objectId);
        State newState = object.mergeFromServer(object.getState(), json, decoder, isComplete);
        object.setState(newState);
        return object;
    }

    //region Getter/Setter helper methods

    /**
     * Method used by parse server webhooks implementation to convert raw JSON to Parse Object
     * <p>
     * Method is used by parse server webhooks implementation to create a
     * new {@code ParseObject} from the incoming json payload. The method is different from
     * {@link #fromJSON(JSONObject, String, ParseDecoder, Set)} ()} in that it calls
     * {@link #build(JSONObject, ParseDecoder)} which populates operation queue
     * rather then the server data from the incoming JSON, as at external server the incoming
     * JSON may not represent the actual server data. Also it handles
     * {@link ParseFieldOperations} separately.
     *
     * @param json    The object's data.
     * @param decoder Delegate for knowing how to decode the values in the JSON.
     */

    static <T extends ParseObject> T fromJSONPayload(
            JSONObject json, ParseDecoder decoder) {
        String className = json.optString(KEY_CLASS_NAME);
        if (className == null || ParseTextUtils.isEmpty(className)) {
            return null;
        }
        String objectId = json.optString(KEY_OBJECT_ID, null);
        @SuppressWarnings("unchecked")
        T object = (T) ParseObject.createWithoutData(className, objectId);
        object.build(json, decoder);
        return object;
    }

    /**
     * This deletes all of the objects from the given List.
     */
    private static <T extends ParseObject> Task<Void> deleteAllAsync(
            final List<T> objects, final String sessionToken) {
        if (objects.size() == 0) {
            return Task.forResult(null);
        }

        // Create a list of unique objects based on objectIds
        int objectCount = objects.size();
        final List<ParseObject> uniqueObjects = new ArrayList<>(objectCount);
        final HashSet<String> idSet = new HashSet<>();
        for (int i = 0; i < objectCount; i++) {
            ParseObject obj = objects.get(i);
            if (!idSet.contains(obj.getObjectId())) {
                idSet.add(obj.getObjectId());
                uniqueObjects.add(obj);
            }
        }

        return enqueueForAll(uniqueObjects, new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> toAwait) {
                return deleteAllAsync(uniqueObjects, sessionToken, toAwait);
            }
        });
    }

    private static <T extends ParseObject> Task<Void> deleteAllAsync(
            final List<T> uniqueObjects, final String sessionToken, Task<Void> toAwait) {
        return toAwait.continueWithTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                int objectCount = uniqueObjects.size();
                List<ParseObject.State> states = new ArrayList<>(objectCount);
                for (int i = 0; i < objectCount; i++) {
                    ParseObject object = uniqueObjects.get(i);
                    object.validateDelete();
                    states.add(object.getState());
                }
                List<Task<Void>> batchTasks = getObjectController().deleteAllAsync(states, sessionToken);

                List<Task<Void>> tasks = new ArrayList<>(objectCount);
                for (int i = 0; i < objectCount; i++) {
                    Task<Void> batchTask = batchTasks.get(i);
                    final T object = uniqueObjects.get(i);
                    tasks.add(batchTask.onSuccessTask(new Continuation<Void, Task<Void>>() {
                        @Override
                        public Task<Void> then(final Task<Void> batchTask) {
                            return object.handleDeleteResultAsync().continueWithTask(new Continuation<Void, Task<Void>>() {
                                @Override
                                public Task<Void> then(Task<Void> task) {
                                    return batchTask;
                                }
                            });
                        }
                    }));
                }
                return Task.whenAll(tasks);
            }
        });
    }

    /**
     * Deletes each object in the provided list. This is faster than deleting each object individually
     * because it batches the requests.
     *
     * @param objects The objects to delete.
     * @throws ParseException Throws an exception if the server returns an error or is inaccessible.
     */
    public static <T extends ParseObject> void deleteAll(List<T> objects) throws ParseException {
        ParseTaskUtils.wait(deleteAllInBackground(objects));
    }

    /**
     * Deletes each object in the provided list. This is faster than deleting each object individually
     * because it batches the requests.
     *
     * @param objects  The objects to delete.
     * @param callback The callback method to execute when completed.
     */
    public static <T extends ParseObject> void deleteAllInBackground(List<T> objects, DeleteCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(deleteAllInBackground(objects), callback);
    }

    /**
     * Deletes each object in the provided list. This is faster than deleting each object individually
     * because it batches the requests.
     *
     * @param objects The objects to delete.
     * @return A {@link bolts.Task} that is resolved when deleteAll completes.
     */
    public static <T extends ParseObject> Task<Void> deleteAllInBackground(final List<T> objects) {
        return ParseUser.getCurrentSessionTokenAsync().onSuccessTask(new Continuation<String, Task<Void>>() {
            @Override
            public Task<Void> then(Task<String> task) {
                String sessionToken = task.getResult();
                return deleteAllAsync(objects, sessionToken);
            }
        });
    }

    /**
     * Finds all of the objects that are reachable from child, including child itself, and adds them
     * to the given mutable array. It traverses arrays and json objects.
     *
     * @param node           An kind object to search for children.
     * @param dirtyChildren  The array to collect the {@code ParseObject}s into.
     * @param dirtyFiles     The array to collect the {@link ParseFile}s into.
     * @param alreadySeen    The set of all objects that have already been seen.
     * @param alreadySeenNew The set of new objects that have already been seen since the last existing object.
     */
    private static void collectDirtyChildren(Object node,
                                             final Collection<ParseObject> dirtyChildren,
                                             final Collection<ParseFile> dirtyFiles,
                                             final Set<ParseObject> alreadySeen,
                                             final Set<ParseObject> alreadySeenNew) {

        new ParseTraverser() {
            @Override
            protected boolean visit(Object node) {
                // If it's a file, then add it to the list if it's dirty.
                if (node instanceof ParseFile) {
                    if (dirtyFiles == null) {
                        return true;
                    }

                    ParseFile file = (ParseFile) node;
                    if (file.getUrl() == null) {
                        dirtyFiles.add(file);
                    }
                    return true;
                }

                // If it's anything other than a file, then just continue;
                if (!(node instanceof ParseObject)) {
                    return true;
                }

                if (dirtyChildren == null) {
                    return true;
                }

                // For files, we need to handle recursion manually to find cycles of new objects.
                ParseObject object = (ParseObject) node;
                Set<ParseObject> seen = alreadySeen;
                Set<ParseObject> seenNew = alreadySeenNew;

                // Check for cycles of new objects. Any such cycle means it will be
                // impossible to save this collection of objects, so throw an exception.
                if (object.getObjectId() != null) {
                    seenNew = new HashSet<>();
                } else {
                    if (seenNew.contains(object)) {
                        throw new RuntimeException("Found a circular dependency while saving.");
                    }
                    seenNew = new HashSet<>(seenNew);
                    seenNew.add(object);
                }

                // Check for cycles of any object. If this occurs, then there's no
                // problem, but we shouldn't recurse any deeper, because it would be
                // an infinite recursion.
                if (seen.contains(object)) {
                    return true;
                }
                seen = new HashSet<>(seen);
                seen.add(object);

                // Recurse into this object's children looking for dirty children.
                // We only need to look at the child object's current estimated data,
                // because that's the only data that might need to be saved now.
                collectDirtyChildren(object.estimatedData, dirtyChildren, dirtyFiles, seen, seenNew);

                if (object.isDirty(false)) {
                    dirtyChildren.add(object);
                }

                return true;
            }
        }.setYieldRoot(true).traverse(node);
    }

    //endregion

    /**
     * Helper version of collectDirtyChildren so that callers don't have to add the internally used
     * parameters.
     */
    private static void collectDirtyChildren(Object node, Collection<ParseObject> dirtyChildren,
                                             Collection<ParseFile> dirtyFiles) {
        collectDirtyChildren(node, dirtyChildren, dirtyFiles,
                new HashSet<ParseObject>(),
                new HashSet<ParseObject>());
    }

    /**
     * This saves all of the objects and files reachable from the given object. It does its work in
     * multiple waves, saving as many as possible in each wave. If there's ever an error, it just
     * gives up, sets error, and returns NO.
     */
    private static Task<Void> deepSaveAsync(final Object object, final String sessionToken) {
        Set<ParseObject> objects = new HashSet<>();
        Set<ParseFile> files = new HashSet<>();
        collectDirtyChildren(object, objects, files);

        // This has to happen separately from everything else because ParseUser.save() is
        // special-cased to work for lazy users, but new users can't be created by
        // ParseMultiCommand's regular save.
        Set<ParseUser> users = new HashSet<>();
        for (ParseObject o : objects) {
            if (o instanceof ParseUser) {
                ParseUser user = (ParseUser) o;
                if (user.isLazy()) {
                    users.add((ParseUser) o);
                }
            }
        }
        objects.removeAll(users);

        // objects will need to wait for files to be complete since they may be nested children.
        final AtomicBoolean filesComplete = new AtomicBoolean(false);
        List<Task<Void>> tasks = new ArrayList<>();
        for (ParseFile file : files) {
            tasks.add(file.saveAsync(sessionToken, null, null));
        }
        Task<Void> filesTask = Task.whenAll(tasks).continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) {
                filesComplete.set(true);
                return null;
            }
        });

        // objects will need to wait for users to be complete since they may be nested children.
        final AtomicBoolean usersComplete = new AtomicBoolean(false);
        tasks = new ArrayList<>();
        for (final ParseUser user : users) {
            tasks.add(user.saveAsync(sessionToken));
        }
        Task<Void> usersTask = Task.whenAll(tasks).continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) {
                usersComplete.set(true);
                return null;
            }
        });

        final Capture<Set<ParseObject>> remaining = new Capture<>(objects);
        Task<Void> objectsTask = Task.forResult(null).continueWhile(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return remaining.get().size() > 0;
            }
        }, new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                // Partition the objects into two sets: those that can be save immediately,
                // and those that rely on other objects to be created first.
                final List<ParseObject> current = new ArrayList<>();
                final Set<ParseObject> nextBatch = new HashSet<>();
                for (ParseObject obj : remaining.get()) {
                    if (obj.canBeSerialized()) {
                        current.add(obj);
                    } else {
                        nextBatch.add(obj);
                    }
                }
                remaining.set(nextBatch);

                if (current.size() == 0 && filesComplete.get() && usersComplete.get()) {
                    // We do cycle-detection when building the list of objects passed to this function, so
                    // this should never get called. But we should check for it anyway, so that we get an
                    // exception instead of an infinite loop.
                    throw new RuntimeException("Unable to save a ParseObject with a relation to a cycle.");
                }

                // Package all save commands together
                if (current.size() == 0) {
                    return Task.forResult(null);
                }

                return enqueueForAll(current, new Continuation<Void, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<Void> toAwait) {
                        return saveAllAsync(current, sessionToken, toAwait);
                    }
                });
            }
        });

        return Task.whenAll(Arrays.asList(filesTask, usersTask, objectsTask));
    }

    private static <T extends ParseObject> Task<Void> saveAllAsync(
            final List<T> uniqueObjects, final String sessionToken, Task<Void> toAwait) {
        return toAwait.continueWithTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                int objectCount = uniqueObjects.size();
                List<ParseObject.State> states = new ArrayList<>(objectCount);
                List<ParseOperationSet> operationsList = new ArrayList<>(objectCount);
                List<ParseDecoder> decoders = new ArrayList<>(objectCount);
                for (int i = 0; i < objectCount; i++) {
                    ParseObject object = uniqueObjects.get(i);
                    object.updateBeforeSave();
                    object.validateSave();

                    states.add(object.getState());
                    operationsList.add(object.startSave());
                    final Map<String, ParseObject> fetchedObjects = object.collectFetchedObjects();
                    decoders.add(new KnownParseObjectDecoder(fetchedObjects));
                }
                List<Task<ParseObject.State>> batchTasks = getObjectController().saveAllAsync(
                        states, operationsList, sessionToken, decoders);

                List<Task<Void>> tasks = new ArrayList<>(objectCount);
                for (int i = 0; i < objectCount; i++) {
                    Task<ParseObject.State> batchTask = batchTasks.get(i);
                    final T object = uniqueObjects.get(i);
                    final ParseOperationSet operations = operationsList.get(i);
                    tasks.add(batchTask.continueWithTask(new Continuation<ParseObject.State, Task<Void>>() {
                        @Override
                        public Task<Void> then(final Task<ParseObject.State> batchTask) {
                            ParseObject.State result = batchTask.getResult(); // will be null on failure
                            return object.handleSaveResultAsync(result, operations).continueWithTask(new Continuation<Void, Task<Void>>() {
                                @Override
                                public Task<Void> then(Task<Void> task) {
                                    if (task.isFaulted() || task.isCancelled()) {
                                        return task;
                                    }

                                    // We still want to propagate batchTask errors
                                    return batchTask.makeVoid();
                                }
                            });
                        }
                    }));
                }
                return Task.whenAll(tasks);
            }
        });
    }

    /**
     * Saves each object in the provided list. This is faster than saving each object individually
     * because it batches the requests.
     *
     * @param objects The objects to save.
     * @throws ParseException Throws an exception if the server returns an error or is inaccessible.
     */
    public static <T extends ParseObject> void saveAll(List<T> objects) throws ParseException {
        ParseTaskUtils.wait(saveAllInBackground(objects));
    }

    /**
     * Saves each object in the provided list to the server in a background thread. This is preferable
     * to using saveAll, unless your code is already running from a background thread.
     *
     * @param objects  The objects to save.
     * @param callback {@code callback.done(e)} is called when the save completes.
     */
    public static <T extends ParseObject> void saveAllInBackground(List<T> objects, SaveCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(saveAllInBackground(objects), callback);
    }

    /**
     * Saves each object in the provided list to the server in a background thread. This is preferable
     * to using saveAll, unless your code is already running from a background thread.
     *
     * @param objects The objects to save.
     * @return A {@link bolts.Task} that is resolved when saveAll completes.
     */
    public static <T extends ParseObject> Task<Void> saveAllInBackground(final List<T> objects) {
        return ParseUser.getCurrentUserAsync().onSuccessTask(new Continuation<ParseUser, Task<String>>() {
            @Override
            public Task<String> then(Task<ParseUser> task) {
                final ParseUser current = task.getResult();
                if (current == null) {
                    return Task.forResult(null);
                }
                if (!current.isLazy()) {
                    return Task.forResult(current.getSessionToken());
                }

                // The current user is lazy/unresolved. If it is attached to any of the objects via ACL,
                // we'll need to resolve/save it before proceeding.
                for (ParseObject object : objects) {
                    if (!object.isDataAvailable(KEY_ACL)) {
                        continue;
                    }
                    final ParseACL acl = object.getACL(false);
                    if (acl == null) {
                        continue;
                    }
                    final ParseUser user = acl.getUnresolvedUser();
                    if (user != null && user.isCurrentUser()) {
                        // We only need to find one, since there's only one current user.
                        return user.saveAsync(null).onSuccess(new Continuation<Void, String>() {
                            @Override
                            public String then(Task<Void> task) {
                                if (acl.hasUnresolvedUser()) {
                                    throw new IllegalStateException("ACL has an unresolved ParseUser. "
                                            + "Save or sign up before attempting to serialize the ACL.");
                                }
                                return user.getSessionToken();
                            }
                        });
                    }
                }

                // There were no objects with ACLs pointing to unresolved users.
                return Task.forResult(null);
            }
        }).onSuccessTask(new Continuation<String, Task<Void>>() {
            @Override
            public Task<Void> then(Task<String> task) {
                final String sessionToken = task.getResult();
                return deepSaveAsync(objects, sessionToken);
            }
        });
    }

    /**
     * Fetches all the objects that don't have data in the provided list in the background.
     *
     * @param objects The list of objects to fetch.
     * @return A {@link bolts.Task} that is resolved when fetchAllIfNeeded completes.
     */
    public static <T extends ParseObject> Task<List<T>> fetchAllIfNeededInBackground(
            final List<T> objects) {
        return fetchAllAsync(objects, true);
    }

    /**
     * Fetches all the objects that don't have data in the provided list.
     *
     * @param objects The list of objects to fetch.
     * @return The list passed in for convenience.
     * @throws ParseException Throws an exception if the server returns an error or is inaccessible.
     */
    public static <T extends ParseObject> List<T> fetchAllIfNeeded(List<T> objects)
            throws ParseException {
        return ParseTaskUtils.wait(fetchAllIfNeededInBackground(objects));
    }

    //region LDS-processing methods.

    /**
     * Fetches all the objects that don't have data in the provided list in the background.
     *
     * @param objects  The list of objects to fetch.
     * @param callback {@code callback.done(result, e)} is called when the fetch completes.
     */
    public static <T extends ParseObject> void fetchAllIfNeededInBackground(final List<T> objects,
                                                                            FindCallback<T> callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(fetchAllIfNeededInBackground(objects), callback);
    }

    private static <T extends ParseObject> Task<List<T>> fetchAllAsync(
            final List<T> objects, final boolean onlyIfNeeded) {
        return ParseUser.getCurrentUserAsync().onSuccessTask(new Continuation<ParseUser, Task<List<T>>>() {
            @Override
            public Task<List<T>> then(Task<ParseUser> task) {
                final ParseUser user = task.getResult();
                return enqueueForAll(objects, new Continuation<Void, Task<List<T>>>() {
                    @Override
                    public Task<List<T>> then(Task<Void> task) {
                        return fetchAllAsync(objects, user, onlyIfNeeded, task);
                    }
                });
            }
        });
    }

    /**
     * @param onlyIfNeeded If enabled, will only fetch if the object has an objectId and
     *                     !isDataAvailable, otherwise it requires objectIds and will fetch regardless
     *                     of data availability.
     */
    // TODO(grantland): Convert to ParseUser.State
    private static <T extends ParseObject> Task<List<T>> fetchAllAsync(
            final List<T> objects, final ParseUser user, final boolean onlyIfNeeded, Task<Void> toAwait) {
        if (objects.size() == 0) {
            return Task.forResult(objects);
        }

        List<String> objectIds = new ArrayList<>();
        String className = null;
        for (T object : objects) {
            if (onlyIfNeeded && object.isDataAvailable()) {
                continue;
            }

            if (className != null && !object.getClassName().equals(className)) {
                throw new IllegalArgumentException("All objects should have the same class");
            }
            className = object.getClassName();

            String objectId = object.getObjectId();
            if (objectId != null) {
                objectIds.add(object.getObjectId());
            } else if (!onlyIfNeeded) {
                throw new IllegalArgumentException("All objects must exist on the server");
            }
        }

        if (objectIds.size() == 0) {
            return Task.forResult(objects);
        }

        final ParseQuery<T> query = ParseQuery.<T>getQuery(className)
                .whereContainedIn(KEY_OBJECT_ID, objectIds)
                .setLimit(objectIds.size());
        return toAwait.continueWithTask(new Continuation<Void, Task<List<T>>>() {
            @Override
            public Task<List<T>> then(Task<Void> task) {
                return query.findAsync(query.getBuilder().build(), user, null);
            }
        }).onSuccess(new Continuation<List<T>, List<T>>() {
            @Override
            public List<T> then(Task<List<T>> task) throws Exception {
                Map<String, T> resultMap = new HashMap<>();
                for (T o : task.getResult()) {
                    resultMap.put(o.getObjectId(), o);
                }
                for (T object : objects) {
                    if (onlyIfNeeded && object.isDataAvailable()) {
                        continue;
                    }

                    T newObject = resultMap.get(object.getObjectId());
                    if (newObject == null) {
                        throw new ParseException(
                                ParseException.OBJECT_NOT_FOUND,
                                "Object id " + object.getObjectId() + " does not exist");
                    }
                    if (!Parse.isLocalDatastoreEnabled()) {
                        // We only need to merge if LDS is disabled, since single instance will do the merging
                        // for us.
                        object.mergeFromObject(newObject);
                    }
                }
                return objects;
            }
        });
    }

    //endregion

    /**
     * Fetches all the objects in the provided list in the background.
     *
     * @param objects The list of objects to fetch.
     * @return A {@link bolts.Task} that is resolved when fetch completes.
     */
    public static <T extends ParseObject> Task<List<T>> fetchAllInBackground(final List<T> objects) {
        return fetchAllAsync(objects, false);
    }

    /**
     * Fetches all the objects in the provided list.
     *
     * @param objects The list of objects to fetch.
     * @return The list passed in.
     * @throws ParseException Throws an exception if the server returns an error or is inaccessible.
     */
    public static <T extends ParseObject> List<T> fetchAll(List<T> objects) throws ParseException {
        return ParseTaskUtils.wait(fetchAllInBackground(objects));
    }

    /**
     * Fetches all the objects in the provided list in the background.
     *
     * @param objects  The list of objects to fetch.
     * @param callback {@code callback.done(result, e)} is called when the fetch completes.
     */
    public static <T extends ParseObject> void fetchAllInBackground(List<T> objects,
                                                                    FindCallback<T> callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(fetchAllInBackground(objects), callback);
    }

    /**
     * Registers the Parse-provided {@code ParseObject} subclasses. Do this here in a real method rather than
     * as part of a static initializer because doing this in a static initializer can lead to
     * deadlocks
     */

    static void registerParseSubclasses() {
        registerSubclass(ParseUser.class);
        registerSubclass(ParseRole.class);
        registerSubclass(ParseInstallation.class);
        registerSubclass(ParseSession.class);

        registerSubclass(ParsePin.class);
        registerSubclass(EventuallyPin.class);
    }


    static void unregisterParseSubclasses() {
        unregisterSubclass(ParseUser.class);
        unregisterSubclass(ParseRole.class);
        unregisterSubclass(ParseInstallation.class);
        unregisterSubclass(ParseSession.class);

        unregisterSubclass(ParsePin.class);
        unregisterSubclass(EventuallyPin.class);
    }

    /**
     * Stores the objects and every object they point to in the local datastore, recursively. If
     * those other objects have not been fetched from Parse, they will not be stored. However, if they
     * have changed data, all of the changes will be retained. To get the objects back later, you can
     * use {@link ParseQuery#fromLocalDatastore()}, or you can create an unfetched pointer with
     * {@link #createWithoutData(Class, String)} and then call {@link #fetchFromLocalDatastore()} on it.
     *
     * @param name     the name
     * @param objects  the objects to be pinned
     * @param callback the callback
     * @see #unpinAllInBackground(String, java.util.List, DeleteCallback)
     */
    public static <T extends ParseObject> void pinAllInBackground(String name,
                                                                  List<T> objects, SaveCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(pinAllInBackground(name, objects), callback);
    }

    /**
     * Stores the objects and every object they point to in the local datastore, recursively. If
     * those other objects have not been fetched from Parse, they will not be stored. However, if they
     * have changed data, all of the changes will be retained. To get the objects back later, you can
     * use {@link ParseQuery#fromLocalDatastore()}, or you can create an unfetched pointer with
     * {@link #createWithoutData(Class, String)} and then call {@link #fetchFromLocalDatastore()} on it.
     *
     * @param name    the name
     * @param objects the objects to be pinned
     * @return A {@link bolts.Task} that is resolved when pinning all completes.
     * @see #unpinAllInBackground(String, java.util.List)
     */
    public static <T extends ParseObject> Task<Void> pinAllInBackground(final String name,
                                                                        final List<T> objects) {
        return pinAllInBackground(name, objects, true);
    }

    private static <T extends ParseObject> Task<Void> pinAllInBackground(final String name,
                                                                         final List<T> objects, final boolean includeAllChildren) {
        if (!Parse.isLocalDatastoreEnabled()) {
            throw new IllegalStateException("Method requires Local Datastore. " +
                    "Please refer to `Parse#enableLocalDatastore(Context)`.");
        }

        Task<Void> task = Task.forResult(null);

        // Resolve and persist unresolved users attached via ACL, similarly how we do in saveAsync
        for (final ParseObject object : objects) {
            task = task.onSuccessTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    if (!object.isDataAvailable(KEY_ACL)) {
                        return Task.forResult(null);
                    }

                    final ParseACL acl = object.getACL(false);
                    if (acl == null) {
                        return Task.forResult(null);
                    }

                    ParseUser user = acl.getUnresolvedUser();
                    if (user == null || !user.isCurrentUser()) {
                        return Task.forResult(null);
                    }

                    return ParseUser.pinCurrentUserIfNeededAsync(user);
                }
            });
        }

        return task.onSuccessTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                return Parse.getLocalDatastore().pinAllObjectsAsync(
                        name != null ? name : DEFAULT_PIN,
                        objects,
                        includeAllChildren);
            }
        }).onSuccessTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                // Hack to emulate persisting current user on disk after a save like in ParseUser#saveAsync
                // Note: This does not persist current user if it's a child object of `objects`, it probably
                // should, but we can't unless we do something similar to #deepSaveAsync.
                if (ParseCorePlugins.PIN_CURRENT_USER.equals(name)) {
                    return task;
                }
                for (ParseObject object : objects) {
                    if (object instanceof ParseUser) {
                        final ParseUser user = (ParseUser) object;
                        if (user.isCurrentUser()) {
                            return ParseUser.pinCurrentUserIfNeededAsync(user);
                        }
                    }
                }
                return task;
            }
        });
    }

    /**
     * Stores the objects and every object they point to in the local datastore, recursively. If
     * those other objects have not been fetched from Parse, they will not be stored. However, if they
     * have changed data, all of the changes will be retained. To get the objects back later, you can
     * use {@link ParseQuery#fromLocalDatastore()}, or you can create an unfetched pointer with
     * {@link #createWithoutData(Class, String)} and then call {@link #fetchFromLocalDatastore()} on it.
     * {@link #fetchFromLocalDatastore()} on it.
     *
     * @param name    the name
     * @param objects the objects to be pinned
     * @throws ParseException exception if fails
     * @see #unpinAll(String, java.util.List)
     */
    public static <T extends ParseObject> void pinAll(String name,
                                                      List<T> objects) throws ParseException {
        ParseTaskUtils.wait(pinAllInBackground(name, objects));
    }

    /**
     * Stores the objects and every object they point to in the local datastore, recursively. If
     * those other objects have not been fetched from Parse, they will not be stored. However, if they
     * have changed data, all of the changes will be retained. To get the objects back later, you can
     * use {@link ParseQuery#fromLocalDatastore()}, or you can create an unfetched pointer with
     * {@link #createWithoutData(Class, String)} and then call {@link #fetchFromLocalDatastore()} on it.
     *
     * @param objects  the objects to be pinned
     * @param callback the callback
     * @see #unpinAllInBackground(java.util.List, DeleteCallback)
     * @see #DEFAULT_PIN
     */
    public static <T extends ParseObject> void pinAllInBackground(List<T> objects,
                                                                  SaveCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(pinAllInBackground(DEFAULT_PIN, objects), callback);
    }

    /**
     * Stores the objects and every object they point to in the local datastore, recursively. If
     * those other objects have not been fetched from Parse, they will not be stored. However, if they
     * have changed data, all of the changes will be retained. To get the objects back later, you can
     * use {@link ParseQuery#fromLocalDatastore()}, or you can create an unfetched pointer with
     * {@link #createWithoutData(Class, String)} and then call {@link #fetchFromLocalDatastore()} on it.
     *
     * @param objects the objects to be pinned
     * @return A {@link bolts.Task} that is resolved when pinning all completes.
     * @see #unpinAllInBackground(java.util.List)
     * @see #DEFAULT_PIN
     */
    public static <T extends ParseObject> Task<Void> pinAllInBackground(List<T> objects) {
        return pinAllInBackground(DEFAULT_PIN, objects);
    }

    /**
     * Stores the objects and every object they point to in the local datastore, recursively. If
     * those other objects have not been fetched from Parse, they will not be stored. However, if they
     * have changed data, all of the changes will be retained. To get the objects back later, you can
     * use {@link ParseQuery#fromLocalDatastore()}, or you can create an unfetched pointer with
     * {@link #createWithoutData(Class, String)} and then call {@link #fetchFromLocalDatastore()} on it.
     *
     * @param objects the objects to be pinned
     * @throws ParseException exception if fails
     * @see #unpinAll(java.util.List)
     * @see #DEFAULT_PIN
     */
    public static <T extends ParseObject> void pinAll(List<T> objects) throws ParseException {
        ParseTaskUtils.wait(pinAllInBackground(DEFAULT_PIN, objects));
    }

    /**
     * Removes the objects and every object they point to in the local datastore, recursively.
     *
     * @param name     the name
     * @param objects  the objects
     * @param callback the callback
     * @see #pinAllInBackground(String, java.util.List, SaveCallback)
     */
    public static <T extends ParseObject> void unpinAllInBackground(String name, List<T> objects,
                                                                    DeleteCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(unpinAllInBackground(name, objects), callback);
    }

    /**
     * Removes the objects and every object they point to in the local datastore, recursively.
     *
     * @param name    the name
     * @param objects the objects
     * @return A {@link bolts.Task} that is resolved when unpinning all completes.
     * @see #pinAllInBackground(String, java.util.List)
     */
    public static <T extends ParseObject> Task<Void> unpinAllInBackground(String name,
                                                                          List<T> objects) {
        if (!Parse.isLocalDatastoreEnabled()) {
            throw new IllegalStateException("Method requires Local Datastore. " +
                    "Please refer to `Parse#enableLocalDatastore(Context)`.");
        }
        if (name == null) {
            name = DEFAULT_PIN;
        }
        return Parse.getLocalDatastore().unpinAllObjectsAsync(name, objects);
    }

    /**
     * Removes the objects and every object they point to in the local datastore, recursively.
     *
     * @param name    the name
     * @param objects the objects
     * @throws ParseException exception if fails
     * @see #pinAll(String, java.util.List)
     */
    public static <T extends ParseObject> void unpinAll(String name,
                                                        List<T> objects) throws ParseException {
        ParseTaskUtils.wait(unpinAllInBackground(name, objects));
    }

    /**
     * Removes the objects and every object they point to in the local datastore, recursively.
     *
     * @param objects  the objects
     * @param callback the callback
     * @see #pinAllInBackground(java.util.List, SaveCallback)
     * @see #DEFAULT_PIN
     */
    public static <T extends ParseObject> void unpinAllInBackground(List<T> objects,
                                                                    DeleteCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(unpinAllInBackground(DEFAULT_PIN, objects), callback);
    }

    /**
     * Removes the objects and every object they point to in the local datastore, recursively.
     *
     * @param objects the objects
     * @return A {@link bolts.Task} that is resolved when unpinning all completes.
     * @see #pinAllInBackground(java.util.List)
     * @see #DEFAULT_PIN
     */
    public static <T extends ParseObject> Task<Void> unpinAllInBackground(List<T> objects) {
        return unpinAllInBackground(DEFAULT_PIN, objects);
    }

    /**
     * Removes the objects and every object they point to in the local datastore, recursively.
     *
     * @param objects the objects
     * @throws ParseException exception if fails
     * @see #pinAll(java.util.List)
     * @see #DEFAULT_PIN
     */
    public static <T extends ParseObject> void unpinAll(List<T> objects) throws ParseException {
        ParseTaskUtils.wait(unpinAllInBackground(DEFAULT_PIN, objects));
    }

    /**
     * Removes the objects and every object they point to in the local datastore, recursively.
     *
     * @param name     the name
     * @param callback the callback
     * @see #pinAll(String, java.util.List)
     */
    public static void unpinAllInBackground(String name, DeleteCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(unpinAllInBackground(name), callback);
    }

    /**
     * Removes the objects and every object they point to in the local datastore, recursively.
     *
     * @param name the name
     * @return A {@link bolts.Task} that is resolved when unpinning all completes.
     * @see #pinAll(String, java.util.List)
     */
    public static Task<Void> unpinAllInBackground(String name) {
        if (!Parse.isLocalDatastoreEnabled()) {
            throw new IllegalStateException("Method requires Local Datastore. " +
                    "Please refer to `Parse#enableLocalDatastore(Context)`.");
        }
        if (name == null) {
            name = DEFAULT_PIN;
        }
        return Parse.getLocalDatastore().unpinAllObjectsAsync(name);
    }

    /**
     * Removes the objects and every object they point to in the local datastore, recursively.
     *
     * @param name the name
     * @throws ParseException exception if fails
     * @see #pinAll(String, java.util.List)
     */
    public static void unpinAll(String name) throws ParseException {
        ParseTaskUtils.wait(unpinAllInBackground(name));
    }

    /**
     * Removes the objects and every object they point to in the local datastore, recursively.
     *
     * @param callback the callback
     * @see #pinAllInBackground(java.util.List, SaveCallback)
     * @see #DEFAULT_PIN
     */
    public static void unpinAllInBackground(DeleteCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(unpinAllInBackground(), callback);
    }

    /**
     * Removes the objects and every object they point to in the local datastore, recursively.
     *
     * @return A {@link bolts.Task} that is resolved when unpinning all completes.
     * @see #pinAllInBackground(java.util.List, SaveCallback)
     * @see #DEFAULT_PIN
     */
    public static Task<Void> unpinAllInBackground() {
        return unpinAllInBackground(DEFAULT_PIN);
    }

    /**
     * Removes the objects and every object they point to in the local datastore, recursively.
     *
     * @throws ParseException exception if fails
     * @see #pinAll(java.util.List)
     * @see #DEFAULT_PIN
     */
    public static void unpinAll() throws ParseException {
        ParseTaskUtils.wait(unpinAllInBackground());
    }


    static ParseObject createFromParcel(Parcel source, ParseParcelDecoder decoder) {
        String className = source.readString();
        String objectId = source.readByte() == 1 ? source.readString() : null;
        // Create empty object (might be the same instance if LDS is enabled)
        // and pass to decoder before unparceling child objects in State
        ParseObject object = createWithoutData(className, objectId);
        if (decoder instanceof ParseObjectParcelDecoder) {
            ((ParseObjectParcelDecoder) decoder).addKnownObject(object);
        }
        State state = State.createFromParcel(source, decoder);
        object.setState(state);
        if (source.readByte() == 1) object.localId = source.readString();
        if (source.readByte() == 1) object.isDeleted = true;
        // If object.ldsEnabledWhenParceling is true, we got this from OfflineStore.
        // There is no need to restore operations in that case.
        boolean restoreOperations = !object.ldsEnabledWhenParceling;
        ParseOperationSet set = ParseOperationSet.fromParcel(source, decoder);
        if (restoreOperations) {
            for (String key : set.keySet()) {
                ParseFieldOperation op = set.get(key);
                object.performOperation(key, op); // Update ops and estimatedData
            }
        }
        Bundle bundle = source.readBundle(ParseObject.class.getClassLoader());
        object.onRestoreInstanceState(bundle);
        return object;
    }

    State.Init<?> newStateBuilder(String className) {
        return new State.Builder(className);
    }

    State getState() {
        synchronized (mutex) {
            return state;
        }
    }

    /**
     * Updates the current state of this object as well as updates our in memory cached state.
     *
     * @param newState The new state.
     */
    void setState(State newState) {
        synchronized (mutex) {
            setState(newState, true);
        }
    }

    private void setState(State newState, boolean notifyIfObjectIdChanges) {
        synchronized (mutex) {
            String oldObjectId = state.objectId();
            String newObjectId = newState.objectId();

            state = newState;

            if (notifyIfObjectIdChanges && !ParseTextUtils.equals(oldObjectId, newObjectId)) {
                notifyObjectIdChanged(oldObjectId, newObjectId);
            }

            rebuildEstimatedData();
        }
    }

    /**
     * Accessor to the class name.
     */
    public String getClassName() {
        synchronized (mutex) {
            return state.className();
        }
    }

    /**
     * This reports time as the server sees it, so that if you make changes to a {@code ParseObject}, then
     * wait a while, and then call {@link #save()}, the updated time will be the time of the
     * {@link #save()} call rather than the time the object was changed locally.
     *
     * @return The last time this object was updated on the server.
     */
    public Date getUpdatedAt() {
        long updatedAt = getState().updatedAt();
        return updatedAt > 0
                ? new Date(updatedAt)
                : null;
    }

    /**
     * This reports time as the server sees it, so that if you create a {@code ParseObject}, then wait a
     * while, and then call {@link #save()}, the creation time will be the time of the first
     * {@link #save()} call rather than the time the object was created locally.
     *
     * @return The first time this object was saved on the server.
     */
    public Date getCreatedAt() {
        long createdAt = getState().createdAt();
        return createdAt > 0
                ? new Date(createdAt)
                : null;
    }

    /**
     * Returns a set view of the keys contained in this object. This does not include createdAt,
     * updatedAt, authData, or objectId. It does include things like username and ACL.
     */
    public Set<String> keySet() {
        synchronized (mutex) {
            return Collections.unmodifiableSet(estimatedData.keySet());
        }
    }

    /**
     * Copies all of the operations that have been performed on another object since its last save
     * onto this one.
     */
    void copyChangesFrom(ParseObject other) {
        synchronized (mutex) {
            ParseOperationSet operations = other.operationSetQueue.getFirst();
            for (String key : operations.keySet()) {
                performOperation(key, operations.get(key));
            }
        }
    }

    void mergeFromObject(ParseObject other) {
        synchronized (mutex) {
            // If they point to the same instance, we don't need to merge.
            if (this == other) {
                return;
            }

            State copy = other.getState().newBuilder().build();

            // We don't want to notify if an objectId changed here since we utilize this method to merge
            // an anonymous current user with a new ParseUser instance that's calling signUp(). This
            // doesn't make any sense and we should probably remove that code in ParseUser.
            // Otherwise, there shouldn't be any objectId changes here since this method is only otherwise
            // used in fetchAll.
            setState(copy, false);
        }
    }

    /**
     * Clears changes to this object's {@code key} made since the last call to {@link #save()} or
     * {@link #saveInBackground()}.
     *
     * @param key The {@code key} to revert changes for.
     */
    public void revert(String key) {
        synchronized (mutex) {
            if (isDirty(key)) {
                currentOperations().remove(key);
                rebuildEstimatedData();
            }
        }
    }

    /**
     * Clears any changes to this object made since the last call to {@link #save()} or
     * {@link #saveInBackground()}.
     */
    public void revert() {
        synchronized (mutex) {
            if (isDirty()) {
                currentOperations().clear();
                rebuildEstimatedData();
            }
        }
    }

    /**
     * Deep traversal on this object to grab a copy of any object referenced by this object. These
     * instances may have already been fetched, and we don't want to lose their data when refreshing
     * or saving.
     *
     * @return the map mapping from objectId to {@code ParseObject} which has been fetched.
     */
    private Map<String, ParseObject> collectFetchedObjects() {
        final Map<String, ParseObject> fetchedObjects = new HashMap<>();
        ParseTraverser traverser = new ParseTraverser() {
            @Override
            protected boolean visit(Object object) {
                if (object instanceof ParseObject) {
                    ParseObject parseObj = (ParseObject) object;
                    State state = parseObj.getState();
                    if (state.objectId() != null && state.isComplete()) {
                        fetchedObjects.put(state.objectId(), parseObj);
                    }
                }
                return true;
            }
        };
        traverser.traverse(estimatedData);
        return fetchedObjects;
    }

    /**
     * Helper method called by {@link #fromJSONPayload(JSONObject, ParseDecoder)}
     * <p>
     * The method helps webhooks implementation to build Parse object from raw JSON payload.
     * It is different from {@link #mergeFromServer(State, JSONObject, ParseDecoder, boolean)}
     * as the method saves the key value pairs (other than className, objectId, updatedAt and
     * createdAt) in the operation queue rather than the server data. It also handles
     * {@link ParseFieldOperations} differently.
     *
     * @param json    : JSON object to be converted to Parse object
     * @param decoder : Decoder to be used for Decoding JSON
     */
    void build(JSONObject json, ParseDecoder decoder) {
        try {
            State.Builder builder = new State.Builder(state)
                    .isComplete(true);

            builder.clear();

            Iterator<?> keys = json.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
        /*
        __className:  Used by fromJSONPayload, should be stripped out by the time it gets here...
         */
                if (key.equals(KEY_CLASS_NAME)) {
                    continue;
                }
                if (key.equals(KEY_OBJECT_ID)) {
                    String newObjectId = json.getString(key);
                    builder.objectId(newObjectId);
                    continue;
                }
                if (key.equals(KEY_CREATED_AT)) {
                    builder.createdAt(ParseDateFormat.getInstance().parse(json.getString(key)));
                    continue;
                }
                if (key.equals(KEY_UPDATED_AT)) {
                    builder.updatedAt(ParseDateFormat.getInstance().parse(json.getString(key)));
                    continue;
                }

                Object value = json.get(key);
                Object decodedObject = decoder.decode(value);
                if (decodedObject instanceof ParseFieldOperation) {
                    performOperation(key, (ParseFieldOperation) decodedObject);
                } else {
                    put(key, decodedObject);
                }
            }

            setState(builder.build());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Merges from JSON in REST format.
     * Updates this object with data from the server.
     *
     * @see #toJSONObjectForSaving(State, ParseOperationSet, ParseEncoder)
     */
    State mergeFromServer(
            State state, JSONObject json, ParseDecoder decoder, boolean completeData) {
        try {
            // If server data is complete, consider this object to be fetched.
            State.Init<?> builder = state.newBuilder();
            if (completeData) {
                builder.clear();
            }
            builder.isComplete(state.isComplete() || completeData);

            Iterator<?> keys = json.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
        /*
        __type:       Returned by queries and cloud functions to designate body is a ParseObject
        __className:  Used by fromJSON, should be stripped out by the time it gets here...
         */
                if (key.equals("__type") || key.equals(KEY_CLASS_NAME)) {
                    continue;
                }
                if (key.equals(KEY_OBJECT_ID)) {
                    String newObjectId = json.getString(key);
                    builder.objectId(newObjectId);
                    continue;
                }
                if (key.equals(KEY_CREATED_AT)) {
                    builder.createdAt(ParseDateFormat.getInstance().parse(json.getString(key)));
                    continue;
                }
                if (key.equals(KEY_UPDATED_AT)) {
                    builder.updatedAt(ParseDateFormat.getInstance().parse(json.getString(key)));
                    continue;
                }
                if (key.equals(KEY_ACL)) {
                    ParseACL acl = ParseACL.createACLFromJSONObject(json.getJSONObject(key), decoder);
                    builder.put(KEY_ACL, acl);
                    continue;
                }
                if (key.equals(KEY_SELECTED_KEYS)) {
                    JSONArray safeKeys = json.getJSONArray(key);
                    if (safeKeys.length() > 0) {
                        Collection<String> set = new HashSet<>();
                        for (int i = 0; i < safeKeys.length(); i++) {
                            // Don't add nested keys.
                            String safeKey = safeKeys.getString(i);
                            if (safeKey.contains(".")) safeKey = safeKey.split("\\.")[0];
                            set.add(safeKey);
                        }
                        builder.availableKeys(set);
                    }
                    continue;
                }

                Object value = json.get(key);
                if (value instanceof JSONObject && json.has(KEY_SELECTED_KEYS)) {
                    // This might be a ParseObject. Pass selected keys to understand if it is complete.
                    JSONArray selectedKeys = json.getJSONArray(KEY_SELECTED_KEYS);
                    JSONArray nestedKeys = new JSONArray();
                    for (int i = 0; i < selectedKeys.length(); i++) {
                        String nestedKey = selectedKeys.getString(i);
                        if (nestedKey.startsWith(key + "."))
                            nestedKeys.put(nestedKey.substring(key.length() + 1));
                    }
                    if (nestedKeys.length() > 0) {
                        ((JSONObject) value).put(KEY_SELECTED_KEYS, nestedKeys);
                    }
                }
                Object decodedObject = decoder.decode(value);
                builder.put(key, decodedObject);
            }

            return builder.build();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convert to REST JSON for persisting in LDS.
     *
     * @see #mergeREST(State, org.json.JSONObject, ParseDecoder)
     */
    JSONObject toRest(ParseEncoder encoder) {
        State state;
        List<ParseOperationSet> operationSetQueueCopy;
        synchronized (mutex) {
            // mutex needed to lock access to state and operationSetQueue and operationSetQueue & children
            // are mutable
            state = getState();

            // operationSetQueue is a List of Lists, so we'll need custom copying logic
            int operationSetQueueSize = operationSetQueue.size();
            operationSetQueueCopy = new ArrayList<>(operationSetQueueSize);
            for (int i = 0; i < operationSetQueueSize; i++) {
                ParseOperationSet original = operationSetQueue.get(i);
                ParseOperationSet copy = new ParseOperationSet(original);
                operationSetQueueCopy.add(copy);
            }
        }
        return toRest(state, operationSetQueueCopy, encoder);
    }

    JSONObject toRest(
            State state, List<ParseOperationSet> operationSetQueue, ParseEncoder objectEncoder) {
        // Public data goes in dataJSON; special fields go in objectJSON.
        JSONObject json = new JSONObject();

        try {
            // REST JSON (State)
            json.put(KEY_CLASS_NAME, state.className());
            if (state.objectId() != null) {
                json.put(KEY_OBJECT_ID, state.objectId());
            }
            if (state.createdAt() > 0) {
                json.put(KEY_CREATED_AT,
                        ParseDateFormat.getInstance().format(new Date(state.createdAt())));
            }
            if (state.updatedAt() > 0) {
                json.put(KEY_UPDATED_AT,
                        ParseDateFormat.getInstance().format(new Date(state.updatedAt())));
            }
            for (String key : state.keySet()) {
                Object value = state.get(key);
                json.put(key, objectEncoder.encode(value));
            }

            // Internal JSON
            //TODO(klimt): We'll need to rip all this stuff out and put it somewhere else if we start
            // using the REST api and want to send data to Parse.
            json.put(KEY_COMPLETE, state.isComplete());
            json.put(KEY_IS_DELETING_EVENTUALLY, isDeletingEventually);
            JSONArray availableKeys = new JSONArray(state.availableKeys());
            json.put(KEY_SELECTED_KEYS, availableKeys);

            // Operation Set Queue
            JSONArray operations = new JSONArray();
            for (ParseOperationSet operationSet : operationSetQueue) {
                operations.put(operationSet.toRest(objectEncoder));
            }
            json.put(KEY_OPERATIONS, operations);

        } catch (JSONException e) {
            throw new RuntimeException("could not serialize object to JSON");
        }

        return json;
    }

    /**
     * Merge with REST JSON from LDS.
     *
     * @see #toRest(ParseEncoder)
     */
    void mergeREST(State state, JSONObject json, ParseDecoder decoder) {
        ArrayList<ParseOperationSet> saveEventuallyOperationSets = new ArrayList<>();

        synchronized (mutex) {
            try {
                boolean isComplete = json.getBoolean(KEY_COMPLETE);
                isDeletingEventually = ParseJSONUtils.getInt(json, Arrays.asList(
                        KEY_IS_DELETING_EVENTUALLY,
                        KEY_IS_DELETING_EVENTUALLY_OLD
                ));
                JSONArray operations = json.getJSONArray(KEY_OPERATIONS);
                {
                    ParseOperationSet newerOperations = currentOperations();
                    operationSetQueue.clear();

                    // Add and enqueue any saveEventually operations, roll forward any other operation sets
                    // (operation sets here are generally failed/incomplete saves).
                    ParseOperationSet current = null;
                    for (int i = 0; i < operations.length(); i++) {
                        JSONObject operationSetJSON = operations.getJSONObject(i);
                        ParseOperationSet operationSet = ParseOperationSet.fromRest(operationSetJSON, decoder);

                        if (operationSet.isSaveEventually()) {
                            if (current != null) {
                                operationSetQueue.add(current);
                                current = null;
                            }
                            saveEventuallyOperationSets.add(operationSet);
                            operationSetQueue.add(operationSet);
                            continue;
                        }

                        if (current != null) {
                            operationSet.mergeFrom(current);
                        }
                        current = operationSet;
                    }
                    if (current != null) {
                        operationSetQueue.add(current);
                    }

                    // Merge the changes that were previously in memory into the updated object.
                    currentOperations().mergeFrom(newerOperations);
                }

                // We only want to merge server data if we our updatedAt is null (we're unsaved or from
                // #createWithoutData) or if the JSON's updatedAt is newer than ours.
                boolean mergeServerData = false;
                if (state.updatedAt() < 0) {
                    mergeServerData = true;
                } else if (json.has(KEY_UPDATED_AT)) {
                    Date otherUpdatedAt = ParseDateFormat.getInstance().parse(json.getString(KEY_UPDATED_AT));
                    if (new Date(state.updatedAt()).compareTo(otherUpdatedAt) < 0) {
                        mergeServerData = true;
                    }
                }

                if (mergeServerData) {
                    // Clean up internal json keys
                    JSONObject mergeJSON = ParseJSONUtils.create(json, Arrays.asList(
                            KEY_COMPLETE, KEY_IS_DELETING_EVENTUALLY, KEY_IS_DELETING_EVENTUALLY_OLD,
                            KEY_OPERATIONS
                    ));
                    State newState = mergeFromServer(state, mergeJSON, decoder, isComplete);
                    setState(newState);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        // We cannot modify the taskQueue inside synchronized (mutex).
        for (ParseOperationSet operationSet : saveEventuallyOperationSets) {
            enqueueSaveEventuallyOperationAsync(operationSet);
        }
    }

    private boolean hasDirtyChildren() {
        synchronized (mutex) {
            // We only need to consider the currently estimated children here,
            // because they're the only ones that might need to be saved in a
            // subsequent call to save, which is the meaning of "dirtiness".
            List<ParseObject> unsavedChildren = new ArrayList<>();
            collectDirtyChildren(estimatedData, unsavedChildren, null);
            return unsavedChildren.size() > 0;
        }
    }

    /**
     * Whether any key-value pair in this object (or its children) has been added/updated/removed and
     * not saved yet.
     *
     * @return Whether this object has been altered and not saved yet.
     */
    public boolean isDirty() {
        return this.isDirty(true);
    }

    boolean isDirty(boolean considerChildren) {
        synchronized (mutex) {
            return (isDeleted || getObjectId() == null || hasChanges() || (considerChildren && hasDirtyChildren()));
        }
    }

    boolean hasChanges() {
        synchronized (mutex) {
            return currentOperations().size() > 0;
        }
    }

    /**
     * Returns {@code true} if this {@code ParseObject} has operations in operationSetQueue that
     * haven't been completed yet, {@code false} if there are no operations in the operationSetQueue.
     */
    boolean hasOutstandingOperations() {
        synchronized (mutex) {
            // > 1 since 1 is for unsaved changes.
            return operationSetQueue.size() > 1;
        }
    }

    /**
     * Whether a value associated with a key has been added/updated/removed and not saved yet.
     *
     * @param key The key to check for
     * @return Whether this key has been altered and not saved yet.
     */
    public boolean isDirty(String key) {
        synchronized (mutex) {
            return currentOperations().containsKey(key);
        }
    }

    /**
     * Accessor to the object id. An object id is assigned as soon as an object is saved to the
     * server. The combination of a className and an objectId uniquely identifies an object in your
     * application.
     *
     * @return The object id.
     */
    public String getObjectId() {
        synchronized (mutex) {
            return state.objectId();
        }
    }

    /**
     * Setter for the object id. In general you do not need to use this. However, in some cases this
     * can be convenient. For example, if you are serializing a {@code ParseObject} yourself and wish
     * to recreate it, you can use this to recreate the {@code ParseObject} exactly.
     */
    public void setObjectId(String newObjectId) {
        synchronized (mutex) {
            String oldObjectId = state.objectId();
            if (ParseTextUtils.equals(oldObjectId, newObjectId)) {
                return;
            }

            // We don't need to use setState since it doesn't affect our cached state.
            state = state.newBuilder().objectId(newObjectId).build();
            notifyObjectIdChanged(oldObjectId, newObjectId);
        }
    }

    /**
     * Returns the localId, which is used internally for serializing relations to objects that don't
     * yet have an objectId.
     */
    String getOrCreateLocalId() {
        synchronized (mutex) {
            if (localId == null) {
                if (state.objectId() != null) {
                    throw new IllegalStateException(
                            "Attempted to get a localId for an object with an objectId.");
                }
                localId = getLocalIdManager().createLocalId();
            }
            return localId;
        }
    }

    // Sets the objectId without marking dirty.
    private void notifyObjectIdChanged(String oldObjectId, String newObjectId) {
        synchronized (mutex) {
            // The offline store may throw if this object already had a different objectId.
            OfflineStore store = Parse.getLocalDatastore();
            if (store != null) {
                store.updateObjectId(this, oldObjectId, newObjectId);
            }

            if (localId != null) {
                getLocalIdManager().setObjectId(localId, newObjectId);
                localId = null;
            }
        }
    }

    private ParseRESTObjectCommand currentSaveEventuallyCommand(
            ParseOperationSet operations, ParseEncoder objectEncoder, String sessionToken) {
        State state = getState();

        /*
         * Get the JSON representation of the object, and use some of the information to construct the
         * command.
         */
        JSONObject objectJSON = toJSONObjectForSaving(state, operations, objectEncoder);

        return ParseRESTObjectCommand.saveObjectCommand(
                state,
                objectJSON,
                sessionToken);
    }

    /**
     * Converts a {@code ParseObject} to a JSON representation for saving to Parse.
     * <p>
     * <pre>
     * {
     *   data: { // objectId plus any ParseFieldOperations },
     *   classname: class name for the object
     * }
     * </pre>
     * <p>
     * updatedAt and createdAt are not included. only dirty keys are represented in the data.
     *
     * @see #mergeFromServer(State state, org.json.JSONObject, ParseDecoder, boolean)
     */
    // Currently only used by saveEventually
    <T extends State> JSONObject toJSONObjectForSaving(
            T state, ParseOperationSet operations, ParseEncoder objectEncoder) {
        JSONObject objectJSON = new JSONObject();

        try {
            // Serialize the data
            for (String key : operations.keySet()) {
                ParseFieldOperation operation = operations.get(key);
                objectJSON.put(key, objectEncoder.encode(operation));

                // TODO(grantland): Use cached value from hashedObjects if it's a set operation.
            }

            if (state.objectId() != null) {
                objectJSON.put(KEY_OBJECT_ID, state.objectId());
            }
        } catch (JSONException e) {
            throw new RuntimeException("could not serialize object to JSON");
        }

        return objectJSON;
    }

    /**
     * Handles the result of {@code save}.
     * <p>
     * Should be called on success or failure.
     */
    // TODO(grantland): Remove once we convert saveEventually and ParseUser.signUp/resolveLaziness
    // to controllers
    Task<Void> handleSaveResultAsync(
            final JSONObject result, final ParseOperationSet operationsBeforeSave) {
        ParseObject.State newState = null;

        if (result != null) { // Success
            synchronized (mutex) {
                final Map<String, ParseObject> fetchedObjects = collectFetchedObjects();
                ParseDecoder decoder = new KnownParseObjectDecoder(fetchedObjects);
                newState = ParseObjectCoder.get().decode(getState().newBuilder().clear(), result, decoder)
                        .isComplete(false)
                        .build();
            }
        }

        return handleSaveResultAsync(newState, operationsBeforeSave);
    }

    /**
     * Handles the result of {@code save}.
     * <p>
     * Should be called on success or failure.
     */
    Task<Void> handleSaveResultAsync(
            final ParseObject.State result, final ParseOperationSet operationsBeforeSave) {
        Task<Void> task = Task.forResult(null);

        /*
         * If this object is in the offline store, then we need to make sure that we pull in any dirty
         * changes it may have before merging the server data into it.
         */
        final OfflineStore store = Parse.getLocalDatastore();
        if (store != null) {
            task = task.onSuccessTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    return store.fetchLocallyAsync(ParseObject.this).makeVoid();
                }
            });
        }

        final boolean success = result != null;
        synchronized (mutex) {
            // Find operationsBeforeSave in the queue so that we can remove it and move to the next
            // operation set.
            ListIterator<ParseOperationSet> opIterator =
                    operationSetQueue.listIterator(operationSetQueue.indexOf(operationsBeforeSave));
            opIterator.next();
            opIterator.remove();

            if (!success) {
                // Merge the data from the failed save into the next save.
                ParseOperationSet nextOperation = opIterator.next();
                nextOperation.mergeFrom(operationsBeforeSave);
                if (store != null) {
                    task = task.continueWithTask(new Continuation<Void, Task<Void>>() {
                        @Override
                        public Task<Void> then(Task<Void> task) {
                            if (task.isFaulted()) {
                                return Task.forResult(null);
                            } else {
                                return store.updateDataForObjectAsync(ParseObject.this);
                            }
                        }
                    });
                }
                return task;
            }
        }

        // fetchLocallyAsync will return an error if this object isn't in the LDS yet and that's ok
        task = task.continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) {
                synchronized (mutex) {
                    State newState;
                    if (result.isComplete()) {
                        // Result is complete, so just replace
                        newState = result;
                    } else {
                        // Result is incomplete, so we'll need to apply it to the current state
                        newState = getState().newBuilder()
                                .apply(operationsBeforeSave)
                                .apply(result)
                                .build();
                    }
                    setState(newState);
                }
                return null;
            }
        });

        if (store != null) {
            task = task.onSuccessTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    return store.updateDataForObjectAsync(ParseObject.this);
                }
            });
        }

        task = task.onSuccess(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) {
                saveEvent.invoke(ParseObject.this, null);
                return null;
            }
        });

        return task;
    }

    ParseOperationSet startSave() {
        synchronized (mutex) {
            ParseOperationSet currentOperations = currentOperations();
            operationSetQueue.addLast(new ParseOperationSet());
            return currentOperations;
        }
    }

    void validateSave() {
        // do nothing
    }

    /**
     * Saves this object to the server. Typically, you should use {@link #saveInBackground} instead of
     * this, unless you are managing your own threading.
     *
     * @throws ParseException Throws an exception if the server is inaccessible.
     */
    public final void save() throws ParseException {
        ParseTaskUtils.wait(saveInBackground());
    }

    /**
     * Saves this object to the server in a background thread. This is preferable to using {@link #save()},
     * unless your code is already running from a background thread.
     *
     * @return A {@link bolts.Task} that is resolved when the save completes.
     */
    public final Task<Void> saveInBackground() {
        return ParseUser.getCurrentUserAsync().onSuccessTask(new Continuation<ParseUser, Task<String>>() {
            @Override
            public Task<String> then(Task<ParseUser> task) {
                final ParseUser current = task.getResult();
                if (current == null) {
                    return Task.forResult(null);
                }
                if (!current.isLazy()) {
                    return Task.forResult(current.getSessionToken());
                }

                // The current user is lazy/unresolved. If it is attached to us via ACL, we'll need to
                // resolve/save it before proceeding.
                if (!isDataAvailable(KEY_ACL)) {
                    return Task.forResult(null);
                }
                final ParseACL acl = getACL(false);
                if (acl == null) {
                    return Task.forResult(null);
                }
                final ParseUser user = acl.getUnresolvedUser();
                if (user == null || !user.isCurrentUser()) {
                    return Task.forResult(null);
                }
                return user.saveAsync(null).onSuccess(new Continuation<Void, String>() {
                    @Override
                    public String then(Task<Void> task) {
                        if (acl.hasUnresolvedUser()) {
                            throw new IllegalStateException("ACL has an unresolved ParseUser. "
                                    + "Save or sign up before attempting to serialize the ACL.");
                        }
                        return user.getSessionToken();
                    }
                });
            }
        }).onSuccessTask(new Continuation<String, Task<Void>>() {
            @Override
            public Task<Void> then(Task<String> task) {
                final String sessionToken = task.getResult();
                return saveAsync(sessionToken);
            }
        });
    }

    Task<Void> saveAsync(final String sessionToken) {
        return taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> toAwait) {
                return saveAsync(sessionToken, toAwait);
            }
        });
    }

    Task<Void> saveAsync(final String sessionToken, final Task<Void> toAwait) {
        if (!isDirty()) {
            return Task.forResult(null);
        }

        final ParseOperationSet operations;
        synchronized (mutex) {
            updateBeforeSave();
            validateSave();
            operations = startSave();
        }

        Task<Void> task;
        synchronized (mutex) {
            // Recursively save children

            /*
             * TODO(klimt): Why is this estimatedData and not... I mean, what if a child is
             * removed after save is called, but before the unresolved user gets resolved? It
             * won't get saved.
             */
            task = deepSaveAsync(estimatedData, sessionToken);
        }

        return task.onSuccessTask(
                TaskQueue.<Void>waitFor(toAwait)
        ).onSuccessTask(new Continuation<Void, Task<ParseObject.State>>() {
            @Override
            public Task<ParseObject.State> then(Task<Void> task) {
                final Map<String, ParseObject> fetchedObjects = collectFetchedObjects();
                ParseDecoder decoder = new KnownParseObjectDecoder(fetchedObjects);
                return getObjectController().saveAsync(getState(), operations, sessionToken, decoder);
            }
        }).continueWithTask(new Continuation<ParseObject.State, Task<Void>>() {
            @Override
            public Task<Void> then(final Task<ParseObject.State> saveTask) {
                ParseObject.State result = saveTask.getResult();
                return handleSaveResultAsync(result, operations).continueWithTask(new Continuation<Void, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<Void> task) {
                        if (task.isFaulted() || task.isCancelled()) {
                            return task;
                        }

                        // We still want to propagate saveTask errors
                        return saveTask.makeVoid();
                    }
                });
            }
        });
    }

    // Currently only used by ParsePinningEventuallyQueue for saveEventually due to the limitation in
    // ParseCommandCache that it can only return JSONObject result.
    Task<JSONObject> saveAsync(
            ParseHttpClient client,
            final ParseOperationSet operationSet,
            String sessionToken) {
        final ParseRESTCommand command =
                currentSaveEventuallyCommand(operationSet, PointerEncoder.get(), sessionToken);
        return command.executeAsync(client);
    }

    /**
     * Saves this object to the server in a background thread. This is preferable to using {@link #save()},
     * unless your code is already running from a background thread.
     *
     * @param callback {@code callback.done(e)} is called when the save completes.
     */
    public final void saveInBackground(SaveCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(saveInBackground(), callback);
    }

    void validateSaveEventually() throws ParseException {
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
     * subsequent calls to {@code #saveEventually()} or {@link #deleteEventually()}  will cause old
     * saves to be silently  discarded until the connection can be re-established, and the queued
     * objects can be saved.
     *
     * @param callback - A callback which will be called if the save completes before the app exits.
     */
    public final void saveEventually(SaveCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(saveEventually(), callback);
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
     * subsequent calls to {@code #saveEventually()} or {@link #deleteEventually()}  will cause old
     * saves to be silently  discarded until the connection can be re-established, and the queued
     * objects can be saved.
     *
     * @return A {@link bolts.Task} that is resolved when the save completes.
     */
    public final Task<Void> saveEventually() {
        if (!isDirty()) {
            Parse.getEventuallyQueue().fakeObjectUpdate();
            return Task.forResult(null);
        }

        final ParseOperationSet operationSet;
        final ParseRESTCommand command;
        final Task<JSONObject> runEventuallyTask;

        synchronized (mutex) {
            updateBeforeSave();
            try {
                validateSaveEventually();
            } catch (ParseException e) {
                return Task.forError(e);
            }

            // TODO(klimt): Once we allow multiple saves on an object, this
            // should be collecting dirty children from the estimate based on
            // whatever data is going to be sent by this saveEventually, which
            // won't necessarily be the current estimatedData. We should resolve
            // this when the multiple save code is added.
            List<ParseObject> unsavedChildren = new ArrayList<>();
            collectDirtyChildren(estimatedData, unsavedChildren, null);

            String localId = null;
            if (getObjectId() == null) {
                localId = getOrCreateLocalId();
            }

            operationSet = startSave();
            operationSet.setIsSaveEventually(true);

            //TODO (grantland): Convert to async
            final String sessionToken = ParseUser.getCurrentSessionToken();

            // See [1]
            command = currentSaveEventuallyCommand(operationSet, PointerOrLocalIdEncoder.get(),
                    sessionToken);

            // TODO: Make this logic make sense once we have deepSaveEventually
            command.setLocalId(localId);

            // Mark the command with a UUID so that we can match it up later.
            command.setOperationSetUUID(operationSet.getUUID());

            // Ensure local ids are retained before saveEventually-ing children
            command.retainLocalIds();

            for (ParseObject object : unsavedChildren) {
                object.saveEventually();
            }

        }

        // We cannot modify the taskQueue inside synchronized (mutex).
        ParseEventuallyQueue cache = Parse.getEventuallyQueue();
        runEventuallyTask = cache.enqueueEventuallyAsync(command, ParseObject.this);
        enqueueSaveEventuallyOperationAsync(operationSet);

        // Release the extra retained local ids.
        command.releaseLocalIds();

        Task<Void> handleSaveResultTask;
        if (Parse.isLocalDatastoreEnabled()) {
            // ParsePinningEventuallyQueue calls handleSaveEventuallyResultAsync directly.
            handleSaveResultTask = runEventuallyTask.makeVoid();
        } else {
            handleSaveResultTask = runEventuallyTask.onSuccessTask(new Continuation<JSONObject, Task<Void>>() {
                @Override
                public Task<Void> then(Task<JSONObject> task) {
                    JSONObject json = task.getResult();
                    return handleSaveEventuallyResultAsync(json, operationSet);
                }
            });
        }
        return handleSaveResultTask;
    }

    /**
     * Enqueues the saveEventually ParseOperationSet in {@link #taskQueue}.
     */
    private void enqueueSaveEventuallyOperationAsync(final ParseOperationSet operationSet) {
        if (!operationSet.isSaveEventually()) {
            throw new IllegalStateException(
                    "This should only be used to enqueue saveEventually operation sets");
        }

        taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> toAwait) {
                return toAwait.continueWithTask(new Continuation<Void, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<Void> task) {
                        ParseEventuallyQueue cache = Parse.getEventuallyQueue();
                        return cache.waitForOperationSetAndEventuallyPin(operationSet, null).makeVoid();
                    }
                });
            }
        });
    }

    /**
     * Handles the result of {@code saveEventually}.
     * <p>
     * In addition to normal save handling, this also notifies the saveEventually test helper.
     * <p>
     * Should be called on success or failure.
     */
    Task<Void> handleSaveEventuallyResultAsync(
            JSONObject json, ParseOperationSet operationSet) {
        final boolean success = json != null;
        Task<Void> handleSaveResultTask = handleSaveResultAsync(json, operationSet);

        return handleSaveResultTask.onSuccessTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                if (success) {
                    Parse.getEventuallyQueue()
                            .notifyTestHelper(ParseCommandCache.TestHelper.OBJECT_UPDATED);
                }
                return task;
            }
        });
    }

    /**
     * Called by {@link #saveInBackground()} and {@link #saveEventually(SaveCallback)}
     * and guaranteed to be thread-safe. Subclasses can override this method to do any custom updates
     * before an object gets saved.
     */
    void updateBeforeSave() {
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
     * waiting to be sent, subsequent calls to {@code #deleteEventually()} or
     * {@link #saveEventually()} will cause old instructions to be silently discarded until the
     * connection can be re-established, and the queued objects can be saved.
     *
     * @param callback - A callback which will be called if the delete completes before the app exits.
     */
    public final void deleteEventually(DeleteCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(deleteEventually(), callback);
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
     * waiting to be sent, subsequent calls to {@code #deleteEventually()} or
     * {@link #saveEventually()} will cause old instructions to be silently discarded until the
     * connection can be re-established, and the queued objects can be saved.
     *
     * @return A {@link bolts.Task} that is resolved when the delete completes.
     */
    public final Task<Void> deleteEventually() {
        final ParseRESTCommand command;
        final Task<JSONObject> runEventuallyTask;
        synchronized (mutex) {
            validateDelete();
            isDeletingEventually += 1;

            String localId = null;
            if (getObjectId() == null) {
                localId = getOrCreateLocalId();
            }

            // TODO(grantland): Convert to async
            final String sessionToken = ParseUser.getCurrentSessionToken();

            // See [1]
            command = ParseRESTObjectCommand.deleteObjectCommand(
                    getState(), sessionToken);
            command.setLocalId(localId);

            runEventuallyTask = Parse.getEventuallyQueue().enqueueEventuallyAsync(command, ParseObject.this);
        }

        Task<Void> handleDeleteResultTask;
        if (Parse.isLocalDatastoreEnabled()) {
            // ParsePinningEventuallyQueue calls handleDeleteEventuallyResultAsync directly.
            handleDeleteResultTask = runEventuallyTask.makeVoid();
        } else {
            handleDeleteResultTask = runEventuallyTask.onSuccessTask(new Continuation<JSONObject, Task<Void>>() {
                @Override
                public Task<Void> then(Task<JSONObject> task) {
                    return handleDeleteEventuallyResultAsync();
                }
            });
        }

        return handleDeleteResultTask;
    }

    /**
     * Handles the result of {@code deleteEventually}.
     * <p>
     * Should only be called on success.
     */
    Task<Void> handleDeleteEventuallyResultAsync() {
        synchronized (mutex) {
            isDeletingEventually -= 1;
        }
        Task<Void> handleDeleteResultTask = handleDeleteResultAsync();

        return handleDeleteResultTask.onSuccessTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                Parse.getEventuallyQueue()
                        .notifyTestHelper(ParseCommandCache.TestHelper.OBJECT_REMOVED);
                return task;
            }
        });
    }

    /**
     * Handles the result of {@code fetch}.
     * <p>
     * Should only be called on success.
     */
    Task<Void> handleFetchResultAsync(final ParseObject.State result) {
        Task<Void> task = Task.forResult(null);

        /*
         * If this object is in the offline store, then we need to make sure that we pull in any dirty
         * changes it may have before merging the server data into it.
         */
        final OfflineStore store = Parse.getLocalDatastore();
        if (store != null) {
            task = task.onSuccessTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    return store.fetchLocallyAsync(ParseObject.this).makeVoid();
                }
            }).continueWithTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    // Catch CACHE_MISS
                    if (task.getError() instanceof ParseException
                            && ((ParseException) task.getError()).getCode() == ParseException.CACHE_MISS) {
                        return null;
                    }
                    return task;
                }
            });
        }

        task = task.onSuccessTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                synchronized (mutex) {
                    State newState;
                    if (result.isComplete()) {
                        // Result is complete, so just replace
                        newState = result;
                    } else {
                        // Result is incomplete, so we'll need to apply it to the current state
                        newState = getState().newBuilder().apply(result).build();
                    }
                    setState(newState);
                }
                return null;
            }
        });

        if (store != null) {
            task = task.onSuccessTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    return store.updateDataForObjectAsync(ParseObject.this);
                }
            }).continueWithTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    // Catch CACHE_MISS
                    if (task.getError() instanceof ParseException
                            && ((ParseException) task.getError()).getCode() == ParseException.CACHE_MISS) {
                        return null;
                    }
                    return task;
                }
            });
        }

        return task;
    }

    /**
     * Fetches this object with the data from the server. Call this whenever you want the state of the
     * object to reflect exactly what is on the server.
     *
     * @return The {@code ParseObject} that was fetched.
     * @throws ParseException Throws an exception if the server is inaccessible.
     */
    public <T extends ParseObject> T fetch() throws ParseException {
        return ParseTaskUtils.wait(this.<T>fetchInBackground());
    }

    @SuppressWarnings("unchecked")
    <T extends ParseObject> Task<T> fetchAsync(
            final String sessionToken, Task<Void> toAwait) {
        return toAwait.onSuccessTask(new Continuation<Void, Task<ParseObject.State>>() {
            @Override
            public Task<ParseObject.State> then(Task<Void> task) {
                State state;
                Map<String, ParseObject> fetchedObjects;
                synchronized (mutex) {
                    state = getState();
                    fetchedObjects = collectFetchedObjects();
                }
                ParseDecoder decoder = new KnownParseObjectDecoder(fetchedObjects);
                return getObjectController().fetchAsync(state, sessionToken, decoder);
            }
        }).onSuccessTask(new Continuation<ParseObject.State, Task<Void>>() {
            @Override
            public Task<Void> then(Task<ParseObject.State> task) {
                ParseObject.State result = task.getResult();
                return handleFetchResultAsync(result);
            }
        }).onSuccess(new Continuation<Void, T>() {
            @Override
            public T then(Task<Void> task) {
                return (T) ParseObject.this;
            }
        });
    }

    /**
     * Fetches this object with the data from the server in a background thread. This is preferable to
     * using fetch(), unless your code is already running from a background thread.
     *
     * @return A {@link bolts.Task} that is resolved when fetch completes.
     */
    public final <T extends ParseObject> Task<T> fetchInBackground() {
        return ParseUser.getCurrentSessionTokenAsync().onSuccessTask(new Continuation<String, Task<T>>() {
            @Override
            public Task<T> then(Task<String> task) {
                final String sessionToken = task.getResult();
                return taskQueue.enqueue(new Continuation<Void, Task<T>>() {
                    @Override
                    public Task<T> then(Task<Void> toAwait) {
                        return fetchAsync(sessionToken, toAwait);
                    }
                });
            }
        });
    }

    /**
     * Fetches this object with the data from the server in a background thread. This is preferable to
     * using fetch(), unless your code is already running from a background thread.
     *
     * @param callback {@code callback.done(object, e)} is called when the fetch completes.
     */
    public final <T extends ParseObject> void fetchInBackground(GetCallback<T> callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(this.<T>fetchInBackground(), callback);
    }

    /**
     * If this {@code ParseObject} has not been fetched (i.e. {@link #isDataAvailable()} returns {@code false}),
     * fetches this object with the data from the server in a background thread. This is preferable to
     * using {@link #fetchIfNeeded()}, unless your code is already running from a background thread.
     *
     * @return A {@link bolts.Task} that is resolved when fetch completes.
     */
    public final <T extends ParseObject> Task<T> fetchIfNeededInBackground() {
        if (isDataAvailable()) {
            return Task.forResult((T) this);
        }
        return ParseUser.getCurrentSessionTokenAsync().onSuccessTask(new Continuation<String, Task<T>>() {
            @Override
            public Task<T> then(Task<String> task) {
                final String sessionToken = task.getResult();
                return taskQueue.enqueue(new Continuation<Void, Task<T>>() {
                    @Override
                    public Task<T> then(Task<Void> toAwait) {
                        if (isDataAvailable()) {
                            return Task.forResult((T) ParseObject.this);
                        }
                        return fetchAsync(sessionToken, toAwait);
                    }
                });
            }
        });

    }

    /**
     * If this {@code ParseObject} has not been fetched (i.e. {@link #isDataAvailable()} returns {@code false}),
     * fetches this object with the data from the server.
     *
     * @return The fetched {@code ParseObject}.
     * @throws ParseException Throws an exception if the server is inaccessible.
     */
    public <T extends ParseObject> T fetchIfNeeded() throws ParseException {
        return ParseTaskUtils.wait(this.<T>fetchIfNeededInBackground());
    }

    /**
     * If this {@code ParseObject} has not been fetched (i.e. {@link #isDataAvailable()} returns {@code false}),
     * fetches this object with the data from the server in a background thread. This is preferable to
     * using {@link #fetchIfNeeded()}, unless your code is already running from a background thread.
     *
     * @param callback {@code callback.done(object, e)} is called when the fetch completes.
     */
    public final <T extends ParseObject> void fetchIfNeededInBackground(GetCallback<T> callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(this.<T>fetchIfNeededInBackground(), callback);
    }

    // Validates the delete method
    void validateDelete() {
        // do nothing
    }

    private Task<Void> deleteAsync(final String sessionToken, Task<Void> toAwait) {
        validateDelete();

        return toAwait.onSuccessTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                isDeleting = true;
                if (state.objectId() == null) {
                    return task.cast(); // no reason to call delete since it doesn't exist
                }
                return deleteAsync(sessionToken);
            }
        }).onSuccessTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) {
                return handleDeleteResultAsync();
            }
        }).continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) throws Exception {
                isDeleting = false;
                if (task.isFaulted()) {
                    throw task.getError();
                }
                return null;
            }
        });
    }

    //TODO (grantland): I'm not sure we want direct access to this. All access to `delete` should
    // enqueue on the taskQueue...
    Task<Void> deleteAsync(String sessionToken) {
        return getObjectController().deleteAsync(getState(), sessionToken);
    }

    /**
     * Handles the result of {@code delete}.
     * <p>
     * Should only be called on success.
     */
    Task<Void> handleDeleteResultAsync() {
        Task<Void> task = Task.forResult(null);

        synchronized (mutex) {
            isDeleted = true;
        }

        final OfflineStore store = Parse.getLocalDatastore();
        if (store != null) {
            task = task.continueWithTask(new Continuation<Void, Task<Void>>() {
                @Override
                public Task<Void> then(Task<Void> task) {
                    synchronized (mutex) {
                        if (isDeleted) {
                            store.unregisterObject(ParseObject.this);
                            return store.deleteDataForObjectAsync(ParseObject.this);
                        } else {
                            return store.updateDataForObjectAsync(ParseObject.this);
                        }
                    }
                }
            });
        }

        return task;
    }

    /**
     * Deletes this object on the server in a background thread. This is preferable to using
     * {@link #delete()}, unless your code is already running from a background thread.
     *
     * @return A {@link bolts.Task} that is resolved when delete completes.
     */
    public final Task<Void> deleteInBackground() {
        return ParseUser.getCurrentSessionTokenAsync().onSuccessTask(new Continuation<String, Task<Void>>() {
            @Override
            public Task<Void> then(Task<String> task) {
                final String sessionToken = task.getResult();
                return taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<Void> toAwait) {
                        return deleteAsync(sessionToken, toAwait);
                    }
                });
            }
        });
    }

    /**
     * Deletes this object on the server. This does not delete or destroy the object locally.
     *
     * @throws ParseException Throws an error if the object does not exist or if the internet fails.
     */
    public final void delete() throws ParseException {
        ParseTaskUtils.wait(deleteInBackground());
    }

    /**
     * Deletes this object on the server in a background thread. This is preferable to using
     * {@link #delete()}, unless your code is already running from a background thread.
     *
     * @param callback {@code callback.done(e)} is called when the save completes.
     */
    public final void deleteInBackground(DeleteCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(deleteInBackground(), callback);
    }

    /**
     * Returns {@code true} if this object can be serialized for saving.
     */
    private boolean canBeSerialized() {
        synchronized (mutex) {
            final Capture<Boolean> result = new Capture<>(true);

            // This method is only used for batching sets of objects for saveAll
            // and when saving children automatically. Since it's only used to
            // determine whether or not save should be called on them, it only
            // needs to examine their current values, so we use estimatedData.
            new ParseTraverser() {
                @Override
                protected boolean visit(Object value) {
                    if (value instanceof ParseFile) {
                        ParseFile file = (ParseFile) value;
                        if (file.isDirty()) {
                            result.set(false);
                        }
                    }

                    if (value instanceof ParseObject) {
                        ParseObject object = (ParseObject) value;
                        if (object.getObjectId() == null) {
                            result.set(false);
                        }
                    }

                    // Continue to traverse only if it can still be serialized.
                    return result.get();
                }
            }.setYieldRoot(false).setTraverseParseObjects(true).traverse(this);

            return result.get();
        }
    }

    /**
     * Return the operations that will be sent in the next call to save.
     */
    private ParseOperationSet currentOperations() {
        synchronized (mutex) {
            return operationSetQueue.getLast();
        }
    }

    /**
     * Updates the estimated values in the map based on the given set of ParseFieldOperations.
     */
    private void applyOperations(ParseOperationSet operations, Map<String, Object> map) {
        for (String key : operations.keySet()) {
            ParseFieldOperation operation = operations.get(key);
            Object oldValue = map.get(key);
            Object newValue = operation.apply(oldValue, key);
            if (newValue != null) {
                map.put(key, newValue);
            } else {
                map.remove(key);
            }
        }
    }

    /**
     * Regenerates the estimatedData map from the serverData and operations.
     */
    private void rebuildEstimatedData() {
        synchronized (mutex) {
            estimatedData.clear();
            for (String key : state.keySet()) {
                estimatedData.put(key, state.get(key));
            }
            for (ParseOperationSet operations : operationSetQueue) {
                applyOperations(operations, estimatedData);
            }
        }
    }

    void markAllFieldsDirty() {
        synchronized (mutex) {
            for (String key : state.keySet()) {
                performPut(key, state.get(key));
            }
        }
    }

    /**
     * performOperation() is like {@link #put(String, Object)} but instead of just taking a new value,
     * it takes a ParseFieldOperation that modifies the value.
     */
    void performOperation(String key, ParseFieldOperation operation) {
        synchronized (mutex) {
            Object oldValue = estimatedData.get(key);
            Object newValue = operation.apply(oldValue, key);
            if (newValue != null) {
                estimatedData.put(key, newValue);
            } else {
                estimatedData.remove(key);
            }

            ParseFieldOperation oldOperation = currentOperations().get(key);
            ParseFieldOperation newOperation = operation.mergeWithPrevious(oldOperation);
            currentOperations().put(key, newOperation);
        }
    }

    /**
     * Add a key-value pair to this object. It is recommended to name keys in
     * <code>camelCaseLikeThis</code>.
     *
     * @param key   Keys must be alphanumerical plus underscore, and start with a letter.
     * @param value Values may be numerical, {@link String}, {@link JSONObject}, {@link JSONArray},
     *              {@link JSONObject#NULL}, or other {@code ParseObject}s. value may not be {@code null}.
     */
    public void put(@NonNull String key, @NonNull Object value) {
        checkKeyIsMutable(key);

        performPut(key, value);
    }

    void performPut(String key, Object value) {
        if (key == null) {
            throw new IllegalArgumentException("key may not be null.");
        }

        if (value == null) {
            throw new IllegalArgumentException("value may not be null.");
        }

        if (value instanceof JSONObject) {
            ParseDecoder decoder = ParseDecoder.get();
            value = decoder.convertJSONObjectToMap((JSONObject) value);
        } else if (value instanceof JSONArray) {
            ParseDecoder decoder = ParseDecoder.get();
            value = decoder.convertJSONArrayToList((JSONArray) value);
        }

        if (!ParseEncoder.isValidType(value)) {
            throw new IllegalArgumentException("invalid type for value: " + value.getClass().toString());
        }

        performOperation(key, new ParseSetOperation(value));
    }

    /**
     * Whether this object has a particular key. Same as {@link #containsKey(String)}.
     *
     * @param key The key to check for
     * @return Whether this object contains the key
     */
    public boolean has(@NonNull String key) {
        return containsKey(key);
    }

    /**
     * Atomically increments the given key by 1.
     *
     * @param key The key to increment.
     */
    public void increment(@NonNull String key) {
        increment(key, 1);
    }

    /**
     * Atomically increments the given key by the given number.
     *
     * @param key    The key to increment.
     * @param amount The amount to increment by.
     */
    public void increment(@NonNull String key, @NonNull Number amount) {
        ParseIncrementOperation operation = new ParseIncrementOperation(amount);
        performOperation(key, operation);
    }

    /**
     * Atomically adds an object to the end of the array associated with a given key.
     *
     * @param key   The key.
     * @param value The object to add.
     */
    public void add(@NonNull String key, Object value) {
        this.addAll(key, Collections.singletonList(value));
    }

    /**
     * Atomically adds the objects contained in a {@code Collection} to the end of the array
     * associated with a given key.
     *
     * @param key    The key.
     * @param values The objects to add.
     */
    public void addAll(@NonNull String key, Collection<?> values) {
        ParseAddOperation operation = new ParseAddOperation(values);
        performOperation(key, operation);
    }

    /**
     * Atomically adds an object to the array associated with a given key, only if it is not already
     * present in the array. The position of the insert is not guaranteed.
     *
     * @param key   The key.
     * @param value The object to add.
     */
    public void addUnique(@NonNull String key, Object value) {
        this.addAllUnique(key, Collections.singletonList(value));
    }

    /**
     * Atomically adds the objects contained in a {@code Collection} to the array associated with a
     * given key, only adding elements which are not already present in the array. The position of the
     * insert is not guaranteed.
     *
     * @param key    The key.
     * @param values The objects to add.
     */
    public void addAllUnique(@NonNull String key, Collection<?> values) {
        ParseAddUniqueOperation operation = new ParseAddUniqueOperation(values);
        performOperation(key, operation);
    }

    /**
     * Removes a key from this object's data if it exists.
     *
     * @param key The key to remove.
     */
    public void remove(@NonNull String key) {
        checkKeyIsMutable(key);

        performRemove(key);
    }

    void performRemove(String key) {
        synchronized (mutex) {
            Object object = get(key);

            if (object != null) {
                performOperation(key, ParseDeleteOperation.getInstance());
            }
        }
    }

    /**
     * Atomically removes all instances of the objects contained in a {@code Collection} from the
     * array associated with a given key. To maintain consistency with the Java Collection API, there
     * is no method removing all instances of a single object. Instead, you can call
     * {@code parseObject.removeAll(key, Arrays.asList(value))}.
     *
     * @param key    The key.
     * @param values The objects to remove.
     */
    public void removeAll(@NonNull String key, Collection<?> values) {
        checkKeyIsMutable(key);

        ParseRemoveOperation operation = new ParseRemoveOperation(values);
        performOperation(key, operation);
    }

    private void checkKeyIsMutable(String key) {
        if (!isKeyMutable(key)) {
            throw new IllegalArgumentException("Cannot modify `" + key
                    + "` property of an " + getClassName() + " object.");
        }
    }

    boolean isKeyMutable(String key) {
        return true;
    }

    /**
     * Whether this object has a particular key. Same as {@link #has(String)}.
     *
     * @param key The key to check for
     * @return Whether this object contains the key
     */
    public boolean containsKey(@NonNull String key) {
        synchronized (mutex) {
            return estimatedData.containsKey(key);
        }
    }

    /**
     * Access a {@link String} value.
     *
     * @param key The key to access the value for.
     * @return {@code null} if there is no such key or if it is not a {@link String}.
     */
    @Nullable
    public String getString(@NonNull String key) {
        synchronized (mutex) {
            checkGetAccess(key);
            Object value = estimatedData.get(key);
            if (!(value instanceof String)) {
                return null;
            }
            return (String) value;
        }
    }

    /**
     * Access a {@code byte[]} value.
     *
     * @param key The key to access the value for.
     * @return {@code null} if there is no such key or if it is not a {@code byte[]}.
     */
    @Nullable
    public byte[] getBytes(String key) {
        synchronized (mutex) {
            checkGetAccess(key);
            Object value = estimatedData.get(key);
            if (!(value instanceof byte[])) {
                return null;
            }

            return (byte[]) value;
        }
    }

    /**
     * Access a {@link Number} value.
     *
     * @param key The key to access the value for.
     * @return {@code null} if there is no such key or if it is not a {@link Number}.
     */
    @Nullable
    public Number getNumber(String key) {
        synchronized (mutex) {
            checkGetAccess(key);
            Object value = estimatedData.get(key);
            if (!(value instanceof Number)) {
                return null;
            }
            return (Number) value;
        }
    }

    /**
     * Access a {@link JSONArray} value.
     *
     * @param key The key to access the value for.
     * @return {@code null} if there is no such key or if it is not a {@link JSONArray}.
     */
    @Nullable
    public JSONArray getJSONArray(String key) {
        synchronized (mutex) {
            checkGetAccess(key);
            Object value = estimatedData.get(key);

            if (value instanceof List) {
                value = PointerOrLocalIdEncoder.get().encode(value);
            }

            if (!(value instanceof JSONArray)) {
                return null;
            }
            return (JSONArray) value;
        }
    }

    /**
     * Access a {@link List} value.
     *
     * @param key The key to access the value for
     * @return {@code null} if there is no such key or if the value can't be converted to a
     * {@link List}.
     */
    @Nullable
    public <T> List<T> getList(String key) {
        synchronized (mutex) {
            Object value = estimatedData.get(key);
            if (!(value instanceof List)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            List<T> returnValue = (List<T>) value;
            return returnValue;
        }
    }

    /**
     * Access a {@link Map} value
     *
     * @param key The key to access the value for
     * @return {@code null} if there is no such key or if the value can't be converted to a
     * {@link Map}.
     */
    @Nullable
    public <V> Map<String, V> getMap(String key) {
        synchronized (mutex) {
            Object value = estimatedData.get(key);
            if (!(value instanceof Map)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, V> returnValue = (Map<String, V>) value;
            return returnValue;
        }
    }

    /**
     * Access a {@link JSONObject} value.
     *
     * @param key The key to access the value for.
     * @return {@code null} if there is no such key or if it is not a {@link JSONObject}.
     */
    @Nullable
    public JSONObject getJSONObject(String key) {
        synchronized (mutex) {
            checkGetAccess(key);
            Object value = estimatedData.get(key);

            if (value instanceof Map) {
                value = PointerOrLocalIdEncoder.get().encode(value);
            }

            if (!(value instanceof JSONObject)) {
                return null;
            }

            return (JSONObject) value;
        }
    }

    /**
     * Access an {@code int} value.
     *
     * @param key The key to access the value for.
     * @return {@code 0} if there is no such key or if it is not a {@code int}.
     */
    public int getInt(@NonNull String key) {
        Number number = getNumber(key);
        if (number == null) {
            return 0;
        }
        return number.intValue();
    }

    /**
     * Access a {@code double} value.
     *
     * @param key The key to access the value for.
     * @return {@code 0} if there is no such key or if it is not a {@code double}.
     */
    public double getDouble(@NonNull String key) {
        Number number = getNumber(key);
        if (number == null) {
            return 0;
        }
        return number.doubleValue();
    }

    /**
     * Access a {@code long} value.
     *
     * @param key The key to access the value for.
     * @return {@code 0} if there is no such key or if it is not a {@code long}.
     */
    public long getLong(@NonNull String key) {
        Number number = getNumber(key);
        if (number == null) {
            return 0;
        }
        return number.longValue();
    }

    /**
     * Access a {@code boolean} value.
     *
     * @param key The key to access the value for.
     * @return {@code false} if there is no such key or if it is not a {@code boolean}.
     */
    public boolean getBoolean(@NonNull String key) {
        synchronized (mutex) {
            checkGetAccess(key);
            Object value = estimatedData.get(key);
            if (!(value instanceof Boolean)) {
                return false;
            }
            return (Boolean) value;
        }
    }

    /**
     * Access a {@link Date} value.
     *
     * @param key The key to access the value for.
     * @return {@code null} if there is no such key or if it is not a {@link Date}.
     */
    @Nullable
    public Date getDate(@NonNull String key) {
        synchronized (mutex) {
            checkGetAccess(key);
            Object value = estimatedData.get(key);
            if (!(value instanceof Date)) {
                return null;
            }
            return (Date) value;
        }
    }

    /**
     * Access a {@code ParseObject} value. This function will not perform a network request. Unless the
     * {@code ParseObject} has been downloaded (e.g. by a {@link ParseQuery#include(String)} or by calling
     * {@link #fetchIfNeeded()} or {@link #fetch()}), {@link #isDataAvailable()} will return
     * {@code false}.
     *
     * @param key The key to access the value for.
     * @return {@code null} if there is no such key or if it is not a {@code ParseObject}.
     */
    @Nullable
    public ParseObject getParseObject(@NonNull String key) {
        Object value = get(key);
        if (!(value instanceof ParseObject)) {
            return null;
        }
        return (ParseObject) value;
    }

    /**
     * Access a {@link ParseUser} value. This function will not perform a network request. Unless the
     * {@code ParseObject} has been downloaded (e.g. by a {@link ParseQuery#include(String)} or by calling
     * {@link #fetchIfNeeded()} or {@link #fetch()}), {@link #isDataAvailable()} will return
     * {@code false}.
     *
     * @param key The key to access the value for.
     * @return {@code null} if there is no such key or if the value is not a {@link ParseUser}.
     */
    @Nullable
    public ParseUser getParseUser(@NonNull String key) {
        Object value = get(key);
        if (!(value instanceof ParseUser)) {
            return null;
        }
        return (ParseUser) value;
    }

    /**
     * Access a {@link ParseFile} value. This function will not perform a network request. Unless the
     * {@link ParseFile} has been downloaded (e.g. by calling {@link ParseFile#getData()}),
     * {@link ParseFile#isDataAvailable()} will return {@code false}.
     *
     * @param key The key to access the value for.
     * @return {@code null} if there is no such key or if it is not a {@link ParseFile}.
     */
    @Nullable
    public ParseFile getParseFile(@NonNull String key) {
        Object value = get(key);
        if (!(value instanceof ParseFile)) {
            return null;
        }
        return (ParseFile) value;
    }

    /**
     * Access a {@link ParseGeoPoint} value.
     *
     * @param key The key to access the value for
     * @return {@code null} if there is no such key or if it is not a {@link ParseGeoPoint}.
     */
    @Nullable
    public ParseGeoPoint getParseGeoPoint(@NonNull String key) {
        synchronized (mutex) {
            checkGetAccess(key);
            Object value = estimatedData.get(key);
            if (!(value instanceof ParseGeoPoint)) {
                return null;
            }
            return (ParseGeoPoint) value;
        }
    }

    /**
     * Access a {@link ParsePolygon} value.
     *
     * @param key The key to access the value for
     * @return {@code null} if there is no such key or if it is not a {@link ParsePolygon}.
     */
    @Nullable
    public ParsePolygon getParsePolygon(@NonNull String key) {
        synchronized (mutex) {
            checkGetAccess(key);
            Object value = estimatedData.get(key);
            if (!(value instanceof ParsePolygon)) {
                return null;
            }
            return (ParsePolygon) value;
        }
    }

    /**
     * Access the {@link ParseACL} governing this object.
     */
    @Nullable
    public ParseACL getACL() {
        return getACL(true);
    }

    /**
     * Set the {@link ParseACL} governing this object.
     */
    public void setACL(ParseACL acl) {
        put(KEY_ACL, acl);
    }

    private ParseACL getACL(boolean mayCopy) {
        synchronized (mutex) {
            checkGetAccess(KEY_ACL);
            Object acl = estimatedData.get(KEY_ACL);
            if (acl == null) {
                return null;
            }
            if (!(acl instanceof ParseACL)) {
                throw new RuntimeException("only ACLs can be stored in the ACL key");
            }
            if (mayCopy && ((ParseACL) acl).isShared()) {
                ParseACL copy = new ParseACL((ParseACL) acl);
                estimatedData.put(KEY_ACL, copy);
                return copy;
            }
            return (ParseACL) acl;
        }
    }

    /**
     * Gets whether the {@code ParseObject} has been fetched.
     *
     * @return {@code true} if the {@code ParseObject} is new or has been fetched or refreshed. {@code false}
     * otherwise.
     */
    public boolean isDataAvailable() {
        synchronized (mutex) {
            return state.isComplete();
        }
    }

    /**
     * Gets whether the {@code ParseObject} specified key has been fetched.
     * This means the property can be accessed safely.
     *
     * @return {@code true} if the {@code ParseObject} key is new or has been fetched or refreshed. {@code false}
     * otherwise.
     */
    public boolean isDataAvailable(@NonNull String key) {
        synchronized (mutex) {
            // Fallback to estimatedData to include dirty changes.
            return isDataAvailable() || state.availableKeys().contains(key) || estimatedData.containsKey(key);
        }
    }

    /**
     * Access or create a {@link ParseRelation} value for a key
     *
     * @param key The key to access the relation for.
     * @return the ParseRelation object if the relation already exists for the key or can be created
     * for this key.
     */
    @NonNull
    public <T extends ParseObject> ParseRelation<T> getRelation(@NonNull String key) {
        synchronized (mutex) {
            // All the sanity checking is done when add or remove is called on the relation.
            Object value = estimatedData.get(key);
            if (value instanceof ParseRelation) {
                @SuppressWarnings("unchecked")
                ParseRelation<T> relation = (ParseRelation<T>) value;
                relation.ensureParentAndKey(this, key);
                return relation;
            } else {
                ParseRelation<T> relation = new ParseRelation<>(this, key);
                /*
                 * We put the relation into the estimated data so that we'll get the same instance later,
                 * which may have known objects cached. If we rebuildEstimatedData, then this relation will
                 * be lost, and we'll get a new one. That's okay, because any cached objects it knows about
                 * must be replayable from the operations in the queue. If there were any objects in this
                 * relation that weren't still in the queue, then they would be in the copy of the
                 * ParseRelation that's in the serverData, so we would have gotten that instance instead.
                 */
                estimatedData.put(key, relation);
                return relation;
            }
        }
    }

    /**
     * Access a value. In most cases it is more convenient to use a helper function such as
     * {@link #getString(String)} or {@link #getInt(String)}.
     *
     * @param key The key to access the value for.
     * @return {@code null} if there is no such key.
     */
    @Nullable
    public Object get(@NonNull String key) {
        synchronized (mutex) {
            if (key.equals(KEY_ACL)) {
                return getACL();
            }

            checkGetAccess(key);
            Object value = estimatedData.get(key);

            // A relation may be deserialized without a parent or key.
            // Either way, make sure it's consistent.
            if (value instanceof ParseRelation) {
                ((ParseRelation<?>) value).ensureParentAndKey(this, key);
            }

            return value;
        }
    }

    private void checkGetAccess(String key) {
        if (!isDataAvailable(key)) {
            throw new IllegalStateException(
                    "ParseObject has no data for '" + key + "'. Call fetchIfNeeded() to get the data.");
        }
    }

    public boolean hasSameId(ParseObject other) {
        synchronized (mutex) {
            return this.getClassName() != null && this.getObjectId() != null
                    && this.getClassName().equals(other.getClassName())
                    && this.getObjectId().equals(other.getObjectId());
        }
    }

    void registerSaveListener(GetCallback<ParseObject> callback) {
        synchronized (mutex) {
            saveEvent.subscribe(callback);
        }
    }

    void unregisterSaveListener(GetCallback<ParseObject> callback) {
        synchronized (mutex) {
            saveEvent.unsubscribe(callback);
        }
    }

    /**
     * Called when a non-pointer is being created to allow additional initialization to occur.
     */
    void setDefaultValues() {
        if (needsDefaultACL() && ParseACL.getDefaultACL() != null) {
            this.setACL(ParseACL.getDefaultACL());
        }
    }

    /**
     * Determines whether this object should get a default ACL. Override in subclasses to turn off
     * default ACLs.
     */
    boolean needsDefaultACL() {
        return true;
    }

    /**
     * Loads data from the local datastore into this object, if it has not been fetched from the
     * server already. If the object is not stored in the local datastore, this method with do
     * nothing.
     */
    @SuppressWarnings("unchecked")
    <T extends ParseObject> Task<T> fetchFromLocalDatastoreAsync() {
        if (!Parse.isLocalDatastoreEnabled()) {
            throw new IllegalStateException("Method requires Local Datastore. " +
                    "Please refer to `Parse#enableLocalDatastore(Context)`.");
        }
        return Parse.getLocalDatastore().fetchLocallyAsync((T) this);
    }

    /**
     * Loads data from the local datastore into this object, if it has not been fetched from the
     * server already. If the object is not stored in the local datastore, this method with do
     * nothing.
     */
    public <T extends ParseObject> void fetchFromLocalDatastoreInBackground(GetCallback<T> callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(this.<T>fetchFromLocalDatastoreAsync(), callback);
    }

    /**
     * Loads data from the local datastore into this object, if it has not been fetched from the
     * server already. If the object is not stored in the local datastore, this method with throw a
     * CACHE_MISS exception.
     *
     * @throws ParseException exception if fails
     */
    public void fetchFromLocalDatastore() throws ParseException {
        ParseTaskUtils.wait(fetchFromLocalDatastoreAsync());
    }

    /**
     * Stores the object and every object it points to in the local datastore, recursively. If those
     * other objects have not been fetched from Parse, they will not be stored. However, if they have
     * changed data, all of the changes will be retained. To get the objects back later, you can use
     * {@link ParseQuery#fromLocalDatastore()}, or you can create an unfetched pointer with
     * {@link #createWithoutData(Class, String)} and then call {@link #fetchFromLocalDatastore()} on
     * it.
     *
     * @param callback the callback
     * @see #unpinInBackground(String, DeleteCallback)
     */
    public void pinInBackground(String name, SaveCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(pinInBackground(name), callback);
    }

    /**
     * Stores the object and every object it points to in the local datastore, recursively. If those
     * other objects have not been fetched from Parse, they will not be stored. However, if they have
     * changed data, all of the changes will be retained. To get the objects back later, you can use
     * {@link ParseQuery#fromLocalDatastore()}, or you can create an unfetched pointer with
     * {@link #createWithoutData(Class, String)} and then call {@link #fetchFromLocalDatastore()} on
     * it.
     *
     * @return A {@link bolts.Task} that is resolved when pinning completes.
     * @see #unpinInBackground(String)
     */
    public Task<Void> pinInBackground(String name) {
        return pinAllInBackground(name, Collections.singletonList(this));
    }

    Task<Void> pinInBackground(String name, boolean includeAllChildren) {
        return pinAllInBackground(name, Collections.singletonList(this), includeAllChildren);
    }

    /**
     * Stores the object and every object it points to in the local datastore, recursively. If those
     * other objects have not been fetched from Parse, they will not be stored. However, if they have
     * changed data, all of the changes will be retained. To get the objects back later, you can use
     * {@link ParseQuery#fromLocalDatastore()}, or you can create an unfetched pointer with
     * {@link #createWithoutData(Class, String)} and then call {@link #fetchFromLocalDatastore()} on
     * it.
     *
     * @throws ParseException exception if fails
     * @see #unpin(String)
     */
    public void pin(String name) throws ParseException {
        ParseTaskUtils.wait(pinInBackground(name));
    }

    /**
     * Stores the object and every object it points to in the local datastore, recursively. If those
     * other objects have not been fetched from Parse, they will not be stored. However, if they have
     * changed data, all of the changes will be retained. To get the objects back later, you can use
     * {@link ParseQuery#fromLocalDatastore()}, or you can create an unfetched pointer with
     * {@link #createWithoutData(Class, String)} and then call {@link #fetchFromLocalDatastore()} on
     * it.
     *
     * @param callback the callback
     * @see #unpinInBackground(DeleteCallback)
     * @see #DEFAULT_PIN
     */
    public void pinInBackground(SaveCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(pinInBackground(), callback);
    }

    /**
     * Stores the object and every object it points to in the local datastore, recursively. If those
     * other objects have not been fetched from Parse, they will not be stored. However, if they have
     * changed data, all of the changes will be retained. To get the objects back later, you can use
     * {@link ParseQuery#fromLocalDatastore()}, or you can create an unfetched pointer with
     * {@link #createWithoutData(Class, String)} and then call {@link #fetchFromLocalDatastore()} on
     * it.
     *
     * @return A {@link bolts.Task} that is resolved when pinning completes.
     * @see #unpinInBackground()
     * @see #DEFAULT_PIN
     */
    public Task<Void> pinInBackground() {
        return pinAllInBackground(DEFAULT_PIN, Collections.singletonList(this));
    }

    /**
     * Stores the object and every object it points to in the local datastore, recursively. If those
     * other objects have not been fetched from Parse, they will not be stored. However, if they have
     * changed data, all of the changes will be retained. To get the objects back later, you can use
     * {@link ParseQuery#fromLocalDatastore()}, or you can create an unfetched pointer with
     * {@link #createWithoutData(Class, String)} and then call {@link #fetchFromLocalDatastore()} on
     * it.
     *
     * @throws ParseException exception if fails
     * @see #unpin()
     * @see #DEFAULT_PIN
     */
    public void pin() throws ParseException {
        ParseTaskUtils.wait(pinInBackground());
    }

    /**
     * Removes the object and every object it points to in the local datastore, recursively.
     *
     * @param callback the callback
     * @see #pinInBackground(String, SaveCallback)
     */
    public void unpinInBackground(String name, DeleteCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(unpinInBackground(name), callback);
    }

    /**
     * Removes the object and every object it points to in the local datastore, recursively.
     *
     * @return A {@link bolts.Task} that is resolved when unpinning completes.
     * @see #pinInBackground(String)
     */
    public Task<Void> unpinInBackground(String name) {
        return unpinAllInBackground(name, Collections.singletonList(this));
    }

    /**
     * Removes the object and every object it points to in the local datastore, recursively.
     *
     * @see #pin(String)
     */
    public void unpin(String name) throws ParseException {
        ParseTaskUtils.wait(unpinInBackground(name));
    }

    /**
     * Removes the object and every object it points to in the local datastore, recursively.
     *
     * @param callback the callback
     * @see #pinInBackground(SaveCallback)
     * @see #DEFAULT_PIN
     */
    public void unpinInBackground(DeleteCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(unpinInBackground(), callback);
    }

    /**
     * Removes the object and every object it points to in the local datastore, recursively.
     *
     * @return A {@link bolts.Task} that is resolved when unpinning completes.
     * @see #pinInBackground()
     * @see #DEFAULT_PIN
     */
    public Task<Void> unpinInBackground() {
        return unpinAllInBackground(DEFAULT_PIN, Collections.singletonList(this));
    }

    /**
     * Removes the object and every object it points to in the local datastore, recursively.
     *
     * @see #pin()
     * @see #DEFAULT_PIN
     */
    public void unpin() throws ParseException {
        ParseTaskUtils.wait(unpinInBackground());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeToParcel(dest, new ParseObjectParcelEncoder(this));
    }

    void writeToParcel(Parcel dest, ParseParcelEncoder encoder) {
        synchronized (mutex) {
            // Developer warnings.
            ldsEnabledWhenParceling = Parse.isLocalDatastoreEnabled();
            boolean saving = hasOutstandingOperations();
            boolean deleting = isDeleting || isDeletingEventually > 0;
            if (saving) {
                PLog.w(TAG, "About to parcel a ParseObject while a save / saveEventually operation is " +
                        "going on. If recovered from LDS, the unparceled object will be internally updated when " +
                        "these tasks end. If not, it will act as if these tasks have failed. This means that " +
                        "the subsequent call to save() will update again the same keys, and this is dangerous " +
                        "for certain operations, like increment(). To avoid inconsistencies, wait for operations " +
                        "to end before parceling.");
            }
            if (deleting) {
                PLog.w(TAG, "About to parcel a ParseObject while a delete / deleteEventually operation is " +
                        "going on. If recovered from LDS, the unparceled object will be internally updated when " +
                        "these tasks end. If not, it will assume it's not deleted, and might incorrectly " +
                        "return false for isDirty(). To avoid inconsistencies, wait for operations to end " +
                        "before parceling.");
            }
            // Write className and id first, regardless of state.
            dest.writeString(getClassName());
            String objectId = getObjectId();
            dest.writeByte(objectId != null ? (byte) 1 : 0);
            if (objectId != null) dest.writeString(objectId);
            // Write state and other members
            state.writeToParcel(dest, encoder);
            dest.writeByte(localId != null ? (byte) 1 : 0);
            if (localId != null) dest.writeString(localId);
            dest.writeByte(isDeleted ? (byte) 1 : 0);
            // Care about dirty changes and ongoing tasks.
            ParseOperationSet set;
            if (hasOutstandingOperations()) {
                // There's more than one set. Squash the queue, creating copies
                // to preserve the original queue when LDS is enabled.
                set = new ParseOperationSet();
                for (ParseOperationSet operationSet : operationSetQueue) {
                    ParseOperationSet copy = new ParseOperationSet(operationSet);
                    copy.mergeFrom(set);
                    set = copy;
                }
            } else {
                set = operationSetQueue.getLast();
            }
            set.setIsSaveEventually(false);
            set.toParcel(dest, encoder);
            // Pass a Bundle to subclasses.
            Bundle bundle = new Bundle();
            onSaveInstanceState(bundle);
            dest.writeBundle(bundle);
        }
    }

    /**
     * Called when parceling this ParseObject.
     * Subclasses can put values into the provided {@link Bundle} and receive them later
     * {@link #onRestoreInstanceState(Bundle)}. Note that internal fields are already parceled by
     * the framework.
     *
     * @param outState Bundle to host extra values
     */
    protected void onSaveInstanceState(Bundle outState) {
    }

    /**
     * Called when unparceling this ParseObject.
     * Subclasses can read values from the provided {@link Bundle} that were previously put
     * during {@link #onSaveInstanceState(Bundle)}. At this point the internal state is already
     * recovered.
     *
     * @param savedState Bundle to read the values from
     */
    protected void onRestoreInstanceState(Bundle savedState) {
    }

    static class State {

        private final String className;
        private final String objectId;
        private final long createdAt;
        private final long updatedAt;
        private final Map<String, Object> serverData;
        private final Set<String> availableKeys;
        private final boolean isComplete;

        State(Init<?> builder) {
            className = builder.className;
            objectId = builder.objectId;
            createdAt = builder.createdAt;
            updatedAt = builder.updatedAt > 0
                    ? builder.updatedAt
                    : createdAt;
            serverData = Collections.unmodifiableMap(new HashMap<>(builder.serverData));
            isComplete = builder.isComplete;
            availableKeys = Collections.synchronizedSet(builder.availableKeys);
        }

        State(Parcel parcel, String clazz, ParseParcelDecoder decoder) {
            className = clazz; // Already read
            objectId = parcel.readByte() == 1 ? parcel.readString() : null;
            createdAt = parcel.readLong();
            long updated = parcel.readLong();
            updatedAt = updated > 0 ? updated : createdAt;
            int size = parcel.readInt();
            HashMap<String, Object> map = new HashMap<>();
            for (int i = 0; i < size; i++) {
                String key = parcel.readString();
                Object obj = decoder.decode(parcel);
                map.put(key, obj);
            }
            serverData = Collections.unmodifiableMap(map);
            isComplete = parcel.readByte() == 1;
            List<String> available = new ArrayList<>();
            parcel.readStringList(available);
            availableKeys = new HashSet<>(available);
        }

        public static Init<?> newBuilder(String className) {
            if ("_User".equals(className)) {
                return new ParseUser.State.Builder();
            }
            return new Builder(className);
        }


        static State createFromParcel(Parcel source, ParseParcelDecoder decoder) {
            String className = source.readString();
            if ("_User".equals(className)) {
                return new ParseUser.State(source, className, decoder);
            }
            return new State(source, className, decoder);
        }

        @SuppressWarnings("unchecked")
        public <T extends Init<?>> T newBuilder() {
            return (T) new Builder(this);
        }

        public String className() {
            return className;
        }

        public String objectId() {
            return objectId;
        }

        public long createdAt() {
            return createdAt;
        }

        public long updatedAt() {
            return updatedAt;
        }

        public boolean isComplete() {
            return isComplete;
        }

        public Object get(String key) {
            return serverData.get(key);
        }

        public Set<String> keySet() {
            return serverData.keySet();
        }

        // Available keys for this object. With respect to keySet(), this includes also keys that are
        // undefined in the server, but that should be accessed without throwing.
        // These extra keys come e.g. from ParseQuery.selectKeys(). Selected keys must be available to
        // get() methods even if undefined, for consistency with complete objects.
        // For a complete object, this set is equal to keySet().
        public Set<String> availableKeys() {
            return availableKeys;
        }

        protected void writeToParcel(Parcel dest, ParseParcelEncoder encoder) {
            dest.writeString(className);
            dest.writeByte(objectId != null ? (byte) 1 : 0);
            if (objectId != null) {
                dest.writeString(objectId);
            }
            dest.writeLong(createdAt);
            dest.writeLong(updatedAt);
            dest.writeInt(serverData.size());
            Set<String> keys = serverData.keySet();
            for (String key : keys) {
                dest.writeString(key);
                encoder.encode(serverData.get(key), dest);
            }
            dest.writeByte(isComplete ? (byte) 1 : 0);
            dest.writeStringList(new ArrayList<>(availableKeys));
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "%s@%s[" +
                            "className=%s, objectId=%s, createdAt=%d, updatedAt=%d, isComplete=%s, " +
                            "serverData=%s, availableKeys=%s]",
                    getClass().getName(),
                    Integer.toHexString(hashCode()),
                    className,
                    objectId,
                    createdAt,
                    updatedAt,
                    isComplete,
                    serverData,
                    availableKeys);
        }

        static abstract class Init<T extends Init> {

            private final String className;
            Map<String, Object> serverData = new HashMap<>();
            private String objectId;
            private long createdAt = -1;
            private long updatedAt = -1;
            private boolean isComplete;
            private Set<String> availableKeys = new HashSet<>();

            public Init(String className) {
                this.className = className;
            }

            Init(State state) {
                className = state.className();
                objectId = state.objectId();
                createdAt = state.createdAt();
                updatedAt = state.updatedAt();
                availableKeys = Collections.synchronizedSet(new HashSet<>(state.availableKeys()));
                for (String key : state.keySet()) {
                    serverData.put(key, state.get(key));
                    availableKeys.add(key);
                }
                isComplete = state.isComplete();
            }


            abstract T self();


            abstract <S extends State> S build();

            public T objectId(String objectId) {
                this.objectId = objectId;
                return self();
            }

            public T createdAt(Date createdAt) {
                this.createdAt = createdAt.getTime();
                return self();
            }

            public T createdAt(long createdAt) {
                this.createdAt = createdAt;
                return self();
            }

            public T updatedAt(Date updatedAt) {
                this.updatedAt = updatedAt.getTime();
                return self();
            }

            public T updatedAt(long updatedAt) {
                this.updatedAt = updatedAt;
                return self();
            }

            public T isComplete(boolean complete) {
                isComplete = complete;
                return self();
            }

            public T put(String key, Object value) {
                serverData.put(key, value);
                availableKeys.add(key);
                return self();
            }

            public T remove(String key) {
                serverData.remove(key);
                return self();
            }

            public T availableKeys(Collection<String> keys) {
                availableKeys.addAll(keys);
                return self();
            }

            public T clear() {
                objectId = null;
                createdAt = -1;
                updatedAt = -1;
                isComplete = false;
                serverData.clear();
                availableKeys.clear();
                return self();
            }

            /**
             * Applies a {@code State} on top of this {@code Builder} instance.
             *
             * @param other The {@code State} to apply over this instance.
             * @return A new {@code Builder} instance.
             */
            public T apply(State other) {
                if (other.objectId() != null) {
                    objectId(other.objectId());
                }
                if (other.createdAt() > 0) {
                    createdAt(other.createdAt());
                }
                if (other.updatedAt() > 0) {
                    updatedAt(other.updatedAt());
                }
                isComplete(isComplete || other.isComplete());
                for (String key : other.keySet()) {
                    put(key, other.get(key));
                }
                availableKeys(other.availableKeys());
                return self();
            }

            public T apply(ParseOperationSet operations) {
                for (String key : operations.keySet()) {
                    ParseFieldOperation operation = operations.get(key);
                    Object oldValue = serverData.get(key);
                    Object newValue = operation.apply(oldValue, key);
                    if (newValue != null) {
                        put(key, newValue);
                    } else {
                        remove(key);
                    }
                }
                return self();
            }
        }

        static class Builder extends Init<Builder> {

            public Builder(String className) {
                super(className);
            }

            public Builder(State state) {
                super(state);
            }

            @Override
            Builder self() {
                return this;
            }

            public State build() {
                return new State(this);
            }
        }
    }

}

// [1] Normally we should only construct the command from state when it's our turn in the
// taskQueue so that new objects will have an updated objectId from previous saves.
// We can't do this for save/deleteEventually since this will break the promise that we'll
// try to run the command eventually, since our process might die before it's our turn in
// the taskQueue.
// This seems like this will only be a problem for new objects that are saved &
// save/deleteEventually'd at the same time, as the first will create 2 objects and the second
// the delete might fail.
