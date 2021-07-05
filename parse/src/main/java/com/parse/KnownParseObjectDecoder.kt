/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

/**
 * A subclass of `ParseDecoder` which can keep `ParseObject` that
 * has been fetched instead of creating a new instance.
 */
internal class KnownParseObjectDecoder(private val fetchedObjects: Map<String?, ParseObject>?) :
    ParseDecoder() {
    /**
     * If the object has been fetched, the fetched object will be returned. Otherwise a
     * new created object will be returned.
     */
    override fun decodePointer(className: String?, objectId: String?): ParseObject? {
        return if (fetchedObjects != null && fetchedObjects.containsKey(objectId)) {
            fetchedObjects[objectId]
        } else super.decodePointer(className, objectId)
    }
}