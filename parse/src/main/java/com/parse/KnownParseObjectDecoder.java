/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.Map;

/**
 * A subclass of <code>ParseDecoder</code> which can keep <code>ParseObject</code> that
 * has been fetched instead of creating a new instance.
 */
/** package */ class KnownParseObjectDecoder extends ParseDecoder {
  private Map<String, ParseObject> fetchedObjects;

  public KnownParseObjectDecoder(Map<String, ParseObject> fetchedObjects) {
    super();
    this.fetchedObjects = fetchedObjects;
  }

  /**
   * If the object has been fetched, the fetched object will be returned. Otherwise a
   * new created object will be returned.
   */
  @Override
  protected ParseObject decodePointer(String className, String objectId) {
    if (fetchedObjects != null && fetchedObjects.containsKey(objectId)) {
      return fetchedObjects.get(objectId);
    }
    return super.decodePointer(className, objectId); 
  }
}
