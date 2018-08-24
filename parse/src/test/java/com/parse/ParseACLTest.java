/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.os.Parcel;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseACLTest {

    private final static String UNRESOLVED_KEY = "*unresolved";
    private static final String READ_PERMISSION = "read";
    private static final String WRITE_PERMISSION = "write";

    private static void setLazy(ParseUser user) {
        Map<String, String> anonymousAuthData = new HashMap<>();
        anonymousAuthData.put("anonymousToken", "anonymousTest");
        user.putAuthData(ParseAnonymousUtils.AUTH_TYPE, anonymousAuthData);
    }

    @Before
    public void setUp() {
        ParseObject.registerSubclass(ParseRole.class);
        ParseObject.registerSubclass(ParseUser.class);
    }

    //region testConstructor

    @After
    public void tearDown() {
        ParseObject.unregisterSubclass(ParseRole.class);
        ParseObject.unregisterSubclass(ParseUser.class);
    }

    @Test
    public void testConstructor() {
        ParseACL acl = new ParseACL();

        assertEquals(0, acl.getPermissionsById().size());
    }

    //endregion

    //region testCopy

    @Test
    public void testConstructorWithUser() {
        ParseUser user = new ParseUser();
        user.setObjectId("test");
        ParseACL acl = new ParseACL(user);

        assertTrue(acl.getReadAccess("test"));
        assertTrue(acl.getWriteAccess("test"));
    }

    @Test
    public void testCopy() {
        ParseACL acl = new ParseACL();
        final ParseUser unresolvedUser = mock(ParseUser.class);
        when(unresolvedUser.isLazy()).thenReturn(true);
        // This will set unresolvedUser and permissionsById
        acl.setReadAccess(unresolvedUser, true);
        acl.setWriteAccess(unresolvedUser, true);
        // We need to reset unresolvedUser since registerSaveListener will be triggered once in
        // setReadAccess()
        reset(unresolvedUser);

        ParseACL copiedACL = new ParseACL(acl);

        assertEquals(1, copiedACL.getPermissionsById().size());
        assertTrue(copiedACL.getPermissionsById().containsKey(UNRESOLVED_KEY));
        assertTrue(copiedACL.getReadAccess(unresolvedUser));
        assertTrue(copiedACL.getWriteAccess(unresolvedUser));
        assertFalse(copiedACL.isShared());
        assertSame(unresolvedUser, copiedACL.getUnresolvedUser());
        verify(unresolvedUser, times(1)).registerSaveListener(any(GetCallback.class));
    }

    //endregion

    //region toJson

    @Test
    public void testCopyWithSaveListener() {
        ParseACL acl = new ParseACL();
        final ParseUser unresolvedUser = mock(ParseUser.class);
        when(unresolvedUser.isLazy()).thenReturn(true);
        // This will set unresolvedUser and permissionsById
        acl.setReadAccess(unresolvedUser, true);
        acl.setWriteAccess(unresolvedUser, true);
        // We need to reset unresolvedUser since registerSaveListener will be triggered once in
        // setReadAccess()
        reset(unresolvedUser);

        ParseACL copiedACL = new ParseACL(acl);

        // Make sure the callback is called
        ArgumentCaptor<GetCallback> callbackCaptor = ArgumentCaptor.forClass(GetCallback.class);
        verify(unresolvedUser, times(1)).registerSaveListener(callbackCaptor.capture());

        // Trigger the callback
        GetCallback callback = callbackCaptor.getValue();
        // Manually set userId and not lazy, mock user is saved
        when(unresolvedUser.getObjectId()).thenReturn("userId");
        when(unresolvedUser.isLazy()).thenReturn(false);
        callback.done(unresolvedUser, null);

        // Makre sure we unregister the callback
        verify(unresolvedUser, times(1)).unregisterSaveListener(any(GetCallback.class));
        assertEquals(1, copiedACL.getPermissionsById().size());
        assertTrue(copiedACL.getReadAccess(unresolvedUser));
        assertTrue(copiedACL.getWriteAccess(unresolvedUser));
        assertFalse(copiedACL.isShared());
        // No more unresolved permissions since it has been resolved in the callback.
        assertFalse(copiedACL.getPermissionsById().containsKey(UNRESOLVED_KEY));
        assertNull(copiedACL.getUnresolvedUser());
    }

    //endregion

    //region parcelable

    @Test
    public void testToJson() throws Exception {
        ParseACL acl = new ParseACL();
        acl.setReadAccess("userId", true);
        ParseUser unresolvedUser = new ParseUser();
        setLazy(unresolvedUser);
        acl.setReadAccess(unresolvedUser, true);
        // Mock decoder
        ParseEncoder mockEncoder = mock(ParseEncoder.class);
        when(mockEncoder.encode(eq(unresolvedUser))).thenReturn("unresolvedUserJson");

        JSONObject aclJson = acl.toJSONObject(mockEncoder);

        assertEquals("unresolvedUserJson", aclJson.getString("unresolvedUser"));
        assertEquals(aclJson.getJSONObject("userId").getBoolean("read"), true);
        assertEquals(aclJson.getJSONObject("userId").has("write"), false);
        assertEquals(aclJson.getJSONObject("*unresolved").getBoolean("read"), true);
        assertEquals(aclJson.getJSONObject("*unresolved").has("write"), false);
        assertEquals(aclJson.length(), 3);
    }

    @Test
    public void testParcelable() {
        ParseACL acl = new ParseACL();
        acl.setReadAccess("userId", true);
        ParseUser user = new ParseUser();
        user.setObjectId("userId2");
        acl.setReadAccess(user, true);
        acl.setRoleWriteAccess("role", true);
        acl.setShared(true);

        Parcel parcel = Parcel.obtain();
        acl.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        acl = ParseACL.CREATOR.createFromParcel(parcel);

        assertTrue(acl.getReadAccess("userId"));
        assertTrue(acl.getReadAccess(user));
        assertTrue(acl.getRoleWriteAccess("role"));
        assertTrue(acl.isShared());
        assertFalse(acl.getPublicReadAccess());
        assertFalse(acl.getPublicWriteAccess());
    }

    //endregion

    //region testCreateACLFromJSONObject

    @Test
    public void testParcelableWithUnresolvedUser() {
        ParseFieldOperations.registerDefaultDecoders(); // Needed for unparceling ParseObjects
        ParseACL acl = new ParseACL();
        ParseUser unresolved = new ParseUser();
        setLazy(unresolved);
        acl.setReadAccess(unresolved, true);

        // unresolved users need a local id when parcelling and unparcelling.
        // Since we don't have an Android environment, local id creation will fail.
        unresolved.localId = "localId";
        Parcel parcel = Parcel.obtain();
        acl.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        // Do not user ParseObjectParcelDecoder because it requires local ids
        acl = new ParseACL(parcel, new ParseParcelDecoder());
        assertTrue(acl.getReadAccess(unresolved));
    }

    //endregion

    //region testResolveUser

    @Test
    public void testCreateACLFromJSONObject() throws Exception {
        JSONObject aclJson = new JSONObject();
        JSONObject permission = new JSONObject();
        permission.put(READ_PERMISSION, true);
        permission.put(WRITE_PERMISSION, true);
        aclJson.put("userId", permission);
        ParseUser unresolvedUser = new ParseUser();
        JSONObject unresolvedUserJson = new JSONObject();
        aclJson.put("unresolvedUser", unresolvedUserJson);
        // Mock decoder
        ParseDecoder mockDecoder = mock(ParseDecoder.class);
        when(mockDecoder.decode(eq(unresolvedUserJson))).thenReturn(unresolvedUser);

        ParseACL acl = ParseACL.createACLFromJSONObject(aclJson, mockDecoder);

        assertSame(unresolvedUser, acl.getUnresolvedUser());
        assertTrue(acl.getReadAccess("userId"));
        assertTrue(acl.getWriteAccess("userId"));
        assertEquals(1, acl.getPermissionsById().size());
    }

    @Test
    public void testResolveUserWithNewUser() {
        ParseUser unresolvedUser = new ParseUser();
        setLazy(unresolvedUser);
        ParseACL acl = new ParseACL();
        acl.setReadAccess(unresolvedUser, true);

        ParseUser other = new ParseUser();
        // local id creation fails if we don't have Android environment
        unresolvedUser.localId = "someId";
        other.localId = "someOtherId";
        acl.resolveUser(other);

        // Make sure unresolvedUser is not changed
        assertSame(unresolvedUser, acl.getUnresolvedUser());
    }

    //endregion

    //region testSetAccess

    @Test
    public void testResolveUserWithUnresolvedUser() {
        ParseACL acl = new ParseACL();
        ParseUser unresolvedUser = new ParseUser();
        setLazy(unresolvedUser);
        // This will set the unresolvedUser in acl
        acl.setReadAccess(unresolvedUser, true);
        acl.setWriteAccess(unresolvedUser, true);

        unresolvedUser.setObjectId("test");
        acl.resolveUser(unresolvedUser);

        assertNull(acl.getUnresolvedUser());
        assertTrue(acl.getReadAccess(unresolvedUser));
        assertTrue(acl.getWriteAccess(unresolvedUser));
        assertEquals(1, acl.getPermissionsById().size());
        assertFalse(acl.getPermissionsById().containsKey(UNRESOLVED_KEY));
    }

    @Test
    public void testSetAccessWithNoPermissionAndNotAllowed() {
        ParseACL acl = new ParseACL();

        acl.setReadAccess("userId", false);

        // Make sure noting is set
        assertEquals(0, acl.getPermissionsById().size());
    }

    @Test
    public void testSetAccessWithAllowed() {
        ParseACL acl = new ParseACL();

        acl.setReadAccess("userId", true);

        assertTrue(acl.getReadAccess("userId"));
        assertEquals(1, acl.getPermissionsById().size());
    }

    @Test
    public void testSetAccessWithPermissionsAndNotAllowed() {
        ParseACL acl = new ParseACL();
        acl.setReadAccess("userId", true);

        acl.setReadAccess("userId", false);

        // Make sure we remove the read access
        assertFalse(acl.getReadAccess("userId"));
        assertEquals(0, acl.getPermissionsById().size());
    }

    @Test
    public void testSetPublicReadAccessAllowed() {
        ParseACL acl = new ParseACL();

        acl.setPublicReadAccess(true);

        assertTrue(acl.getPublicReadAccess());
    }

    @Test
    public void testSetPublicReadAccessNotAllowed() {
        ParseACL acl = new ParseACL();

        acl.setPublicReadAccess(false);

        // Make sure noting is set
        assertEquals(0, acl.getPermissionsById().size());
    }

    @Test
    public void testSetPublicWriteAccessAllowed() {
        ParseACL acl = new ParseACL();

        acl.setPublicWriteAccess(true);

        assertTrue(acl.getPublicWriteAccess());
        assertEquals(1, acl.getPermissionsById().size());
    }

    @Test
    public void testSetPublicWriteAccessNotAllowed() {
        ParseACL acl = new ParseACL();

        acl.setPublicWriteAccess(false);

        // Make sure noting is set
        assertEquals(0, acl.getPermissionsById().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetReadAccessWithNullUserId() {
        ParseACL acl = new ParseACL();

        String userId = null;
        acl.setReadAccess(userId, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetWriteAccessWithNullUserId() {
        ParseACL acl = new ParseACL();

        String userId = null;
        acl.setWriteAccess(userId, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetRoleReadAccessWithInvalidRole() {
        ParseRole role = new ParseRole();
        role.setName("Player");
        ParseACL acl = new ParseACL();

        acl.setRoleReadAccess(role, true);
    }

    @Test
    public void testSetRoleReadAccess() {
        ParseRole role = new ParseRole();
        role.setName("Player");
        role.setObjectId("test");
        ParseACL acl = new ParseACL();

        acl.setRoleReadAccess(role, true);

        assertTrue(acl.getRoleReadAccess(role));
        assertEquals(1, acl.getPermissionsById().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetRoleWriteAccessWithInvalidRole() {
        ParseRole role = new ParseRole();
        role.setName("Player");
        ParseACL acl = new ParseACL();

        acl.setRoleWriteAccess(role, true);
    }

    @Test
    public void testSetRoleWriteAccess() {
        ParseRole role = new ParseRole();
        role.setName("Player");
        role.setObjectId("test");
        ParseACL acl = new ParseACL();

        acl.setRoleWriteAccess(role, true);

        assertTrue(acl.getRoleWriteAccess(role));
        assertEquals(1, acl.getPermissionsById().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetUserReadAccessWithNotSavedNotLazyUser() {
        ParseUser user = new ParseUser();
        ParseACL acl = new ParseACL();

        acl.setReadAccess(user, true);
    }

    @Test
    public void testSetUserReadAccessWithLazyUser() {
        ParseUser unresolvedUser = mock(ParseUser.class);
        when(unresolvedUser.isLazy()).thenReturn(true);
        ParseACL acl = new ParseACL();

        acl.setReadAccess(unresolvedUser, true);

        assertSame(unresolvedUser, acl.getUnresolvedUser());
        verify(unresolvedUser, times(1)).registerSaveListener(any(GetCallback.class));
        assertTrue(acl.getPermissionsById().containsKey(UNRESOLVED_KEY));
        assertTrue(acl.getReadAccess(unresolvedUser));
        assertEquals(1, acl.getPermissionsById().size());
    }

    @Test
    public void testSetUserReadAccessWithNormalUser() {
        ParseUser user = new ParseUser();
        user.setObjectId("test");
        ParseACL acl = new ParseACL();

        acl.setReadAccess(user, true);

        assertTrue(acl.getReadAccess(user));
        assertEquals(1, acl.getPermissionsById().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetUserWriteAccessWithNotSavedNotLazyUser() {
        ParseUser user = new ParseUser();
        ParseACL acl = new ParseACL();

        acl.setWriteAccess(user, true);
    }

    @Test
    public void testSetUserWriteAccessWithLazyUser() {
        ParseUser user = mock(ParseUser.class);
        when(user.isLazy()).thenReturn(true);
        ParseACL acl = new ParseACL();

        acl.setWriteAccess(user, true);

        assertSame(user, acl.getUnresolvedUser());
        verify(user, times(1)).registerSaveListener(any(GetCallback.class));
        assertTrue(acl.getPermissionsById().containsKey(UNRESOLVED_KEY));
        assertTrue(acl.getWriteAccess(user));
        assertEquals(1, acl.getPermissionsById().size());
    }
    //endregion

    //region testGetAccess

    @Test
    public void testSetUserWriteAccessWithNormalUser() {
        ParseUser user = new ParseUser();
        user.setObjectId("test");
        ParseACL acl = new ParseACL();

        acl.setWriteAccess(user, true);

        assertTrue(acl.getWriteAccess(user));
        assertEquals(1, acl.getPermissionsById().size());
    }

    @Test
    public void testGetAccessWithNoPermission() {
        ParseACL acl = new ParseACL();

        assertFalse(acl.getReadAccess("userId"));
    }

    @Test
    public void testGetAccessWithNoAccessType() {
        ParseACL acl = new ParseACL();
        acl.setReadAccess("userId", true);

        assertFalse(acl.getWriteAccess("userId"));
    }

    @Test
    public void testGetAccessWithPermission() {
        ParseACL acl = new ParseACL();
        acl.setReadAccess("userId", true);

        assertTrue(acl.getReadAccess("userId"));
    }

    @Test
    public void testGetPublicReadAccess() {
        ParseACL acl = new ParseACL();
        acl.setPublicWriteAccess(true);

        assertTrue(acl.getPublicWriteAccess());
    }

    @Test
    public void testGetPublicWriteAccess() {
        ParseACL acl = new ParseACL();
        acl.setPublicWriteAccess(true);

        assertTrue(acl.getPublicWriteAccess());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetReadAccessWithNullUserId() {
        ParseACL acl = new ParseACL();

        String userId = null;
        acl.getReadAccess(userId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetWriteAccessWithNullUserId() {
        ParseACL acl = new ParseACL();

        String userId = null;
        acl.getWriteAccess(userId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetRoleReadAccessWithInvalidRole() {
        ParseACL acl = new ParseACL();
        ParseRole role = new ParseRole();
        role.setName("Player");

        acl.getRoleReadAccess(role);
    }

    @Test
    public void testGetRoleReadAccess() {
        ParseACL acl = new ParseACL();
        ParseRole role = new ParseRole();
        role.setName("Player");
        role.setObjectId("test");
        acl.setRoleReadAccess(role, true);

        assertTrue(acl.getRoleReadAccess(role));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetRoleWriteAccessWithInvalidRole() {
        ParseACL acl = new ParseACL();
        ParseRole role = new ParseRole();
        role.setName("Player");

        acl.getRoleWriteAccess(role);
    }

    @Test
    public void testGetRoleWriteAccess() {
        ParseACL acl = new ParseACL();
        ParseRole role = new ParseRole();
        role.setName("Player");
        role.setObjectId("test");
        acl.setRoleWriteAccess(role, true);

        assertTrue(acl.getRoleWriteAccess(role));
    }

    @Test
    public void testGetUserReadAccessWithUnresolvedUser() {
        ParseACL acl = new ParseACL();
        ParseUser user = new ParseUser();
        setLazy(user);
        // Since user is a lazy user, this will set the acl's unresolved user and give it read access
        acl.setReadAccess(user, true);

        assertTrue(acl.getReadAccess(user));
    }

    @Test
    public void testGetUserReadAccessWithLazyUser() {
        ParseACL acl = new ParseACL();
        ParseUser user = new ParseUser();
        setLazy(user);

        assertFalse(acl.getReadAccess(user));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetUserReadAccessWithNotSavedUser() {
        ParseACL acl = new ParseACL();
        ParseUser user = new ParseUser();

        assertFalse(acl.getReadAccess(user));
    }

    @Test
    public void testGetUserReadAccessWithNormalUser() {
        ParseACL acl = new ParseACL();
        ParseUser user = new ParseUser();
        user.setObjectId("test");
        acl.setReadAccess(user, true);

        assertTrue(acl.getReadAccess(user));
    }

    @Test
    public void testGetUserWriteAccessWithUnresolvedUser() {
        ParseACL acl = new ParseACL();
        ParseUser user = new ParseUser();
        setLazy(user);
        // Since user is a lazy user, this will set the acl's unresolved user and give it write access
        acl.setWriteAccess(user, true);

        assertTrue(acl.getWriteAccess(user));
    }

    @Test
    public void testGetUserWriteAccessWithLazyUser() {
        ParseACL acl = new ParseACL();
        ParseUser user = mock(ParseUser.class);
        when(user.isLazy()).thenReturn(true);

        assertFalse(acl.getWriteAccess(user));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetUserWriteAccessWithNotSavedUser() {
        ParseACL acl = new ParseACL();
        ParseUser user = new ParseUser();

        assertFalse(acl.getWriteAccess(user));
    }

    //endregion

    //region testGetter/Setter

    @Test
    public void testGetUserWriteAccessWithNormalUser() {
        ParseACL acl = new ParseACL();
        ParseUser user = new ParseUser();
        user.setObjectId("test");
        acl.setWriteAccess(user, true);

        assertTrue(acl.getWriteAccess(user));
    }

    @Test
    public void testIsShared() {
        ParseACL acl = new ParseACL();
        acl.setShared(true);

        assertTrue(acl.isShared());
    }

    //endregion

    @Test
    public void testUnresolvedUser() {
        ParseACL acl = new ParseACL();
        ParseUser user = new ParseUser();
        setLazy(user);
        // This will set unresolvedUser in acl
        acl.setReadAccess(user, true);

        assertTrue(acl.hasUnresolvedUser());
        assertSame(user, acl.getUnresolvedUser());
    }
}
