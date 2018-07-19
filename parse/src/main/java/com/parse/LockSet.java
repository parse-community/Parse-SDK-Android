/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;

class LockSet {
    private static final WeakHashMap<Lock, Long> stableIds = new WeakHashMap<>();
    private static long nextStableId = 0L;

    private final Set<Lock> locks;

    public LockSet(Collection<Lock> locks) {
        this.locks = new TreeSet<>(new Comparator<Lock>() {
            @Override
            public int compare(Lock lhs, Lock rhs) {
                Long lhsId = getStableId(lhs);
                Long rhsId = getStableId(rhs);
                return lhsId.compareTo(rhsId);
            }
        });
        this.locks.addAll(locks);
    }

    private static Long getStableId(Lock lock) {
        synchronized (stableIds) {
            if (stableIds.containsKey(lock)) {
                return stableIds.get(lock);
            }
            long id = nextStableId++;
            stableIds.put(lock, id);
            return id;
        }
    }

    public void lock() {
        for (Lock l : locks) {
            l.lock();
        }
    }

    public void unlock() {
        for (Lock l : locks) {
            l.unlock();
        }
    }
}
