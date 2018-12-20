/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ListsTest {

    @Test
    public void testPartition() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 99; i++) {
            list.add(i);
        }
        List<List<Integer>> partitions = Lists.partition(list, 5);
        assertEquals(20, partitions.size());

        int count = 0;
        for (int i = 0; i < 19; i++) {
            List<Integer> partition = partitions.get(i);
            assertEquals(5, partition.size());
            for (int j : partition) {
                assertEquals(count, j);
                count += 1;
            }
        }
        assertEquals(4, partitions.get(19).size());
        for (int i = 0; i < 4; i++) {
            assertEquals(95 + i, partitions.get(19).get(i).intValue());
        }
    }
}
