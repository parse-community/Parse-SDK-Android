/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.os.Parcel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * An operation where a ParseRelation's value is modified.
 */
class ParseRelationOperation<T extends ParseObject> implements ParseFieldOperation {
    /* package */ final static String OP_NAME_ADD = "AddRelation";
    /* package */ final static String OP_NAME_REMOVE = "RemoveRelation";
    /* package */ final static String OP_NAME_BATCH = "Batch";

    // The className of the target objects.
    private final String targetClass;

    // A set of objects to add to this relation.
    private final Set<ParseObject> relationsToAdd;
    // A set of objects to remove from this relation.
    private final Set<ParseObject> relationsToRemove;

    ParseRelationOperation(Set<T> newRelationsToAdd, Set<T> newRelationsToRemove) {
        String targetClass = null;
        relationsToAdd = new HashSet<>();
        relationsToRemove = new HashSet<>();

        if (newRelationsToAdd != null) {
            for (T object : newRelationsToAdd) {
                addParseObjectToSet(object, relationsToAdd);

                if (targetClass == null) {
                    targetClass = object.getClassName();
                } else {
                    if (!targetClass.equals(object.getClassName())) {
                        throw new IllegalArgumentException(
                                "All objects in a relation must be of the same class.");
                    }
                }
            }
        }

        if (newRelationsToRemove != null) {
            for (T object : newRelationsToRemove) {
                addParseObjectToSet(object, relationsToRemove);

                if (targetClass == null) {
                    targetClass = object.getClassName();
                } else {
                    if (!targetClass.equals(object.getClassName())) {
                        throw new IllegalArgumentException(
                                "All objects in a relation must be of the same class.");
                    }
                }
            }
        }

        if (targetClass == null) {
            throw new IllegalArgumentException("Cannot create a ParseRelationOperation with no objects.");
        }
        this.targetClass = targetClass;
    }

    private ParseRelationOperation(String newTargetClass, Set<ParseObject> newRelationsToAdd,
                                   Set<ParseObject> newRelationsToRemove) {
        targetClass = newTargetClass;
        relationsToAdd = new HashSet<>(newRelationsToAdd);
        relationsToRemove = new HashSet<>(newRelationsToRemove);
    }

    /*
     * Adds a ParseObject to a set, replacing any existing instance of the same object.
     */
    private void addParseObjectToSet(ParseObject obj, Set<ParseObject> set) {
        if (Parse.getLocalDatastore() != null || obj.getObjectId() == null) {
            // There's no way there could be duplicate instances.
            set.add(obj);
            return;
        }

        // We have to do this the hard way.
        for (ParseObject existingObject : set) {
            if (obj.getObjectId().equals(existingObject.getObjectId())) {
                set.remove(existingObject);
            }
        }
        set.add(obj);
    }

    /*
     * Adds a list of ParseObject to a set, replacing any existing instance of the same object.
     */
    private void addAllParseObjectsToSet(Collection<ParseObject> list, Set<ParseObject> set) {
        for (ParseObject obj : list) {
            addParseObjectToSet(obj, set);
        }
    }

    /*
     * Removes an object (and any duplicate instances of that object) from the set.
     */
    private void removeParseObjectFromSet(ParseObject obj, Set<ParseObject> set) {
        if (Parse.getLocalDatastore() != null || obj.getObjectId() == null) {
            // There's no way there could be duplicate instances.
            set.remove(obj);
            return;
        }

        // We have to do this the hard way.
        for (ParseObject existingObject : set) {
            if (obj.getObjectId().equals(existingObject.getObjectId())) {
                set.remove(existingObject);
            }
        }
    }

    /*
     * Removes all objects (and any duplicate instances of those objects) from the set.
     */
    private void removeAllParseObjectsFromSet(Collection<ParseObject> list, Set<ParseObject> set) {
        for (ParseObject obj : list) {
            removeParseObjectFromSet(obj, set);
        }
    }

    String getTargetClass() {
        return targetClass;
    }

    /*
     * Converts a set of objects into a JSONArray of Parse pointers.
     */
    JSONArray convertSetToArray(Set<ParseObject> set, ParseEncoder objectEncoder) {
        JSONArray array = new JSONArray();
        for (ParseObject obj : set) {
            array.put(objectEncoder.encode(obj));
        }
        return array;
    }

    // Encodes any add/removes ops to JSON to send to the server.
    @Override
    public JSONObject encode(ParseEncoder objectEncoder) throws JSONException {
        JSONObject adds = null;
        JSONObject removes = null;

        if (relationsToAdd.size() > 0) {
            adds = new JSONObject();
            adds.put("__op", OP_NAME_ADD);
            adds.put("objects", convertSetToArray(relationsToAdd, objectEncoder));
        }

        if (relationsToRemove.size() > 0) {
            removes = new JSONObject();
            removes.put("__op", OP_NAME_REMOVE);
            removes.put("objects", convertSetToArray(relationsToRemove, objectEncoder));
        }

        if (adds != null && removes != null) {
            JSONObject result = new JSONObject();
            result.put("__op", OP_NAME_BATCH);
            JSONArray ops = new JSONArray();
            ops.put(adds);
            ops.put(removes);
            result.put("ops", ops);
            return result;
        }

        if (adds != null) {
            return adds;
        }

        if (removes != null) {
            return removes;
        }

        throw new IllegalArgumentException("A ParseRelationOperation was created without any data.");
    }

    @Override
    public void encode(Parcel dest, ParseParcelEncoder parcelableEncoder) {
        if (relationsToAdd.isEmpty() && relationsToRemove.isEmpty()) {
            throw new IllegalArgumentException("A ParseRelationOperation was created without any data.");
        }
        if (relationsToAdd.size() > 0 && relationsToRemove.size() > 0) {
            dest.writeString(OP_NAME_BATCH);
        }
        if (relationsToAdd.size() > 0) {
            dest.writeString(OP_NAME_ADD);
            dest.writeInt(relationsToAdd.size());
            for (ParseObject object : relationsToAdd) {
                parcelableEncoder.encode(object, dest);
            }
        }
        if (relationsToRemove.size() > 0) {
            dest.writeString(OP_NAME_REMOVE);
            dest.writeInt(relationsToRemove.size());
            for (ParseObject object : relationsToRemove) {
                parcelableEncoder.encode(object, dest);
            }
        }
    }

    @Override
    public ParseFieldOperation mergeWithPrevious(ParseFieldOperation previous) {
        if (previous == null) {
            return this;

        } else if (previous instanceof ParseDeleteOperation) {
            throw new IllegalArgumentException("You can't modify a relation after deleting it.");

        } else if (previous instanceof ParseRelationOperation) {
            @SuppressWarnings("unchecked")
            ParseRelationOperation<T> previousOperation = (ParseRelationOperation<T>) previous;

            if (previousOperation.targetClass != null
                    && !previousOperation.targetClass.equals(targetClass)) {
                throw new IllegalArgumentException("Related object object must be of class "
                        + previousOperation.targetClass + ", but " + targetClass + " was passed in.");
            }

            Set<ParseObject> newRelationsToAdd = new HashSet<>(previousOperation.relationsToAdd);
            Set<ParseObject> newRelationsToRemove = new HashSet<>(previousOperation.relationsToRemove);
            if (relationsToAdd != null) {
                addAllParseObjectsToSet(relationsToAdd, newRelationsToAdd);
                removeAllParseObjectsFromSet(relationsToAdd, newRelationsToRemove);
            }
            if (relationsToRemove != null) {
                removeAllParseObjectsFromSet(relationsToRemove, newRelationsToAdd);
                addAllParseObjectsToSet(relationsToRemove, newRelationsToRemove);
            }
            return new ParseRelationOperation<T>(targetClass, newRelationsToAdd, newRelationsToRemove);

        } else {
            throw new IllegalArgumentException("Operation is invalid after previous operation.");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object apply(Object oldValue, String key) {
        ParseRelation<T> relation;

        if (oldValue == null) {
            relation = new ParseRelation<>(targetClass);

        } else if (oldValue instanceof ParseRelation) {
            relation = (ParseRelation<T>) oldValue;
            if (targetClass != null && !targetClass.equals(relation.getTargetClass())) {
                throw new IllegalArgumentException("Related object object must be of class "
                        + relation.getTargetClass() + ", but " + targetClass + " was passed in.");
            }
        } else {
            throw new IllegalArgumentException("Operation is invalid after previous operation.");
        }

        for (ParseObject relationToAdd : relationsToAdd) {
            relation.addKnownObject(relationToAdd);
        }
        for (ParseObject relationToRemove : relationsToRemove) {
            relation.removeKnownObject(relationToRemove);
        }
        return relation;
    }
}
