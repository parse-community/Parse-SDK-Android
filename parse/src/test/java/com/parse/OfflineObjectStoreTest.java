/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.Collections;

import bolts.Task;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OfflineObjectStoreTest {

    private static final String PIN_NAME = "test";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() {
        ParseObject.registerSubclass(ParseUser.class);
    }

    @After
    public void tearDown() {
        ParseObject.unregisterSubclass(ParseUser.class);
        Parse.setLocalDatastore(null);
        ParseCorePlugins.getInstance().reset();
    }

    @Test
    public void testSetAsync() throws Exception {
        OfflineStore lds = mock(OfflineStore.class);
        when(lds.unpinAllObjectsAsync(anyString())).thenReturn(Task.<Void>forResult(null));
        when(lds.pinAllObjectsAsync(anyString(), anyList(), anyBoolean()))
                .thenReturn(Task.forResult(null));
        Parse.setLocalDatastore(lds);

        OfflineObjectStore<ParseUser> store =
                new OfflineObjectStore<>(ParseUser.class, PIN_NAME, null);

        ParseUser user = mock(ParseUser.class);
        ParseTaskUtils.wait(store.setAsync(user));

        verify(lds, times(1)).unpinAllObjectsAsync(PIN_NAME);
        verify(user, times(1)).pinInBackground(PIN_NAME, false);
    }

    //region getAsync

    @Test
    public void testGetAsyncFromLDS() throws Exception {
        Parse.enableLocalDatastore(null);
        ParseUser user = mock(ParseUser.class);
        ParseQueryController queryController = mock(ParseQueryController.class);
        //noinspection unchecked
        when(queryController.findAsync(
                any(ParseQuery.State.class),
                any(ParseUser.class),
                any(Task.class))
        ).thenReturn(Task.forResult(Collections.singletonList(user)));
        ParseCorePlugins.getInstance().registerQueryController(queryController);

        OfflineObjectStore<ParseUser> store =
                new OfflineObjectStore<>(ParseUser.class, PIN_NAME, null);

        ParseUser userAgain = ParseTaskUtils.wait(store.getAsync());
        //noinspection unchecked
        verify(queryController, times(1))
                .findAsync(any(ParseQuery.State.class), any(ParseUser.class), any(Task.class));
        assertSame(user, userAgain);
    }

    @Test
    public void testGetAsyncFromLDSWithTooManyObjects() throws Exception {
        Parse.enableLocalDatastore(null);
        ParseQueryController queryController = mock(ParseQueryController.class);
        //noinspection unchecked
        when(queryController.findAsync(
                any(ParseQuery.State.class),
                any(ParseUser.class),
                any(Task.class))
        ).thenReturn(Task.forResult(Arrays.asList(mock(ParseUser.class), mock(ParseUser.class))));
        ParseCorePlugins.getInstance().registerQueryController(queryController);
        OfflineStore lds = mock(OfflineStore.class);
        when(lds.unpinAllObjectsAsync(anyString())).thenReturn(Task.<Void>forResult(null));
        Parse.setLocalDatastore(lds);

        @SuppressWarnings("unchecked")
        ParseObjectStore<ParseUser> legacy = mock(ParseObjectStore.class);
        when(legacy.getAsync()).thenReturn(Task.<ParseUser>forResult(null));

        OfflineObjectStore<ParseUser> store =
                new OfflineObjectStore<>(ParseUser.class, PIN_NAME, legacy);

        ParseUser user = ParseTaskUtils.wait(store.getAsync());
        //noinspection unchecked
        verify(queryController, times(1))
                .findAsync(any(ParseQuery.State.class), any(ParseUser.class), any(Task.class));
        verify(lds, times(1)).unpinAllObjectsAsync(PIN_NAME);
        assertNull(user);
    }

    @Test
    public void testGetAsyncMigrate() throws Exception {
        Parse.enableLocalDatastore(null);
        ParseQueryController queryController = mock(ParseQueryController.class);
        //noinspection unchecked
        when(queryController.findAsync(
                any(ParseQuery.State.class),
                any(ParseUser.class),
                any(Task.class))
        ).thenReturn(Task.forResult(null));
        ParseCorePlugins.getInstance().registerQueryController(queryController);
        OfflineStore lds = mock(OfflineStore.class);
        when(lds.pinAllObjectsAsync(anyString(), anyList(), anyBoolean()))
                .thenReturn(Task.forResult(null));
        when(lds.unpinAllObjectsAsync(anyString())).thenReturn(Task.<Void>forResult(null));
        when(lds.pinAllObjectsAsync(anyString(), anyList(), anyBoolean()))
                .thenReturn(Task.<Void>forResult(null));
        Parse.setLocalDatastore(lds);

        ParseUser user = mock(ParseUser.class);
        when(user.pinInBackground(anyString(), anyBoolean())).thenReturn(Task.<Void>forResult(null));
        @SuppressWarnings("unchecked")
        ParseObjectStore<ParseUser> legacy = mock(ParseObjectStore.class);
        when(legacy.getAsync()).thenReturn(Task.forResult(user));
        when(legacy.deleteAsync()).thenReturn(Task.<Void>forResult(null));

        OfflineObjectStore<ParseUser> store =
                new OfflineObjectStore<>(ParseUser.class, PIN_NAME, legacy);

        ParseUser userAgain = ParseTaskUtils.wait(store.getAsync());
        //noinspection unchecked
        verify(queryController, times(1))
                .findAsync(any(ParseQuery.State.class), any(ParseUser.class), any(Task.class));
        verify(legacy, times(1)).getAsync();
        verify(legacy, times(1)).deleteAsync();
        verify(lds, times(1)).unpinAllObjectsAsync(PIN_NAME);
        verify(user, times(1)).pinInBackground(PIN_NAME, false);
        assertNotNull(userAgain);
        assertSame(user, userAgain);
    }

    //endregion

    //region existsAsync

    @Test
    public void testExistsAsyncLDS() throws Exception {
        Parse.enableLocalDatastore(null);
        ParseQueryController queryController = mock(ParseQueryController.class);
        //noinspection unchecked
        when(queryController.countAsync(
                any(ParseQuery.State.class),
                any(ParseUser.class),
                any(Task.class))
        ).thenReturn(Task.forResult(1));
        ParseCorePlugins.getInstance().registerQueryController(queryController);

        OfflineObjectStore<ParseUser> store =
                new OfflineObjectStore<>(ParseUser.class, PIN_NAME, null);
        assertTrue(ParseTaskUtils.wait(store.existsAsync()));
        //noinspection unchecked
        verify(queryController, times(1)).countAsync(
                any(ParseQuery.State.class), any(ParseUser.class), any(Task.class));
    }

    @Test
    public void testExistsAsyncLegacy() throws Exception {
        Parse.enableLocalDatastore(null);
        ParseQueryController queryController = mock(ParseQueryController.class);
        //noinspection unchecked
        when(queryController.countAsync(
                any(ParseQuery.State.class),
                any(ParseUser.class),
                any(Task.class))
        ).thenReturn(Task.forResult(0));
        ParseCorePlugins.getInstance().registerQueryController(queryController);

        @SuppressWarnings("unchecked")
        ParseObjectStore<ParseUser> legacy = mock(ParseObjectStore.class);
        when(legacy.existsAsync()).thenReturn(Task.forResult(true));

        OfflineObjectStore<ParseUser> store =
                new OfflineObjectStore<>(ParseUser.class, PIN_NAME, legacy);

        assertTrue(ParseTaskUtils.wait(store.existsAsync()));
        //noinspection unchecked
        verify(queryController, times(1)).countAsync(
                any(ParseQuery.State.class), any(ParseUser.class), any(Task.class));
    }

    //endregion

    //region deleteAsync

    @Test
    public void testDeleteAsync() throws Exception {
        OfflineStore lds = mock(OfflineStore.class);
        when(lds.unpinAllObjectsAsync(anyString())).thenReturn(Task.<Void>forResult(null));
        Parse.setLocalDatastore(lds);

        @SuppressWarnings("unchecked")
        ParseObjectStore<ParseUser> legacy = mock(ParseObjectStore.class);
        when(legacy.deleteAsync()).thenReturn(Task.<Void>forResult(null));

        OfflineObjectStore<ParseUser> store =
                new OfflineObjectStore<>(ParseUser.class, PIN_NAME, legacy);

        ParseTaskUtils.wait(store.deleteAsync());
        verify(legacy, times(1)).deleteAsync();
        verify(lds, times(1)).unpinAllObjectsAsync(PIN_NAME);
    }

    @Test
    public void testDeleteAsyncFailure() throws Exception {
        OfflineStore lds = mock(OfflineStore.class);
        when(lds.unpinAllObjectsAsync(anyString()))
                .thenReturn(Task.<Void>forError(new RuntimeException("failure")));
        Parse.setLocalDatastore(lds);

        @SuppressWarnings("unchecked")
        ParseObjectStore<ParseUser> legacy = mock(ParseObjectStore.class);
        when(legacy.deleteAsync()).thenReturn(Task.<Void>forResult(null));

        OfflineObjectStore<ParseUser> store =
                new OfflineObjectStore<>(ParseUser.class, PIN_NAME, legacy);

        thrown.expect(RuntimeException.class);
        ParseTaskUtils.wait(store.deleteAsync());
        verify(legacy, times(1)).deleteAsync();
    }

    //endregion
}

