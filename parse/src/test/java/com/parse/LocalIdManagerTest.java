/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class LocalIdManagerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testLocalIdManager() throws Exception {
        LocalIdManager manager = new LocalIdManager(temporaryFolder.newFolder("test"));
        manager.clear();

        String localId1 = manager.createLocalId();
        assertNotNull(localId1);
        manager.retainLocalIdOnDisk(localId1);  // refcount = 1
        assertNull(manager.getObjectId(localId1));

        String localId2 = manager.createLocalId();
        assertNotNull(localId2);
        manager.retainLocalIdOnDisk(localId2);  // refcount = 1
        assertNull(manager.getObjectId(localId2));

        manager.retainLocalIdOnDisk(localId1);  // refcount = 2
        assertNull(manager.getObjectId(localId1));
        assertNull(manager.getObjectId(localId2));

        manager.releaseLocalIdOnDisk(localId1);  // refcount = 1
        assertNull(manager.getObjectId(localId1));
        assertNull(manager.getObjectId(localId2));

        String objectId1 = "objectId1";
        manager.setObjectId(localId1, objectId1);
        assertEquals(objectId1, manager.getObjectId(localId1));
        assertNull(manager.getObjectId(localId2));

        manager.retainLocalIdOnDisk(localId1);  // refcount = 2
        assertEquals(objectId1, manager.getObjectId(localId1));
        assertNull(manager.getObjectId(localId2));

        String objectId2 = "objectId2";
        manager.setObjectId(localId2, objectId2);
        assertEquals(objectId1, manager.getObjectId(localId1));
        assertEquals(objectId2, manager.getObjectId(localId2));

        manager.releaseLocalIdOnDisk(localId1);  // refcount = 1
        assertEquals(objectId1, manager.getObjectId(localId1));
        assertEquals(objectId2, manager.getObjectId(localId2));

        manager.releaseLocalIdOnDisk(localId1);  // refcount = 0
        assertNull(manager.getObjectId(localId1));
        assertEquals(objectId2, manager.getObjectId(localId2));

        manager.releaseLocalIdOnDisk(localId2);  // refcount = 0
        assertNull(manager.getObjectId(localId1));
        assertNull(manager.getObjectId(localId2));

        assertFalse(manager.clear());
    }

    @Test
    public void testLongSerialization() throws Exception {
        long expected = 0x8000000000000000L;
        JSONObject object = new JSONObject();
        object.put("hugeNumber", expected);
        String json = object.toString();

        object = new JSONObject(json);
        long actual = object.getLong("hugeNumber");
        assertEquals(expected, actual);
    }
}
