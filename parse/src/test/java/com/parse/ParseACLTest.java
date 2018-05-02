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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseACLTest {

  private final static String UNRESOLVED_KEY = "*unresolved";
  private static final String READ_PERMISSION = "read";
  private static final String WRITE_PERMISSION = "write";

  @Before
  public void setUp() {
    ParseObject.registerSubclass(ParseRole.class);
    ParseObject.registerSubclass(ParseUser.class);
  }

  @After
  public void tearDown() {
    ParseObject.unregisterSubclass(ParseRole.class);
    ParseObject.unregisterSubclass(ParseUser.class);
  }

  //region testConstructor

  @Test
  public void testConstructor() throws Exception {
    ParseACL acl = new ParseACL();

    assertEquals(0, acl.getPermissionsById().size());
  }

  @Test
  public void testConstructorWithUser() throws Exception {
    ParseUser user = new ParseUser();
    user.setObjectId("test");
    ParseACL acl = new ParseACL(user);

    assertTrue(acl.getReadAccess("test"));
    assertTrue(acl.getWriteAccess("test"));
  }

  //endregion

  //region testCopy

  @Test
  public void testCopy() throws Exception {
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

  @Test
  public void testCopyWithSaveListener() throws Exception {
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

  //region toJson

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

  //endregion

  //region parcelable

  @Test
  public void testParcelable() throws Exception {
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

  @Test
  public void testParcelableWithUnresolvedUser() throws Exception {
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

  //region testCreateACLFromJSONObject

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

  //endregion

  //region testResolveUser

  @Test
  public void testResolveUserWithNewUser() throws Exception {
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

  @Test
  public void testResolveUserWithUnresolvedUser() throws Exception {
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

  //endregion

  //region testSetAccess

  @Test
  public void testSetAccessWithNoPermissionAndNotAllowed() throws Exception {
    ParseACL acl = new ParseACL();

    acl.setReadAccess("userId", false);

    // Make sure noting is set
    assertEquals(0, acl.getPermissionsById().size());
  }

  @Test
  public void testSetAccessWithAllowed() throws Exception {
    ParseACL acl = new ParseACL();

    acl.setReadAccess("userId", true);

    assertTrue(acl.getReadAccess("userId"));
    assertEquals(1, acl.getPermissionsById().size());
  }

  @Test
  public void testSetAccessWithPermissionsAndNotAllowed() throws Exception {
    ParseACL acl = new ParseACL();
    acl.setReadAccess("userId", true);

    acl.setReadAccess("userId", false);

    // Make sure we remove the read access
    assertFalse(acl.getReadAccess("userId"));
    assertEquals(0, acl.getPermissionsById().size());
  }

  @Test
  public void testSetPublicReadAccessAllowed() throws Exception {
    ParseACL acl = new ParseACL();

    acl.setPublicReadAccess(true);

    assertTrue(acl.getPublicReadAccess());
  }

  @Test
  public void testSetPublicReadAccessNotAllowed() throws Exception {
    ParseACL acl = new ParseACL();

    acl.setPublicReadAccess(false);

    // Make sure noting is set
    assertEquals(0, acl.getPermissionsById().size());
  }

  @Test
  public void testSetPublicWriteAccessAllowed() throws Exception {
    ParseACL acl = new ParseACL();

    acl.setPublicWriteAccess(true);

    assertTrue(acl.getPublicWriteAccess());
    assertEquals(1, acl.getPermissionsById().size());
  }

  @Test
  public void testSetPublicWriteAccessNotAllowed() throws Exception {
    ParseACL acl = new ParseACL();

    acl.setPublicWriteAccess(false);

    // Make sure noting is set
    assertEquals(0, acl.getPermissionsById().size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetReadAccessWithNullUserId() throws Exception {
    ParseACL acl = new ParseACL();

    String userId = null;
    acl.setReadAccess(userId, true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetWriteAccessWithNullUserId() throws Exception {
    ParseACL acl = new ParseACL();

    String userId = null;
    acl.setWriteAccess(userId, true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetRoleReadAccessWithInvalidRole() throws Exception {
    ParseRole role = new ParseRole();
    role.setName("Player");
    ParseACL acl = new ParseACL();

    acl.setRoleReadAccess(role, true);
  }

  @Test
  public void testSetRoleReadAccess() throws Exception {
    ParseRole role = new ParseRole();
    role.setName("Player");
    role.setObjectId("test");
    ParseACL acl = new ParseACL();

    acl.setRoleReadAccess(role, true);

    assertTrue(acl.getRoleReadAccess(role));
    assertEquals(1, acl.getPermissionsById().size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetRoleWriteAccessWithInvalidRole() throws Exception {
    ParseRole role = new ParseRole();
    role.setName("Player");
    ParseACL acl = new ParseACL();

    acl.setRoleWriteAccess(role, true);
  }

  @Test
  public void testSetRoleWriteAccess() throws Exception {
    ParseRole role = new ParseRole();
    role.setName("Player");
    role.setObjectId("test");
    ParseACL acl = new ParseACL();

    acl.setRoleWriteAccess(role, true);

    assertTrue(acl.getRoleWriteAccess(role));
    assertEquals(1, acl.getPermissionsById().size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetUserReadAccessWithNotSavedNotLazyUser() throws Exception {
    ParseUser user = new ParseUser();
    ParseACL acl = new ParseACL();

    acl.setReadAccess(user, true);
  }

  @Test
  public void testSetUserReadAccessWithLazyUser() throws Exception {
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
  public void testSetUserReadAccessWithNormalUser() throws Exception {
    ParseUser user = new ParseUser();
    user.setObjectId("test");
    ParseACL acl = new ParseACL();

    acl.setReadAccess(user, true);

    assertTrue(acl.getReadAccess(user));
    assertEquals(1, acl.getPermissionsById().size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetUserWriteAccessWithNotSavedNotLazyUser() throws Exception {
    ParseUser user = new ParseUser();
    ParseACL acl = new ParseACL();

    acl.setWriteAccess(user, true);
  }

  @Test
  public void testSetUserWriteAccessWithLazyUser() throws Exception {
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

  @Test
  public void testSetUserWriteAccessWithNormalUser() throws Exception {
    ParseUser user = new ParseUser();
    user.setObjectId("test");
    ParseACL acl = new ParseACL();

    acl.setWriteAccess(user, true);

    assertTrue(acl.getWriteAccess(user));
    assertEquals(1, acl.getPermissionsById().size());
  }
  //endregion

  //region testGetAccess

  @Test
  public void testGetAccessWithNoPermission() throws Exception {
    ParseACL acl = new ParseACL();

    assertFalse(acl.getReadAccess("userId"));
  }

  @Test
  public void testGetAccessWithNoAccessType() throws Exception {
    ParseACL acl = new ParseACL();
    acl.setReadAccess("userId", true);

    assertFalse(acl.getWriteAccess("userId"));
  }

  @Test
  public void testGetAccessWithPermission() throws Exception {
    ParseACL acl = new ParseACL();
    acl.setReadAccess("userId", true);

    assertTrue(acl.getReadAccess("userId"));
  }

  @Test
  public void testGetPublicReadAccess() throws Exception {
    ParseACL acl = new ParseACL();
    acl.setPublicWriteAccess(true);

    assertTrue(acl.getPublicWriteAccess());
  }

  @Test
  public void testGetPublicWriteAccess() throws Exception {
    ParseACL acl = new ParseACL();
    acl.setPublicWriteAccess(true);

    assertTrue(acl.getPublicWriteAccess());
  }


  @Test(expected = IllegalArgumentException.class)
  public void testGetReadAccessWithNullUserId() throws Exception {
    ParseACL acl = new ParseACL();

    String userId = null;
    acl.getReadAccess(userId);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetWriteAccessWithNullUserId() throws Exception {
    ParseACL acl = new ParseACL();

    String userId = null;
    acl.getWriteAccess(userId);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetRoleReadAccessWithInvalidRole() throws Exception {
    ParseACL acl = new ParseACL();
    ParseRole role = new ParseRole();
    role.setName("Player");

    acl.getRoleReadAccess(role);
  }

  @Test
  public void testGetRoleReadAccess() throws Exception {
    ParseACL acl = new ParseACL();
    ParseRole role = new ParseRole();
    role.setName("Player");
    role.setObjectId("test");
    acl.setRoleReadAccess(role, true);

    assertTrue(acl.getRoleReadAccess(role));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetRoleWriteAccessWithInvalidRole() throws Exception {
    ParseACL acl = new ParseACL();
    ParseRole role = new ParseRole();
    role.setName("Player");

    acl.getRoleWriteAccess(role);
  }

  @Test
  public void testGetRoleWriteAccess() throws Exception {
    ParseACL acl = new ParseACL();
    ParseRole role = new ParseRole();
    role.setName("Player");
    role.setObjectId("test");
    acl.setRoleWriteAccess(role, true);

    assertTrue(acl.getRoleWriteAccess(role));
  }

  @Test
  public void testGetUserReadAccessWithUnresolvedUser() throws Exception {
    ParseACL acl = new ParseACL();
    ParseUser user = new ParseUser();
    setLazy(user);
    // Since user is a lazy user, this will set the acl's unresolved user and give it read access
    acl.setReadAccess(user ,true);

    assertTrue(acl.getReadAccess(user));
  }

  @Test
  public void testGetUserReadAccessWithLazyUser() throws Exception {
    ParseACL acl = new ParseACL();
    ParseUser user = new ParseUser();
    setLazy(user);

    assertFalse(acl.getReadAccess(user));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetUserReadAccessWithNotSavedUser() throws Exception {
    ParseACL acl = new ParseACL();
    ParseUser user = new ParseUser();

    assertFalse(acl.getReadAccess(user));
  }

  @Test
  public void testGetUserReadAccessWithNormalUser() throws Exception {
    ParseACL acl = new ParseACL();
    ParseUser user = new ParseUser();
    user.setObjectId("test");
    acl.setReadAccess(user, true);

    assertTrue(acl.getReadAccess(user));
  }

  @Test
  public void testGetUserWriteAccessWithUnresolvedUser() throws Exception {
    ParseACL acl = new ParseACL();
    ParseUser user = new ParseUser();
    setLazy(user);
    // Since user is a lazy user, this will set the acl's unresolved user and give it write access
    acl.setWriteAccess(user, true);

    assertTrue(acl.getWriteAccess(user));
  }

  @Test
  public void testGetUserWriteAccessWithLazyUser() throws Exception {
    ParseACL acl = new ParseACL();
    ParseUser user = mock(ParseUser.class);
    when(user.isLazy()).thenReturn(true);

    assertFalse(acl.getWriteAccess(user));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetUserWriteAccessWithNotSavedUser() throws Exception {
    ParseACL acl = new ParseACL();
    ParseUser user = new ParseUser();

    assertFalse(acl.getWriteAccess(user));
  }

  @Test
  public void testGetUserWriteAccessWithNormalUser() throws Exception {
    ParseACL acl = new ParseACL();
    ParseUser user = new ParseUser();
    user.setObjectId("test");
    acl.setWriteAccess(user, true);

    assertTrue(acl.getWriteAccess(user));
  }

  //endregion

  //region testGetter/Setter

  @Test
  public void testIsShared() throws Exception {
    ParseACL acl = new ParseACL();
    acl.setShared(true);

    assertTrue(acl.isShared());
  }

  @Test
  public void testUnresolvedUser() throws Exception {
    ParseACL acl = new ParseACL();
    ParseUser user = new ParseUser();
    setLazy(user);
    // This will set unresolvedUser in acl
    acl.setReadAccess(user, true);

    assertTrue(acl.hasUnresolvedUser());
    assertSame(user, acl.getUnresolvedUser());
  }

  //endregion

  private static void setLazy(ParseUser user) {
    Map<String, String> anonymousAuthData = new HashMap<>();
    anonymousAuthData.put("anonymousToken", "anonymousTest");
    user.putAuthData(ParseAnonymousUtils.AUTH_TYPE, anonymousAuthData);
  }
}
