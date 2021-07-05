/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import java.util.*
import java.util.concurrent.locks.Lock

internal class LockSet(locks: Collection<Lock>?) {
    private val locks: MutableSet<Lock>
    fun lock() {
        for (l in locks) {
            l.lock()
        }
    }

    fun unlock() {
        for (l in locks) {
            l.unlock()
        }
    }

    companion object {
        private val stableIds = WeakHashMap<Lock, Long>()
        private var nextStableId = 0L
        private fun getStableId(lock: Lock): Long? {
            synchronized(stableIds) {
                if (stableIds.containsKey(lock)) {
                    return stableIds[lock]
                }
                val id = nextStableId++
                stableIds[lock] = id
                return id
            }
        }
    }

    init {
        this.locks = TreeSet { lhs: Lock, rhs: Lock ->
            val lhsId = getStableId(lhs)
            val rhsId = getStableId(rhs)
            lhsId!!.compareTo(rhsId!!)
        }
        this.locks.addAll(locks!!)
    }
}