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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import bolts.Task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// For ParseExecutors.main()
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseUserTest extends ResetPluginsParseTest {

  @Rule
  public ExpectedException thrown= ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    super.setUp();
    ParseObject.registerSubclass(ParseUser.class);
    ParseObject.registerSubclass(ParseSession.class);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    ParseObject.unregisterSubclass(ParseUser.class);
    ParseObject.unregisterSubclass(ParseSession.class);
    Parse.disableLocalDatastore();
  }

  @Test
  public void testImmutableKeys() {
    ParseUser user = new ParseUser();
    user.put("foo", "bar");

    try {
      user.put("sessionToken", "blah");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Cannot modify"));
    }

    try {
      user.remove("sessionToken");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Cannot modify"));
    }

    try {
      user.removeAll("sessionToken", Collections.emptyList());
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Cannot modify"));
    }
  }

  // region Parcelable

  @Test
  public void testOnSaveRestoreState() throws Exception {
    ParseUser user = new ParseUser();
    user.setObjectId("objId");
    user.setIsCurrentUser(true);

    Parcel parcel = Parcel.obtain();
    user.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);
    user = (ParseUser) ParseObject.CREATOR.createFromParcel(parcel);
    assertTrue(user.isCurrentUser());
  }

  @Test
  public void testParcelableState() throws Exception {
    ParseUser.State state = new ParseUser.State.Builder()
        .objectId("test")
        .isNew(true)
        .build();
    ParseUser user = ParseObject.from(state);
    assertTrue(user.isNew());

    Parcel parcel = Parcel.obtain();
    user.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);
    user = (ParseUser) ParseObject.CREATOR.createFromParcel(parcel);
    assertTrue(user.isNew());
  }

  // endregion

  //region SignUpAsync

  @Test
  public void testSignUpAsyncWithNoUserName() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.<ParseUser>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseUser user = new ParseUser();

    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Username cannot be missing or blank");

    ParseTaskUtils.wait(user.signUpAsync(Task.<Void>forResult(null)));
  }

  @Test
  public void testSignUpAsyncWithNoPassword() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.<ParseUser>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseUser user = new ParseUser();
    user.setUsername("userName");

    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Password cannot be missing or blank");

    ParseTaskUtils.wait(user.signUpAsync(Task.<Void>forResult(null)));
  }

  @Test
  public void testSignUpAsyncWithObjectIdSetAndAuthDataNotSet() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.<ParseUser>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseUser.State userState = new ParseUser.State.Builder()
        .objectId("test")
        .build();
    ParseUser user = ParseObject.from(userState);
    user.setUsername("userName");
    user.setPassword("password");

    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Cannot sign up a user that has already signed up.");

    ParseTaskUtils.wait(user.signUpAsync(Task.<Void>forResult(null)));
  }

  @Test
  public void testSignUpAsyncWithObjectIdSetAndAuthDataSet() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseUser currentUser = mock(ParseUser.class);
    when(currentUser.getSessionToken()).thenReturn("sessionToken");
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.forResult(currentUser));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseUser.State userState = new ParseUser.State.Builder()
        .objectId("test")
        .putAuthData(ParseAnonymousUtils.AUTH_TYPE, null)
        .build();
    ParseUser user = ParseObject.from(userState);
    user.setUsername("userName");
    user.setPassword("password");
    //TODO (mengyan): Avoid using partial mock after we have ParseObjectInstanceController
    ParseUser partialMockUser = spy(user);
    doReturn(Task.<Void>forResult(null))
        .when(partialMockUser)
        .saveAsync(anyString(), Matchers.<Task<Void>>any());

    ParseTaskUtils.wait(partialMockUser.signUpAsync(Task.<Void>forResult(null)));

    // Verify user is saved
    verify(partialMockUser, times(1)).saveAsync(eq("sessionToken"), Matchers.<Task<Void>>any());
  }

  @Test
  public void testSignUpAsyncWithAnotherSignUpAlreadyRunning() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.<ParseUser>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseUser user = new ParseUser();
    user.setUsername("userName");
    user.setPassword("password");
    user.startSave();

    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Cannot sign up a user that is already signing up.");

    ParseTaskUtils.wait(user.signUpAsync(Task.<Void>forResult(null)));
  }

  @Test
  public void testSignUpAsyncWithSignUpSameAnonymousUser() throws Exception {
    ParseUser user = new ParseUser();
    user.setUsername("userName");
    user.setPassword("password");
    Map<String, String> anonymousAuthData = new HashMap<>();
    anonymousAuthData.put("key", "token");
    user.putAuthData(ParseAnonymousUtils.AUTH_TYPE, anonymousAuthData);

    // Register a mock currentUserController to make getCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.forResult(user));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Attempt to merge currentUser with itself.");

    ParseTaskUtils.wait(user.signUpAsync(Task.<Void>forResult(null)));
  }

  @Test
  public void testSignUpAsyncWithMergeInDiskAnonymousUser() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseUser currentUser = mock(ParseUser.class);
    when(currentUser.getUsername()).thenReturn("oldUserName");
    when(currentUser.getPassword()).thenReturn("oldPassword");
    when(currentUser.isLazy()).thenReturn(false);
    when(currentUser.isLinked(ParseAnonymousUtils.AUTH_TYPE)).thenReturn(true);
    when(currentUser.getSessionToken()).thenReturn("oldSessionToken");
    when(currentUser.getAuthData()).thenReturn(new HashMap<String, Map<String, String>>());
    when(currentUser.saveAsync(anyString(), eq(false), Matchers.<Task<Void>>any()))
        .thenReturn(Task.<Void>forResult(null));
    ParseUser.State state = new ParseUser.State.Builder()
        .put("oldKey", "oldValue")
        .build();
    when(currentUser.getState()).thenReturn(state);
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.forResult(currentUser));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseUser user = new ParseUser();
    user.setUsername("userName");
    user.setPassword("password");
    Map<String, String> anonymousAuthData = new HashMap<>();
    anonymousAuthData.put("key", "token");
    user.putAuthData(ParseAnonymousUtils.AUTH_TYPE, anonymousAuthData);

    Task<Void> signUpTask = user.signUpAsync(Task.<Void>forResult(null));
    signUpTask.waitForCompletion();

    // Make sure currentUser copy changes from user
    verify(currentUser, times(1)).copyChangesFrom(user);
    // Make sure we update currentUser username and password
    verify(currentUser, times(1)).setUsername("userName");
    verify(currentUser, times(1)).setPassword("password");
    // Make sure we save currentUser
    verify(currentUser, times(1))
        .saveAsync(eq("oldSessionToken"), eq(false), Matchers.<Task<Void>>any());
    // Make sure we merge currentUser with user after save
    assertEquals("oldValue", user.get("oldKey"));
    // Make sure set currentUser
    verify(currentUserController, times(1)).setAsync(eq(user));
  }

  @Test
  public void testSignUpAsyncWithMergeInDiskAnonymousUserSaveFailure() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseUser currentUser = new ParseUser();
    Map<String, String> oldAnonymousAuthData = new HashMap<>();
    oldAnonymousAuthData.put("oldKey", "oldToken");
    currentUser.putAuthData(ParseAnonymousUtils.AUTH_TYPE, oldAnonymousAuthData);
    ParseUser partialMockCurrentUser = spy(currentUser); // Spy since we need mutex
    when(partialMockCurrentUser.getUsername()).thenReturn("oldUserName");
    when(partialMockCurrentUser.getPassword()).thenReturn("oldPassword");
    when(partialMockCurrentUser.getSessionToken()).thenReturn("oldSessionToken");
    when(partialMockCurrentUser.isLazy()).thenReturn(false);
    ParseException saveException = new ParseException(ParseException.OTHER_CAUSE, "");
    doReturn(Task.<Void>forError(saveException))
        .when(partialMockCurrentUser)
        .saveAsync(anyString(), eq(false), Matchers.<Task<Void>>any());
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean()))
        .thenReturn(Task.forResult(partialMockCurrentUser));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseUser user = new ParseUser();
    user.setUsername("userName");
    user.setPassword("password");
    Map<String, String> anonymousAuthData = new HashMap<>();
    anonymousAuthData.put("key", "token");
    user.putAuthData(ParseAnonymousUtils.AUTH_TYPE, anonymousAuthData);

    Task<Void> signUpTask = user.signUpAsync(Task.<Void>forResult(null));
    signUpTask.waitForCompletion();

    // Make sure we update currentUser username and password
    verify(partialMockCurrentUser, times(1)).setUsername("userName");
    verify(partialMockCurrentUser, times(1)).setPassword("password");
    // Make sure we sync user with currentUser
    verify(partialMockCurrentUser, times(1)).copyChangesFrom(eq(user));
    // Make sure we save currentUser
    verify(partialMockCurrentUser, times(1))
        .saveAsync(eq("oldSessionToken"), eq(false), Matchers.<Task<Void>>any());
    // Make sure we restore old username and password after save fails
    verify(partialMockCurrentUser, times(1)).setUsername("oldUserName");
    verify(partialMockCurrentUser, times(1)).setPassword("oldPassword");
    // Make sure we restore anonymity
    verify(partialMockCurrentUser, times(1)).putAuthData(
        ParseAnonymousUtils.AUTH_TYPE, oldAnonymousAuthData);
    // Make sure task is failed
    assertTrue(signUpTask.isFaulted());
    assertSame(saveException, signUpTask.getError());
  }

  @Test
  public void testSignUpAsyncWithNoCurrentUserAndSignUpSuccess() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean()))
        .thenReturn(Task.<ParseUser>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);
    // Register a mock userController to make logIn work
    ParseUserController userController = mock(ParseUserController.class);
    ParseUser.State newUserState = new ParseUser.State.Builder()
        .put("newKey", "newValue")
        .sessionToken("newSessionToken")
        .build();
    when(userController.signUpAsync(
        any(ParseUser.State.class), any(ParseOperationSet.class), anyString()))
        .thenReturn(Task.forResult(newUserState));
    ParseCorePlugins.getInstance().registerUserController(userController);

    ParseUser user = new ParseUser();
    user.setUsername("userName");
    user.setPassword("password");

    ParseTaskUtils.wait(user.signUpAsync(Task.<Void>forResult(null)));

    // Make sure we sign up the user
    verify(userController, times(1)).signUpAsync(
        any(ParseUser.State.class), any(ParseOperationSet.class), anyString());
    // Make sure user's data is correct
    assertEquals("newSessionToken", user.getSessionToken());
    assertEquals("newValue", user.getString("newKey"));
    assertFalse(user.isLazy());
    // Make sure we set the current user
    verify(currentUserController, times(1)).setAsync(user);
  }

  @Test
  public void testSignUpAsyncWithNoCurrentUserAndSignUpFailure() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean()))
        .thenReturn(Task.<ParseUser>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);
    // Register a mock userController to make logIn work
    ParseUserController userController = mock(ParseUserController.class);
    ParseException signUpException = new ParseException(ParseException.OTHER_CAUSE, "test");
    when(userController.signUpAsync(
        any(ParseUser.State.class), any(ParseOperationSet.class), anyString()))
        .thenReturn(Task.<ParseUser.State>forError(signUpException));
    ParseCorePlugins.getInstance().registerUserController(userController);

    ParseUser user = new ParseUser();
    user.put("key", "value");
    user.setUsername("userName");
    user.setPassword("password");

    Task<Void> signUpTask = user.signUpAsync(Task.<Void>forResult(null));

    // Make sure we sign up the user
    verify(userController, times(1)).signUpAsync(
        any(ParseUser.State.class), any(ParseOperationSet.class), anyString());
    // Make sure user's data is correct
    assertEquals("value", user.getString("key"));
    // Make sure we never set the current user
    verify(currentUserController, never()).setAsync(user);
    // Make sure task is failed
    assertTrue(signUpTask.isFaulted());
    assertSame(signUpException, signUpTask.getError());
  }

  //endregion

  //region testLogInWithAsync

  @Test
  public void testLoginWithAsyncWithoutExistingLazyUser() throws ParseException {
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(false)).thenReturn(Task.<ParseUser>forResult(null));
    when(currentUserController.setAsync(any(ParseUser.class)))
        .thenReturn(Task.<Void>forResult(null));

    ParseUser.State userState = mock(ParseUser.State.class);
    when(userState.className()).thenReturn("_User");
    when(userState.objectId()).thenReturn("1234");
    when(userState.isComplete()).thenReturn(true);

    ParseUserController userController = mock(ParseUserController.class);
    when(userController.logInAsync(anyString(), anyMapOf(String.class, String.class)))
        .thenReturn(Task.forResult(userState));

    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);
    ParseCorePlugins.getInstance().registerUserController(userController);

    String authType = "facebook";
    Map<String, String> authData = new HashMap<>();
    authData.put("token", "123");
    ParseUser user = ParseTaskUtils.wait(ParseUser.logInWithInBackground(authType, authData));

    verify(currentUserController).getAsync(false);
    verify(userController).logInAsync(authType, authData);
    verify(currentUserController).setAsync(user);
    assertSame(userState, user.getState());

    verifyNoMoreInteractions(currentUserController);
    verifyNoMoreInteractions(userController);
  }

  @Test
  public void testLoginWithAsyncWithLinkedLazyUser() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseUser currentUser = new ParseUser();
    currentUser.putAuthData(ParseAnonymousUtils.AUTH_TYPE, new HashMap<String, String>());
    setLazy(currentUser);
    ParseUser partialMockCurrentUser = spy(currentUser);
    when(partialMockCurrentUser.getSessionToken()).thenReturn("oldSessionToken");
    doReturn(Task.<ParseUser>forResult(null))
        .when(partialMockCurrentUser)
        .resolveLazinessAsync(Matchers.<Task<Void>>any());
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(false)).thenReturn(Task.forResult(partialMockCurrentUser));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    String authType = "facebook";
    Map<String, String> authData = new HashMap<>();
    authData.put("token", "123");
    ParseUser userAfterLogin = ParseTaskUtils.wait(ParseUser.logInWithInBackground(authType,
        authData));

    // Make sure we stripAnonymity
    assertNull(userAfterLogin.getAuthData().get(ParseAnonymousUtils.AUTH_TYPE));
    // Make sure we update authData
    assertEquals(authData, userAfterLogin.getAuthData().get("facebook"));
    // Make sure we resolveLaziness
    verify(partialMockCurrentUser, times(1)).resolveLazinessAsync(Matchers.<Task<Void>>any());
  }

  @Test
  public void testLoginWithAsyncWithLinkedLazyUseAndResolveLazinessFailure() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseUser currentUser = new ParseUser();
    Map<String, String> oldAnonymousAuthData = new HashMap<>();
    oldAnonymousAuthData.put("oldKey", "oldToken");
    currentUser.putAuthData(ParseAnonymousUtils.AUTH_TYPE, oldAnonymousAuthData);
    ParseUser partialMockCurrentUser = spy(currentUser);
    when(partialMockCurrentUser.getSessionToken()).thenReturn("oldSessionToken");
    doReturn(Task.<ParseUser>forError(new Exception()))
        .when(partialMockCurrentUser)
        .resolveLazinessAsync(Matchers.<Task<Void>>any());
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(false)).thenReturn(Task.forResult(partialMockCurrentUser));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    String authType = "facebook";
    Map<String, String> authData = new HashMap<>();
    authData.put("token", "123");

    Task<ParseUser> loginTask = ParseUser.logInWithInBackground(authType, authData);
    loginTask.waitForCompletion();

    // Make sure we try to resolveLaziness
    verify(partialMockCurrentUser, times(1)).resolveLazinessAsync(Matchers.<Task<Void>>any());
    // Make sure we do not save new authData
    assertNull(partialMockCurrentUser.getAuthData().get("facebook"));
    // Make sure we restore anonymity after resolve laziness failure
    assertEquals(oldAnonymousAuthData, partialMockCurrentUser.getAuthData()
        .get(ParseAnonymousUtils.AUTH_TYPE));
    // Make sure task fails
    assertTrue(loginTask.isFaulted());
  }

  @Test
  public void testLoginWithAsyncWithLinkedNotLazyUser() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseUser.State state = new ParseUser.State.Builder()
        .objectId("objectId") // Make it not lazy
        .putAuthData(ParseAnonymousUtils.AUTH_TYPE, new HashMap<String, String>())
        .build();
    ParseUser currentUser = ParseUser.from(state);
    ParseUser partialMockCurrentUser = spy(currentUser); // ParseUser.mutex
    doReturn(Task.<Void>forResult(null))
        .when(partialMockCurrentUser)
        .linkWithInBackground(anyString(), Matchers.<Map<String, String>>any());
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync()).thenReturn(Task.forResult(partialMockCurrentUser));
    when(currentUserController.getAsync(anyBoolean()))
        .thenReturn(Task.forResult(partialMockCurrentUser));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    String authType = "facebook";
    Map<String, String> authData = new HashMap<>();
    authData.put("token", "123");

    ParseUser userAfterLogin = ParseTaskUtils.wait(ParseUser.logInWithInBackground(authType,
        authData));

    // Make sure we link authData
    verify(partialMockCurrentUser, times(1)).linkWithInBackground(authType, authData);
    assertSame(partialMockCurrentUser, userAfterLogin);
  }

  @Test
  public void testLoginWithAsyncWithLinkedNotLazyUserLinkFailure() throws Exception {
    // Register a mock userController to make logIn work
    ParseUserController userController = mock(ParseUserController.class);
    ParseUser.State newUserState = new ParseUser.State.Builder()
        .put("newKey", "newValue")
        .sessionToken("newSessionToken")
        .build();
    when(userController.logInAsync(anyString(), Matchers.<Map<String, String>>any()))
        .thenReturn(Task.forResult(newUserState));
    ParseCorePlugins.getInstance().registerUserController(userController);
    // Register a mock currentUserController to make getCurrentUser work
    ParseUser currentUser = new ParseUser();
    currentUser.putAuthData(ParseAnonymousUtils.AUTH_TYPE, new HashMap<String, String>());
    currentUser.setObjectId("objectId"); // Make it not lazy.
    ParseUser partialMockCurrentUser = spy(currentUser);
    when(partialMockCurrentUser.getSessionToken()).thenReturn("sessionToken");
    ParseException linkException =
        new ParseException(ParseException.ACCOUNT_ALREADY_LINKED, "Account already linked");
    doReturn(Task.<Void>forError(linkException))
        .when(partialMockCurrentUser)
        .linkWithInBackground(anyString(), Matchers.<Map<String, String>>any());
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(false)).thenReturn(Task.forResult(partialMockCurrentUser));
    when(currentUserController.setAsync(any(ParseUser.class)))
        .thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);


    String authType = "facebook";
    Map<String, String> authData = new HashMap<>();
    authData.put("token", "123");
    ParseUser userAfterLogin = ParseTaskUtils.wait(ParseUser.logInWithInBackground(authType,
        authData));

    // Make sure we link authData
    verify(partialMockCurrentUser, times(1)).linkWithInBackground(authType, authData);
    // Make sure we login authData
    verify(userController, times(1)).logInAsync("facebook", authData);
    // Make sure we save the new created user as currentUser
    verify(currentUserController, times(1)).setAsync(any(ParseUser.class));
    // Make sure the new created user has correct data
    assertEquals("newValue", userAfterLogin.get("newKey"));
    assertEquals("newSessionToken", userAfterLogin.getSessionToken());
  }

  @Test
  public void testLoginWithAsyncWithNoCurrentUser() throws Exception {
    // Register a mock userController to make logIn work
    ParseUserController userController = mock(ParseUserController.class);
    ParseUser.State newUserState = new ParseUser.State.Builder()
        .put("newKey", "newValue")
        .sessionToken("newSessionToken")
        .build();
    when(userController.logInAsync(anyString(), Matchers.<Map<String, String>>any()))
        .thenReturn(Task.forResult(newUserState));
    ParseCorePlugins.getInstance().registerUserController(userController);
    // Register a mock currentUserController to make getCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(false)).thenReturn(Task.<ParseUser>forResult(null));
    when(currentUserController.setAsync(any(ParseUser.class)))
        .thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    String authType = "facebook";
    Map<String, String> authData = new HashMap<>();
    authData.put("token", "123");

    ParseUser userAfterLogin = ParseTaskUtils.wait(ParseUser.logInWithInBackground(authType,
        authData));

    // Make sure we login authData
    verify(userController, times(1)).logInAsync("facebook", authData);
    // Make sure we save the new created user as currentUser
    verify(currentUserController, times(1)).setAsync(any(ParseUser.class));
    // Make sure the new created user has correct data
    assertEquals("newValue", userAfterLogin.get("newKey"));
    assertEquals("newSessionToken", userAfterLogin.getSessionToken());
  }

  //endregion

  //region testlinkWithInBackground

  @Test
  public void testlinkWithInBackgroundWithSaveAsyncSuccess() throws Exception {
    // Register a mock currentUserController to make setCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getCurrentSessionTokenAsync())
        .thenReturn(Task.<String>forResult(null));
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.<ParseUser>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);
    // Register mock callbacks
    AuthenticationCallback callbacks = mock(AuthenticationCallback.class);
    when(callbacks.onRestore(Matchers.<Map<String, String>>any()))
        .thenReturn(true);
    ParseUser.registerAuthenticationCallback("facebook", callbacks);

    ParseUser user = new ParseUser();
    // To make synchronizeAuthData work
    user.setIsCurrentUser(true);
    // To verify stripAnonymity
    user.setObjectId("objectId");
    user.putAuthData(ParseAnonymousUtils.AUTH_TYPE, new HashMap<String, String>());
    ParseUser partialMockUser = spy(user);
    doReturn(Task.<Void>forResult(null))
        .when(partialMockUser)
        .saveAsync(anyString(), eq(false), Matchers.<Task<Void>>any());
    doReturn("sessionTokenAgain")
        .when(partialMockUser)
        .getSessionToken();
    Map<String, String> authData = new HashMap<>();
    authData.put("token", "test");

    ParseTaskUtils.wait(partialMockUser.linkWithInBackground("facebook", authData));

    // Make sure we stripAnonymity
    assertNull(partialMockUser.getAuthData().get(ParseAnonymousUtils.AUTH_TYPE));
    // Make sure new authData is added
    assertSame(authData, partialMockUser.getAuthData().get("facebook"));
    // Make sure we save the user
    verify(partialMockUser, times(1))
        .saveAsync(eq("sessionTokenAgain"), eq(false), Matchers.<Task<Void>>any());
    // Make sure synchronizeAuthData() is called
    verify(callbacks, times(1)).onRestore(authData);
  }

  @Test
  public void testlinkWithInBackgroundWithSaveAsyncFailure() throws Exception {
    // Register a mock currentUserController to make setCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getCurrentSessionTokenAsync())
        .thenReturn(Task.forResult("sessionToken"));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseUser user = new ParseUser();
    Map<String, String> anonymousAuthData = new HashMap<>();
    anonymousAuthData.put("anonymousToken", "anonymousTest");
    // To verify stripAnonymity
    user.setObjectId("objectId");
    user.putAuthData(ParseAnonymousUtils.AUTH_TYPE, anonymousAuthData);
    ParseUser partialMockUser = spy(user);
    Exception saveException = new Exception();
    doReturn(Task.<Void>forError(saveException))
        .when(partialMockUser)
        .saveAsync(anyString(), eq(false), Matchers.<Task<Void>>any());
    doReturn("sessionTokenAgain")
        .when(partialMockUser)
        .getSessionToken();
    String authType = "facebook";
    Map<String, String> authData = new HashMap<>();
    authData.put("facebookToken", "facebookTest");

    Task<Void> linkTask =
        partialMockUser.linkWithInBackground(authType, authData);
    linkTask.waitForCompletion();

    // Make sure new authData is added
    assertSame(authData, partialMockUser.getAuthData().get(authType));
    // Make sure we save the user
    verify(partialMockUser, times(1))
        .saveAsync(eq("sessionTokenAgain"), eq(false), Matchers.<Task<Void>>any());
    // Make sure old authData is restored
    assertSame(anonymousAuthData, partialMockUser.getAuthData().get(ParseAnonymousUtils.AUTH_TYPE));
    // Verify exception
    assertSame(saveException, linkTask.getError());
  }

  //endregion

  //region testResolveLazinessAsync

  @Test
  public void testResolveLazinessAsyncWithAuthDataAndNotNewUser() throws Exception {
    ParseUser user = new ParseUser();
    setLazy(user);
    user.putAuthData("facebook", new HashMap<String, String>());
    // Register a mock userController to make logIn work
    ParseUserController userController = mock(ParseUserController.class);
    ParseUser.State newUserState = new ParseUser.State.Builder()
        .put("newKey", "newValue")
        .sessionToken("newSessionToken")
        .isNew(false)
        .build();
    when(userController.logInAsync(any(ParseUser.State.class), any(ParseOperationSet.class)))
        .thenReturn(Task.forResult(newUserState));
    ParseCorePlugins.getInstance().registerUserController(userController);
    // Register a mock currentUserController to make getCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.setAsync(any(ParseUser.class)))
        .thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseTaskUtils.wait(user.resolveLazinessAsync(Task.<Void>forResult(null)));
    ArgumentCaptor<ParseUser> userAfterResolveLazinessCaptor =
        ArgumentCaptor.forClass(ParseUser.class);

    // Make sure we logIn the lazy user
    verify(userController, times(1)).logInAsync(
        any(ParseUser.State.class), any(ParseOperationSet.class));
    // Make sure we save currentUser
    verify(currentUserController, times(1)).setAsync(userAfterResolveLazinessCaptor.capture());
    ParseUser userAfterResolveLaziness = userAfterResolveLazinessCaptor.getValue();
    // Make sure user's data is correct
    assertEquals("newSessionToken", userAfterResolveLaziness.getSessionToken());
    assertEquals("newValue", userAfterResolveLaziness.get("newKey"));
    // Make sure userAfterResolveLaziness is not lazy
    assertFalse(userAfterResolveLaziness.isLazy());
    // Make sure we create new user
    assertNotSame(user, userAfterResolveLaziness);
  }

  @Test
  public void testResolveLazinessAsyncWithAuthDataAndNewUser() throws Exception {
    ParseUser user = new ParseUser();
    setLazy(user);
    user.putAuthData("facebook", new HashMap<String, String>());
    // Register a mock userController to make logIn work
    ParseUserController userController = mock(ParseUserController.class);
    ParseUser.State newUserState = new ParseUser.State.Builder()
        .objectId("objectId")
        .put("newKey", "newValue")
        .sessionToken("newSessionToken")
        .isNew(true)
        .build();
    when(userController.logInAsync(any(ParseUser.State.class), any(ParseOperationSet.class)))
        .thenReturn(Task.forResult(newUserState));
    ParseCorePlugins.getInstance().registerUserController(userController);
    // Register a mock currentUserController to verify setAsync
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseTaskUtils.wait(user.resolveLazinessAsync(Task.<Void>forResult(null)));

    // Make sure we logIn the lazy user
    verify(userController, times(1)).logInAsync(
        any(ParseUser.State.class), any(ParseOperationSet.class));
    // Make sure we do not save currentUser
    verify(currentUserController, never()).setAsync(any(ParseUser.class));
    // Make sure userAfterResolveLaziness's data is correct
    assertEquals("newSessionToken", user.getSessionToken());
    assertEquals("newValue", user.get("newKey"));
    // Make sure userAfterResolveLaziness is not lazy
    assertFalse(user.isLazy());
  }

  @Test
  public void testResolveLazinessAsyncWithAuthDataAndNotNewUserAndLDSEnabled() throws Exception {
    ParseUser user = new ParseUser();
    setLazy(user);
    user.putAuthData("facebook", new HashMap<String, String>());
    // To verify handleSaveResultAsync is not called
    user.setPassword("password");
    // Register a mock userController to make logIn work
    ParseUserController userController = mock(ParseUserController.class);
    ParseUser.State newUserState = new ParseUser.State.Builder()
        .put("newKey", "newValue")
        .sessionToken("newSessionToken")
        .isNew(false)
        .build();
    when(userController.logInAsync(any(ParseUser.State.class), any(ParseOperationSet.class)))
        .thenReturn(Task.forResult(newUserState));
    ParseCorePlugins.getInstance().registerUserController(userController);
    // Register a mock currentUserController to make getCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.setAsync(any(ParseUser.class)))
        .thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);
    // Enable LDS
    Parse.enableLocalDatastore(null);

    ParseTaskUtils.wait(user.resolveLazinessAsync(Task.<Void>forResult(null)));
    ArgumentCaptor<ParseUser> userAfterResolveLazinessCaptor =
        ArgumentCaptor.forClass(ParseUser.class);

    // Make sure we logIn the lazy user
    verify(userController, times(1)).logInAsync(
        any(ParseUser.State.class), any(ParseOperationSet.class));
    // Make sure handleSaveResultAsync() is not called, if handleSaveResultAsync is called, password
    // field should be cleaned
    assertEquals("password", user.getPassword());
    // Make sure we do not save currentUser
    verify(currentUserController, times(1)).setAsync(userAfterResolveLazinessCaptor.capture());
    ParseUser userAfterResolveLaziness = userAfterResolveLazinessCaptor.getValue();
    // Make sure userAfterResolveLaziness's data is correct
    assertEquals("newSessionToken", userAfterResolveLaziness.getSessionToken());
    assertEquals("newValue", userAfterResolveLaziness.get("newKey"));
    // Make sure userAfterResolveLaziness is not lazy
    assertFalse(userAfterResolveLaziness.isLazy());
    // Make sure we create new user
    assertNotSame(user, userAfterResolveLaziness);
  }

  //endregion

  //region testValidateSave

  @Test
  public void testValidateSaveWithNoObjectId() throws Exception {
    ParseUser user = new ParseUser();

    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Cannot save a ParseUser until it has been signed up. Call signUp first.");

    user.validateSave();
  }

  // TODO(mengyan): Add testValidateSaveWithIsAuthenticatedWithNotDirty

  // TODO(mengyan): Add testValidateSaveWithIsAuthenticatedWithIsCurrentUser

  @Test
  public void testValidateSaveWithLDSNotEnabled() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseUser currentUser = new ParseUser();
    currentUser.setObjectId("test");
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.forResult(currentUser));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseUser user = new ParseUser();
    user.setObjectId("test");
    // Make isDirty return true
    user.put("key", "value");
    // Make isCurrent return false
    user.setIsCurrentUser(false);

    user.validateSave();
  }

  @Test
  public void testValidateSaveWithLDSNotEnabledAndCurrentUserNotMatch() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseUser currentUser = new ParseUser();
    currentUser.setObjectId("testAgain");
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.forResult(currentUser));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseUser user = new ParseUser();
    user.setObjectId("test");
    // Make isDirty return true
    user.put("key", "value");
    // Make isCurrent return false
    user.setIsCurrentUser(false);

    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Cannot save a ParseUser that is not authenticated.");

    user.validateSave();
  }

  //endregion

  //region testSaveAsync

  @Test
  public void testSaveAsyncWithLazyAndCurrentUser() throws Exception {
    // Register a mock currentUserController to make setCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.setAsync(any(ParseUser.class)))
        .thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    // Set facebook authData to null to verify cleanAuthData()
    ParseUser.State userState = new ParseUser.State.Builder()
        .putAuthData("facebook", null)
        .build();
    ParseUser user = ParseObject.from(userState);
    setLazy(user);
    user.setIsCurrentUser(true);
    ParseUser partialMockUser = spy(user);
    doReturn(Task.<Void>forResult(null))
        .when(partialMockUser)
        .resolveLazinessAsync(Matchers.<Task<Void>>any());

    ParseTaskUtils.wait(partialMockUser.saveAsync("sessionToken", Task.<Void>forResult(null)));

    // Make sure we clean authData
    assertFalse(partialMockUser.getAuthData().containsKey("facebook"));
    // Make sure we save new currentUser
    verify(currentUserController, times(1)).setAsync(partialMockUser);
  }

  @Test
  public void testSaveAsyncWithLazyAndNotCurrentUser() throws Exception {
    // Register a mock currentUserController to make setCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.setAsync(any(ParseUser.class)))
        .thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    // Set facebook authData to null to verify cleanAuthData()
    ParseUser.State userState = new ParseUser.State.Builder()
        .putAuthData("facebook", null)
        .build();
    ParseUser user = ParseObject.from(userState);
    setLazy(user);
    user.setIsCurrentUser(false);
    ParseUser partialMockUser = spy(user);
    doReturn(Task.<Void>forResult(null))
        .when(partialMockUser)
        .resolveLazinessAsync(Matchers.<Task<Void>>any());

    ParseTaskUtils.wait(partialMockUser.saveAsync("sessionToken", Task.<Void>forResult(null)));

    // Make sure we do not clean authData
    assertTrue(partialMockUser.getAuthData().containsKey("facebook"));
    // Make sure we do not save new currentUser
    verify(currentUserController, never()).setAsync(partialMockUser);
  }

  // TODO(mengyan): Add testSaveAsyncWithNotLazyAndNotCurrentUser, right now we can not mock
  // super.save()

  //endregion

  //region testLogOutAsync

  @Test
  public void testLogOutAsync() throws Exception {
    // Register a mock sessionController to verify revokeAsync()
    NetworkSessionController sessionController = mock(NetworkSessionController.class);
    when(sessionController.revokeAsync(anyString())).thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerSessionController(sessionController);
    ParseAuthenticationManager manager = mock(ParseAuthenticationManager.class);
    when(manager.deauthenticateAsync(anyString())).thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerAuthenticationManager(manager);

    // Set user initial state
    String facebookAuthType = "facebook";
    Map<String, String> facebookAuthData = new HashMap<>();
    facebookAuthData.put("facebookToken", "facebookTest");
    ParseUser.State userState = new ParseUser.State.Builder()
        .objectId("test")
        .putAuthData(facebookAuthType, facebookAuthData)
        .sessionToken("r:oldSessionToken")
        .build();
    ParseUser user = ParseObject.from(userState);

    ParseTaskUtils.wait(user.logOutAsync());

    verify(manager).deauthenticateAsync("facebook");
    // Verify we revoke session
    verify(sessionController, times(1)).revokeAsync("r:oldSessionToken");
  }

  //endregion

  //region testEnable/UpgradeSessionToken

  @Test
  public void testEnableRevocableSessionInBackgroundWithCurrentUser() throws Exception {
    // Register a mock ParsePlugins to make restClient() work
    ParsePlugins mockPlugins = mock(ParsePlugins.class);
    when(mockPlugins.restClient()).thenReturn(null);
    ParsePlugins.set(mockPlugins);
    // Register a mock currentUserController to verify setAsync
    ParseUser mockUser = mock(ParseUser.class);
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.forResult(mockUser));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseTaskUtils.wait(ParseUser.enableRevocableSessionInBackground());

    verify(currentUserController, times(1)).getAsync(false);
    verify(mockUser, times(1)).upgradeToRevocableSessionAsync();
  }

  @Test
  public void testEnableRevocableSessionInBackgroundWithNoCurrentUser() throws Exception {
    // Register a mock ParsePlugins to make restClient() work
    ParsePlugins mockPlugins = mock(ParsePlugins.class);
    when(mockPlugins.restClient()).thenReturn(null);
    ParsePlugins.set(mockPlugins);
    // Register a mock currentUserController to verify setAsync
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.<ParseUser>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseTaskUtils.wait(ParseUser.enableRevocableSessionInBackground());

    verify(currentUserController, times(1)).getAsync(false);
  }

  @Test
  public void testUpgradeToRevocableSessionAsync() throws Exception {
    // Register a mock currentUserController to verify setAsync
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);
    // Register a mock sessionController to verify revokeAsync()
    NetworkSessionController sessionController = mock(NetworkSessionController.class);
    ParseSession.State state = new ParseSession.State.Builder("_Session")
        .put("sessionToken", "r:newSessionToken")
        .build();
    when(sessionController.upgradeToRevocable(anyString()))
        .thenReturn(Task.forResult(state));
    ParseCorePlugins.getInstance().registerSessionController(sessionController);

    // Set user initial state
    ParseUser.State userState = new ParseUser.State.Builder()
        .objectId("test")
        .sessionToken("oldSessionToken")
        .build();
    ParseUser user = ParseObject.from(userState);

    ParseTaskUtils.wait(user.upgradeToRevocableSessionAsync());

    // Make sure we update to new sessionToken
    assertEquals("r:newSessionToken", user.getSessionToken());
    // Make sure we update currentUser
    verify(currentUserController, times(1)).setAsync(user);
  }

  @Test
  public void testDontOverwriteSessionTokenForCurrentUser() throws Exception {
    ParseUser.State sessionTokenState = new ParseUser.State.Builder()
            .sessionToken("sessionToken")
            .put("key0", "value0")
            .put("key1", "value1")
            .isComplete(true)
            .build();
    ParseUser.State newState = new ParseUser.State.Builder()
            .put("key0", "newValue0")
            .put("key2", "value2")
            .isComplete(true)
            .build();
    ParseUser.State emptyState = new ParseUser.State.Builder().isComplete(true).build();

    ParseUser user = ParseObject.from(sessionTokenState);
    user.setIsCurrentUser(true);
    assertEquals(user.getSessionToken(), "sessionToken");
    assertEquals(user.getString("key0"), "value0");
    assertEquals(user.getString("key1"), "value1");

    user.setState(newState);
    assertEquals(user.getSessionToken(), "sessionToken");
    assertEquals(user.getString("key0"), "newValue0");
    assertNull(user.getString("key1"));
    assertEquals(user.getString("key2"), "value2");

    user.setIsCurrentUser(false);
    user.setState(emptyState);
    assertNull(user.getSessionToken());
    assertNull(user.getString("key0"));
    assertNull(user.getString("key1"));
    assertNull(user.getString("key2"));
  }

  //endregion

  //region testUnlinkFromAsync

  @Test
  public void testUnlinkFromAsyncWithAuthType() throws Exception {
    // Register a mock currentUserController to make getAsync work
    ParseUser mockUser = mock(ParseUser.class);
    when(mockUser.getSessionToken()).thenReturn("sessionToken");
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync()).thenReturn(Task.forResult(mockUser));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    // Set user initial state
    String authType = "facebook";
    Map<String, String> authData = new HashMap<>();
    authData.put("facebookToken", "facebookTest");
    ParseUser.State userState = new ParseUser.State.Builder()
        .objectId("test")
        .putAuthData(authType, authData)
        .build();
    ParseUser user = ParseObject.from(userState);
    ParseUser partialMockUser = spy(user);
    doReturn(Task.<Void>forResult(null))
        .when(partialMockUser)
        .saveAsync(anyString(), Matchers.<Task<Void>>any());

    ParseTaskUtils.wait(partialMockUser.unlinkFromInBackground(authType));

    // Verify we delete authData
    assertNull(user.getAuthData().get("facebook"));
    // Verify we save the user
    verify(partialMockUser, times(1)).saveAsync(eq("sessionToken"), Matchers.<Task<Void>>any());
  }

  //endregion

  //region testLogin

  @Test
  public void testLogInInWithNoUserName() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Must specify a username for the user to log in with");

    ParseTaskUtils.wait(ParseUser.logInInBackground(null, "password"));
  }

  @Test
  public void testLogInWithNoPassword() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Must specify a password for the user to log in with");

    ParseTaskUtils.wait(ParseUser.logInInBackground("userName", null));
  }

  @Test
  public void testLogIn() throws Exception {
    // Register a mock currentUserController to make setCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.setAsync(any(ParseUser.class)))
        .thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);
    // Register a mock userController to make logIn work
    ParseUserController userController = mock(ParseUserController.class);
    ParseUser.State newUserState = new ParseUser.State.Builder()
        .put("newKey", "newValue")
        .sessionToken("newSessionToken")
        .build();
    when(userController.logInAsync(anyString(), anyString()))
        .thenReturn(Task.forResult(newUserState));
    ParseCorePlugins.getInstance().registerUserController(userController);

    ParseUser user = ParseUser.logIn("userName", "password");

    // Make sure user is login
    verify(userController, times(1)).logInAsync("userName", "password");
    // Make sure we set currentUser
    verify(currentUserController, times(1)).setAsync(user);
    // Make sure user's data is correct
    assertEquals("newSessionToken", user.getSessionToken());
    assertEquals("newValue", user.get("newKey"));
  }

  @Test
  public void testLogInWithCallback() throws Exception {
    // Register a mock currentUserController to make setCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.setAsync(any(ParseUser.class)))
        .thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);
    // Register a mock userController to make logIn work
    ParseUserController userController = mock(ParseUserController.class);
    ParseUser.State newUserState = new ParseUser.State.Builder()
        .put("newKey", "newValue")
        .sessionToken("newSessionToken")
        .build();
    when(userController.logInAsync(anyString(), anyString()))
        .thenReturn(Task.forResult(newUserState));
    ParseCorePlugins.getInstance().registerUserController(userController);

    final Semaphore done = new Semaphore(0);
    ParseUser.logInInBackground("userName", "password", new LogInCallback() {
      @Override
      public void done(ParseUser user, ParseException e) {
        done.release();
        assertNull(e);
        // Make sure user's data is correct
        assertEquals("newSessionToken", user.getSessionToken());
        assertEquals("newValue", user.get("newKey"));
      }
    });

    assertTrue(done.tryAcquire(5, TimeUnit.SECONDS));
    // Make sure user is login
    verify(userController, times(1)).logInAsync("userName", "password");
    // Make sure we set currentUser
    verify(currentUserController, times(1)).setAsync(any(ParseUser.class));
  }

  //endregion

  //region testBecome

  @Test
  public void testBecomeWithNoSessionToken() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Must specify a sessionToken for the user to log in with");

    ParseUser.become(null);
  }

  @Test
  public void testBecome() throws Exception {
    // Register a mock currentUserController to make setCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.setAsync(any(ParseUser.class)))
        .thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);
    // Register a mock userController to make getUsreAsync work
    ParseUserController userController = mock(ParseUserController.class);
    ParseUser.State newUserState = new ParseUser.State.Builder()
        .put("key", "value")
        .sessionToken("sessionToken")
        .build();
    when(userController.getUserAsync(anyString()))
        .thenReturn(Task.forResult(newUserState));
    ParseCorePlugins.getInstance().registerUserController(userController);

    ParseUser user = ParseUser.become("sessionToken");

    // Make sure we call getUserAsync
    verify(userController, times(1)).getUserAsync("sessionToken");
    // Make sure we set currentUser
    verify(currentUserController, times(1)).setAsync(user);
    // Make sure user's data is correct
    assertEquals("sessionToken", user.getSessionToken());
    assertEquals("value", user.get("key"));
  }

  @Test
  public void testBecomeWithCallback() throws Exception {
    // Register a mock currentUserController to make setCurrentUser work
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.setAsync(any(ParseUser.class)))
        .thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);
    // Register a mock userController to make getUsreAsync work
    ParseUserController userController = mock(ParseUserController.class);
    ParseUser.State newUserState = new ParseUser.State.Builder()
        .put("key", "value")
        .sessionToken("sessionToken")
        .build();
    when(userController.getUserAsync(anyString()))
        .thenReturn(Task.forResult(newUserState));
    ParseCorePlugins.getInstance().registerUserController(userController);

    final Semaphore done = new Semaphore(0);
    ParseUser.becomeInBackground("sessionToken", new LogInCallback() {
      @Override
      public void done(ParseUser user, ParseException e) {
        done.release();
        assertNull(e);
        // Make sure user's data is correct
        assertEquals("sessionToken", user.getSessionToken());
        assertEquals("value", user.get("key"));
      }
    });

    // Make sure we call getUserAsync
    verify(userController, times(1)).getUserAsync("sessionToken");
    // Make sure we set currentUser
    verify(currentUserController, times(1)).setAsync(any(ParseUser.class));
  }

  //endregion

  //region testToRest

  @Test
  public void testToRest() throws Exception {
    ParseUser user = new ParseUser();
    user.setUsername("userName");
    user.setPassword("password");

    JSONObject json = user.toRest(user.getState(), user.operationSetQueue, PointerEncoder.get());

    // Make sure we delete password operations
    assertFalse(json.getJSONArray("__operations").getJSONObject(0).has("password"));
    // Make sure we have username operations
    assertEquals(
        "userName", json.getJSONArray("__operations").getJSONObject(0).getString("username"));
  }

  //endregion

  //region testValidateDelete

  @Test
  public void testValidDelete() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseUser currentUser = new ParseUser();
    currentUser.setObjectId("test");
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.forResult(currentUser));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseUser user = new ParseUser();
    user.setObjectId("test");
    // Make isDirty return true
    user.put("key", "value");

    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Cannot delete a ParseUser that is not authenticated.");

    user.validateDelete();
  }

  //endregion

  //region testValidateDelete

  @Test
  public void testValidateSaveEventually() throws Exception {
    ParseUser user = new ParseUser();
    user.setPassword("password");

    thrown.expect(ParseException.class);
    thrown.expectMessage("Unable to saveEventually on a ParseUser with dirty password");

    user.validateSaveEventually();
  }

  //endregion

  //region testSynchronizeAuthData

  @Test
  public void testSynchronizeAuthData() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseUser currentUser = new ParseUser();
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.forResult(currentUser));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);
    // Register mock callbacks
    AuthenticationCallback callbacks = mock(AuthenticationCallback.class);
    when(callbacks.onRestore(Matchers.<Map<String, String>>any()))
        .thenReturn(true);
    ParseUser.registerAuthenticationCallback("facebook", callbacks);

    // Set user initial state
    String authType = "facebook";
    Map<String, String> authData = new HashMap<>();
    authData.put("facebookToken", "facebookTest");
    ParseUser.State userState = new ParseUser.State.Builder()
        .putAuthData(authType, authData)
        .build();
    ParseUser user = ParseObject.from(userState);
    user.setIsCurrentUser(true);

    ParseTaskUtils.wait(user.synchronizeAuthDataAsync(authType));

    // Make sure we restore authentication
    verify(callbacks, times(1)).onRestore(authData);
  }

  @Test
  public void testSynchronizeAllAuthData() throws Exception {
    // Register a mock currentUserController to make getCurrentUser work
    ParseUser currentUser = new ParseUser();
    ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
    when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.forResult(currentUser));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);
    // Register mock callbacks
    AuthenticationCallback callbacks = mock(AuthenticationCallback.class);
    when(callbacks.onRestore(Matchers.<Map<String, String>>any()))
        .thenReturn(true);
    ParseUser.registerAuthenticationCallback("facebook", callbacks);

    // Set user initial state
    String facebookAuthType = "facebook";
    Map<String, String> facebookAuthData = new HashMap<>();
    facebookAuthData.put("facebookToken", "facebookTest");
    ParseUser.State userState = new ParseUser.State.Builder()
        .putAuthData(facebookAuthType, facebookAuthData)
        .build();
    ParseUser user = ParseObject.from(userState);
    user.setIsCurrentUser(true);

    ParseTaskUtils.wait(user.synchronizeAllAuthDataAsync());

    // Make sure we restore authentication
    verify(callbacks, times(1)).onRestore(facebookAuthData);
  }

  //endregion

  //region testAutomaticUser

  @Test
  public void testAutomaticUser() throws Exception {
    new ParseUser();

    ParseUser.disableAutomaticUser();
    assertFalse(ParseUser.isAutomaticUserEnabled());

    ParseUser.enableAutomaticUser();
    assertTrue(ParseUser.isAutomaticUserEnabled());
  }

  //endregion

  //region testAutomaticUser

  @Test
  public void testPinCurrentUserIfNeededAsyncWithNoLDSEnabled() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Method requires Local Datastore.");

    ParseUser.pinCurrentUserIfNeededAsync(new ParseUser());
  }

  //endregion

  //region testPinCurrentUserIfNeededAsync

  @Test
  public void testPinCurrentUserIfNeededAsync() throws Exception {
    // Enable LDS
    Parse.enableLocalDatastore(null);
    // Register a mock currentUserController to make getCurrentUser work
    ParseUser currentUser = new ParseUser();
    currentUser.setObjectId("test");
    CachedCurrentUserController currentUserController = mock(CachedCurrentUserController.class);
    when(currentUserController.setIfNeededAsync(any(ParseUser.class)))
        .thenReturn(Task.<Void>forResult(null));
    ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

    ParseUser user = new ParseUser();
    ParseUser.pinCurrentUserIfNeededAsync(user);

    // Make sure we pin the user
    verify(currentUserController, times(1)).setIfNeededAsync(user);
  }

  //endregion

  //region testRemove

  @Test
  public void testRemoveWithUserName() throws Exception {
    ParseUser user = new ParseUser();

    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Can't remove the username key.");

    user.remove("username");
  }

  //endregion

  //region testSetState

  @Test
  public void testSetCurrentUserStateWithoutAuthData() throws Exception {
    // Set user initial state
    String authType = "facebook";
    Map<String, String> authData = new HashMap<>();
    authData.put("facebookToken", "facebookTest");
    ParseUser.State userState = new ParseUser.State.Builder()
        .objectId("test")
        .put("oldKey", "oldValue")
        .put("key", "value")
        .putAuthData(authType, authData)
        .build();
    ParseUser user = ParseObject.from(userState);
    user.setIsCurrentUser(true);
    // Build new state
    ParseUser.State newUserState = new ParseUser.State.Builder()
        .objectId("testAgain")
        .put("key", "valueAgain")
        .build();

    user.setState(newUserState);

    // Make sure we keep the authData
    assertEquals(1, user.getAuthData().size());
    assertEquals(authData, user.getAuthData().get(authType));
    // Make sure old state is replaced
    assertFalse(user.has("oldKey"));
    // Make sure new state is set
    assertEquals("testAgain", user.getObjectId());
    assertEquals("valueAgain", user.get("key"));
  }

  @Test
  public void testSetStateDoesNotAddNonExistentAuthData() throws Exception {
    // Set user initial state
    ParseUser.State userState = new ParseUser.State.Builder()
        .objectId("test")
        .put("oldKey", "oldValue")
        .put("key", "value")
        .build();
    ParseUser user = ParseObject.from(userState);
    user.setIsCurrentUser(true);
    // Build new state
    ParseUser.State newUserState = new ParseUser.State.Builder()
        .objectId("testAgain")
        .put("key", "valueAgain")
        .build();

    user.setState(newUserState);

    // Make sure we do not add authData when it did not exist before
    assertFalse(user.keySet().contains("authData"));
    assertEquals(1, user.keySet().size());
    assertEquals(0, user.getAuthData().size());
    // Make sure old state is replaced
    assertFalse(user.has("oldKey"));
    // Make sure new state is set
    assertEquals("testAgain", user.getObjectId());
    assertEquals("valueAgain", user.get("key"));
  }

  //endregion

  private static void setLazy(ParseUser user) {
    Map<String, String> anonymousAuthData = new HashMap<>();
    anonymousAuthData.put("anonymousToken", "anonymousTest");
    user.putAuthData(ParseAnonymousUtils.AUTH_TYPE, anonymousAuthData);
  }
}
