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
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import com.parse.boltsinternal.Task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class CachedCurrentUserControllerTest extends ResetPluginsParseTest {

    private static final String KEY_AUTH_DATA = "authData";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        ParseObject.registerSubclass(ParseUser.class);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        ParseObject.unregisterSubclass(ParseUser.class);
    }

    //region testSetAsync

    @Test
    public void testSetAsyncWithOldInMemoryCurrentUser() throws Exception {
        // Mock currentUser in memory
        ParseUser oldCurrentUser = mock(ParseUser.class);
        when(oldCurrentUser.logOutAsync(anyBoolean())).thenReturn(Task.<Void>forResult(null));

        ParseUser.State state = new ParseUser.State.Builder()
                .put("key", "value")
                .build();
        ParseUser currentUser = ParseObject.from(state);
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.setAsync(currentUser)).thenReturn(Task.<Void>forResult(null));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);
        controller.currentUser = oldCurrentUser;

        ParseTaskUtils.wait(controller.setAsync(currentUser));

        // Make sure oldCurrentUser logout
        verify(oldCurrentUser, times(1)).logOutAsync(false);
        // Verify it was persisted
        verify(store, times(1)).setAsync(currentUser);
        // TODO(mengyan): Find a way to verify user.synchronizeAllAuthData() is called
        // Verify newUser is currentUser
        assertTrue(currentUser.isCurrentUser());
        // Make sure in memory currentUser is up to date
        assertSame(currentUser, controller.currentUser);
        assertTrue(controller.currentUserMatchesDisk);
    }

    @Test
    public void testSetAsyncWithNoInMemoryCurrentUser() throws Exception {
        ParseUser.State state = new ParseUser.State.Builder()
                .put("key", "value")
                .build();
        ParseUser currentUser = ParseObject.from(state);
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.setAsync(currentUser)).thenReturn(Task.<Void>forResult(null));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);

        ParseTaskUtils.wait(controller.setAsync(currentUser));

        // Verify it was persisted
        verify(store, times(1)).setAsync(currentUser);
        // TODO(mengyan): Find a way to verify user.synchronizeAllAuthData() is called
        // Verify newUser is currentUser
        assertTrue(currentUser.isCurrentUser());
        // Make sure in memory currentUser is up to date
        assertSame(currentUser, controller.currentUser);
        assertTrue(controller.currentUserMatchesDisk);
    }

    @Test
    public void testSetAsyncWithPersistFailure() throws Exception {
        // Mock currentUser in memory
        ParseUser oldCurrentUser = mock(ParseUser.class);
        when(oldCurrentUser.logOutAsync(anyBoolean())).thenReturn(Task.<Void>forResult(null));

        ParseUser currentUser = new ParseUser();
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.setAsync(currentUser))
                .thenReturn(Task.<Void>forError(new RuntimeException("failure")));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);
        controller.currentUser = oldCurrentUser;

        ParseTaskUtils.wait(controller.setAsync(currentUser));

        // Make sure oldCurrentUser logout
        verify(oldCurrentUser, times(1)).logOutAsync(false);
        // Verify we tried to persist
        verify(store, times(1)).setAsync(currentUser);
        // TODO(mengyan): Find a way to verify user.synchronizeAllAuthData() is called
        // Verify newUser is currentUser
        assertTrue(currentUser.isCurrentUser());
        // Make sure in memory currentUser is up to date
        assertSame(currentUser, controller.currentUser);
        // Make sure in currentUserMatchesDisk since we can not write to disk
        assertFalse(controller.currentUserMatchesDisk);
    }

    //endregion

    //region testGetAsync

    @Test
    public void testGetAsyncWithInMemoryCurrentUserSet() throws Exception {
        ParseUser currentUserInMemory = new ParseUser();

        CachedCurrentUserController controller =
                new CachedCurrentUserController(null);
        controller.currentUser = currentUserInMemory;

        ParseUser currentUser = ParseTaskUtils.wait(controller.getAsync(false));

        assertSame(currentUserInMemory, currentUser);
    }

    @Test
    public void testGetAsyncWithNoInMemoryCurrentUserAndLazyLogin() throws Exception {
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.getAsync()).thenReturn(Task.<ParseUser>forResult(null));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);
        ParseCorePlugins.getInstance().registerCurrentUserController(controller);
        // CurrentUser is null but currentUserMatchesDisk is true happens when a user logout
        controller.currentUserMatchesDisk = true;

        ParseUser currentUser = ParseTaskUtils.wait(controller.getAsync(true));

        // We need to make sure the user is created by lazy login
        assertTrue(currentUser.isLazy());
        assertTrue(currentUser.isCurrentUser());
        assertSame(controller.currentUser, currentUser);
        assertFalse(controller.currentUserMatchesDisk);
        // We do not test the lazy login auth data here, it is covered in lazyLogin() unit test
    }

    @Test
    public void testGetAsyncWithNoInMemoryAndInDiskCurrentUserAndNoLazyLogin()
            throws Exception {
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.getAsync()).thenReturn(Task.<ParseUser>forResult(null));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);
        // CurrentUser is null but currentUserMatchesDisk is true happens when a user logout
        controller.currentUserMatchesDisk = true;

        ParseUser currentUser = ParseTaskUtils.wait(controller.getAsync(false));

        assertNull(currentUser);
    }

    @Test
    public void testGetAsyncWithCurrentUserReadFromDiskSuccess() throws Exception {
        ParseUser.State state = new ParseUser.State.Builder()
                .put("key", "value")
                .build();
        ParseUser currentUserInDisk = ParseObject.from(state);
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.getAsync()).thenReturn(Task.forResult(currentUserInDisk));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);

        ParseUser currentUser = ParseTaskUtils.wait(controller.getAsync(false));

        assertSame(currentUser, currentUserInDisk);
        assertSame(currentUser, controller.currentUser);
        assertTrue(controller.currentUserMatchesDisk);
        assertTrue(currentUser.isCurrentUser());
        assertEquals("value", currentUser.get("key"));
    }

    @Test
    public void testGetAsyncAnonymousUser() throws Exception {
        ParseUser.State state = new ParseUser.State.Builder()
                .objectId("fake")
                .putAuthData(ParseAnonymousUtils.AUTH_TYPE, new HashMap<String, String>())
                .build();
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.getAsync()).thenReturn(Task.forResult(ParseObject.<ParseUser>from(state)));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);

        ParseUser user = ParseTaskUtils.wait(controller.getAsync(false));
        assertFalse(user.isLazy());
    }

    @Test
    public void testGetAsyncLazyAnonymousUser() throws Exception {
        ParseUser.State state = new ParseUser.State.Builder()
                .putAuthData(ParseAnonymousUtils.AUTH_TYPE, new HashMap<String, String>())
                .build();
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.getAsync()).thenReturn(Task.forResult(ParseObject.<ParseUser>from(state)));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);

        ParseUser user = ParseTaskUtils.wait(controller.getAsync(false));
        assertTrue(user.isLazy());
    }

    @Test
    public void testGetAsyncWithCurrentUserReadFromDiskFailure() throws Exception {
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.getAsync()).thenReturn(Task.<ParseUser>forError(new RuntimeException("failure")));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);

        ParseUser currentUser = ParseTaskUtils.wait(controller.getAsync(false));

        assertNull(currentUser);
    }

    @Test
    public void testGetAsyncWithCurrentUserReadFromDiskFailureAndLazyLogin() throws Exception {
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.getAsync()).thenReturn(Task.<ParseUser>forError(new RuntimeException("failure")));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);

        ParseUser currentUser = ParseTaskUtils.wait(controller.getAsync(true));

        // We need to make sure the user is created by lazy login
        assertTrue(currentUser.isLazy());
        assertTrue(currentUser.isCurrentUser());
        assertSame(controller.currentUser, currentUser);
        assertFalse(controller.currentUserMatchesDisk);
        // We do not test the lazy login auth data here, it is covered in lazyLogin() unit test
    }

    //endregion

    //region testLogoOutAsync

    @Test
    public void testLogOutAsyncWithDeleteInDiskCurrentUserSuccess() throws Exception {
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.deleteAsync()).thenReturn(Task.<Void>forResult(null));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);
        // We set the currentUser to make sure getAsync() return a mock user
        ParseUser currentUser = mock(ParseUser.class);
        when(currentUser.logOutAsync()).thenReturn(Task.<Void>forResult(null));
        controller.currentUser = currentUser;

        ParseTaskUtils.wait(controller.logOutAsync());

        // Make sure currentUser.logout() is called
        verify(currentUser, times(1)).logOutAsync();
        // Make sure in disk currentUser is deleted
        verify(store, times(1)).deleteAsync();
        // Make sure controller state is correct
        assertNull(controller.currentUser);
        assertTrue(controller.currentUserMatchesDisk);
    }

    @Test
    public void testLogOutAsyncWithDeleteInDiskCurrentUserFailure() throws Exception {
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.getAsync()).thenReturn(Task.<ParseUser>forResult(null));
        when(store.deleteAsync()).thenReturn(Task.<Void>forError(new RuntimeException("failure")));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);

        ParseTaskUtils.wait(controller.logOutAsync());

        // Make sure controller state is correct
        assertNull(controller.currentUser);
        assertFalse(controller.currentUserMatchesDisk);
    }

    //endregion

    //region testLazyLogin

    @Test
    public void testLazyLogin() {
        CachedCurrentUserController controller =
                new CachedCurrentUserController(null);

        String authType = ParseAnonymousUtils.AUTH_TYPE;
        Map<String, String> authData = new HashMap<>();
        authData.put("sessionToken", "testSessionToken");

        ParseUser user = controller.lazyLogIn(authType, authData);

        // Make sure use is generated through lazyLogin
        assertTrue(user.isLazy());
        assertTrue(user.isCurrentUser());
        Map<String, Map<String, String>> authPair = user.getMap(KEY_AUTH_DATA);
        assertEquals(1, authPair.size());
        Map<String, String> authDataAgain = authPair.get(authType);
        assertEquals(1, authDataAgain.size());
        assertEquals("testSessionToken", authDataAgain.get("sessionToken"));
        // Make sure controller state is correct
        assertSame(user, controller.currentUser);
        assertFalse(controller.currentUserMatchesDisk);
    }

    //endregion

    //region testGetCurrentSessionTokenAsync

    @Test
    public void testGetCurrentSessionTokenAsyncWithCurrentUserSet() throws Exception {
        CachedCurrentUserController controller =
                new CachedCurrentUserController(null);

        // We set the currentUser to make sure getAsync() return a mock user
        ParseUser currentUser = mock(ParseUser.class);
        when(currentUser.getSessionToken()).thenReturn("sessionToken");
        controller.currentUser = currentUser;

        String sessionToken = ParseTaskUtils.wait(controller.getCurrentSessionTokenAsync());

        assertEquals("sessionToken", sessionToken);
    }

    @Test
    public void testGetCurrentSessionTokenAsyncWithNoCurrentUserSet() throws Exception {
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.getAsync()).thenReturn(Task.<ParseUser>forResult(null));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);

        String sessionToken = ParseTaskUtils.wait(controller.getCurrentSessionTokenAsync());

        assertNull(sessionToken);
    }

    //endregion

    //region testClearFromMemory

    @Test
    public void testClearFromMemory() {
        CachedCurrentUserController controller =
                new CachedCurrentUserController(null);
        controller.currentUser = mock(ParseUser.class);

        controller.clearFromMemory();

        assertNull(controller.currentUser);
        assertFalse(controller.currentUserMatchesDisk);
    }

    //endregion

    //region testClearFromDisk()

    @Test
    public void testClearFromDisk() {
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.deleteAsync()).thenReturn(Task.<Void>forResult(null));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);

        controller.currentUser = new ParseUser();

        controller.clearFromDisk();

        assertNull(controller.currentUser);
        assertFalse(controller.currentUserMatchesDisk);
        verify(store, times(1)).deleteAsync();
    }

    //endregion

    //region testExistsAsync()

    @Test
    public void testExistsAsyncWithInMemoryCurrentUserSet() throws Exception {
        CachedCurrentUserController controller =
                new CachedCurrentUserController(null);
        controller.currentUser = new ParseUser();

        assertTrue(ParseTaskUtils.wait(controller.existsAsync()));
    }

    @Test
    public void testExistsAsyncWithInDiskCurrentUserSet() throws Exception {
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.existsAsync()).thenReturn(Task.forResult(true));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);

        assertTrue(ParseTaskUtils.wait(controller.existsAsync()));
    }

    @Test
    public void testExistsAsyncWithNoInMemoryAndInDiskCurrentUserSet() throws Exception {
        ParseObjectStore<ParseUser> store =
                (ParseObjectStore<ParseUser>) mock(ParseObjectStore.class);
        when(store.existsAsync()).thenReturn(Task.forResult(false));

        CachedCurrentUserController controller =
                new CachedCurrentUserController(store);

        assertFalse(ParseTaskUtils.wait(controller.existsAsync()));
    }

    //endregion

    //region testIsCurrent

    @Test
    public void testIsCurrent() {
        CachedCurrentUserController controller =
                new CachedCurrentUserController(null);
        ParseUser currentUser = new ParseUser();
        controller.currentUser = currentUser;

        assertTrue(controller.isCurrent(currentUser));
        assertFalse(controller.isCurrent(new ParseUser()));
    }

    //endregion
}