/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A class that is used to access all of the children of a many-to-many relationship. Each instance
 * of Parse.Relation is associated with a particular parent object and key.
 */
public class ParseRelation<T extends ParseObject> {
  private final Object mutex = new Object();

  // The owning object of this ParseRelation.
  private WeakReference<ParseObject> parent;

  // The object Id of the parent.
  private String parentObjectId;

  // The classname of the parent to retrieve the parent ParseObject in case the parent is GC'ed.
  private String parentClassName;

  // The key of the relation in the parent object.
  private String key;

  // The className of the target objects.
  private String targetClass;
  
  // For offline caching, we keep track of every object we've known to be in the relation.
  private Set<ParseObject> knownObjects = new HashSet<>();

  /* package */ ParseRelation(ParseObject parent, String key) {
    this.parent = new WeakReference<>(parent);
    this.parentObjectId = parent.getObjectId();
    this.parentClassName = parent.getClassName();
    this.key = key;
    this.targetClass = null;
  }

  /* package */ ParseRelation(String targetClass) {
    this.parent = null;
    this.parentObjectId = null;
    this.parentClassName = null;
    this.key = null;
    this.targetClass = targetClass;
  }

  /**
   * Parses a relation from JSON.
   */
  /* package */ ParseRelation(JSONObject jsonObject, ParseDecoder decoder) {
    this.parent = null;
    this.parentObjectId = null;
    this.parentClassName = null;
    this.key = null;
    this.targetClass = jsonObject.optString("className", null);
    JSONArray objectsArray = jsonObject.optJSONArray("objects");
    if (objectsArray != null) {
      for (int i = 0; i < objectsArray.length(); ++i) {
        knownObjects.add((ParseObject)decoder.decode(objectsArray.optJSONObject(i)));
      }
    }
  }

  /* package */ void ensureParentAndKey(ParseObject someParent, String someKey) {
    synchronized (mutex) {
      if (parent == null) {
        parent = new WeakReference<>(someParent);
        parentObjectId = someParent.getObjectId();
        parentClassName = someParent.getClassName();
      }
      if (key == null) {
        key = someKey;
      }
      if (parent.get() != someParent) {
        throw new IllegalStateException(
            "Internal error. One ParseRelation retrieved from two different ParseObjects.");
      }
      if (!key.equals(someKey)) {
        throw new IllegalStateException(
            "Internal error. One ParseRelation retrieved from two different keys.");
      }
    }
  }

  /**
   * Adds an object to this relation.
   * 
   * @param object
   *          The object to add to this relation.
   */
  public void add(T object) {
    synchronized (mutex) {
      ParseRelationOperation<T> operation =
          new ParseRelationOperation<>(Collections.singleton(object), null);
      targetClass = operation.getTargetClass();
      getParent().performOperation(key, operation);
      
      knownObjects.add(object);
    }
  }

  /**
   * Removes an object from this relation.
   * 
   * @param object
   *          The object to remove from this relation.
   */
  public void remove(T object) {
    synchronized (mutex) {
      ParseRelationOperation<T> operation =
          new ParseRelationOperation<>(null, Collections.singleton(object));
      targetClass = operation.getTargetClass();
      getParent().performOperation(key, operation);
  
      knownObjects.remove(object);
    }
  }

  /**
   * Gets a query that can be used to query the objects in this relation.
   * 
   * @return A ParseQuery that restricts the results to objects in this relations.
   */
  public ParseQuery<T> getQuery() {
    synchronized (mutex) {
      ParseQuery.State.Builder<T> builder;
      if (targetClass == null) {
        builder = new ParseQuery.State.Builder<T>(parentClassName)
            .redirectClassNameForKey(key);
      } else {
        builder = new ParseQuery.State.Builder<>(targetClass);
      }
      builder.whereRelatedTo(getParent(), key);
      return new ParseQuery<>(builder);
    }
  }

  /* package */ JSONObject encodeToJSON(ParseEncoder objectEncoder) throws JSONException {
    synchronized (mutex) {
      JSONObject relation = new JSONObject();
      relation.put("__type", "Relation");
      relation.put("className", targetClass);
      JSONArray knownObjectsArray = new JSONArray();
      for (ParseObject knownObject : knownObjects) {
        try {
          knownObjectsArray.put(objectEncoder.encodeRelatedObject(knownObject));
        } catch (Exception e) {
          // This is just for caching, so if an object can't be encoded for any reason, drop it.
        }
      }
      relation.put("objects", knownObjectsArray);
      return relation;
    }
  }

  /* package */ String getTargetClass() {
    synchronized (mutex) {
      return targetClass;
    }
  }

  /* package */ void setTargetClass(String className) {
    synchronized (mutex) {
      targetClass = className;
    }
  }
  
  /**
   * Adds an object that is known to be in the relation. This is used for offline caching.
   */
  /* package */ void addKnownObject(ParseObject object) {
    synchronized (mutex) {
      knownObjects.add(object);
    }
  }
  
  /**
   * Removes an object that is known to not be in the relation. This is used for offline caching.
   */
  /* package */ void removeKnownObject(ParseObject object) {
    synchronized (mutex) {
      knownObjects.remove(object);
    }
  }
  
  /**
   * Returns true iff this object was ever known to be in the relation. This is used for offline
   * caching.
   */
  /* package */ boolean hasKnownObject(ParseObject object) {
    synchronized (mutex) {
      return knownObjects.contains(object);
    }
  }

  /* package for tests */ ParseObject getParent() {
    if(parent == null){
      return null;
    }
    if(parent.get() == null){
      return ParseObject.createWithoutData(parentClassName, parentObjectId);
    }
    return parent.get();
  }

  /* package for tests */ String getKey() {
    return key;
  }

  /* package for tests */ Set<ParseObject> getKnownObjects() {
    return knownObjects;
  }
}
