/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import java.lang.ref.WeakReference
import java.util.*

/**
 * A HashMap where all the values are weak.
 */
internal class WeakValueHashMap<K, V> {
    private val map: HashMap<K, WeakReference<V>> = HashMap()

    fun put(key: K, value: V) {
        map[key] = WeakReference(value)
    }

    /**
     * Returns null if the key isn't in the map, or if it is an expired reference. If it is, then the
     * reference is removed from the map.
     */
    operator fun get(key: K): V? {
        val reference = map[key] ?: return null
        val value = reference.get()
        if (value == null) {
            map.remove(key)
        }
        return value
    }

    fun remove(key: K) {
        map.remove(key)
    }

    fun clear() {
        map.clear()
    }

}