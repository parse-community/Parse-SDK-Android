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
import java.util.HashMap;

/**
 * A HashMap where all the values are weak.
 */
/** package */ class WeakValueHashMap<K, V> {
  private HashMap<K, WeakReference<V>> map;
  
  public WeakValueHashMap() {
    map = new HashMap<>();
  }

  public void put(K key, V value) {
    map.put(key, new WeakReference<>(value));
  }
  
  /**
   * Returns null if the key isn't in the map, or if it is an expired reference. If it is, then the
   * reference is removed from the map.
   */
  public V get(K key) {
    WeakReference<V> reference = map.get(key);
    if (reference == null) {
      return null;
    }
    
    V value = reference.get();
    if (value == null) {
      map.remove(key);
    }
    
    return value;
  }
  
  public void remove(K key) {
    map.remove(key);
  }
  
  public void clear() {
    map.clear();
  }
}
