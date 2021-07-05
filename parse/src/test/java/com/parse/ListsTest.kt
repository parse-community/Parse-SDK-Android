/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import org.junit.Assert
import org.junit.Test
import java.util.*

class ListsTest {
    @Test
    fun testPartition() {
        val list: MutableList<Int> = ArrayList()
        for (i in 0..98) {
            list.add(i)
        }
        val partitions = list.chunked(5)
        Assert.assertEquals(20, partitions.size.toLong())
        var count = 0
        for (i in 0..18) {
            val partition = partitions[i]
            Assert.assertEquals(5, partition.size.toLong())
            for (j in partition) {
                Assert.assertEquals(count.toLong(), j.toLong())
                count += 1
            }
        }
        Assert.assertEquals(4, partitions[19].size.toLong())
        for (i in 0..3) {
            Assert.assertEquals((95 + i).toLong(), partitions[19][i].toLong())
        }
    }
}