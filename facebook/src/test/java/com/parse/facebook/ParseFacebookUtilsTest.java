/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.facebook;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.fragment.app.Fragment;

import com.facebook.AccessToken;
import com.parse.AuthenticationCallback;
import com.parse.ParseUser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.parse.boltsinternal.Task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(manifest=Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class ParseFacebookUtilsTest {

    @Mock
    private FacebookController controller;
    @Mock
    private ParseFacebookUtils.ParseUserDelegate userDelegate;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ParseFacebookUtils.controller = controller;
        ParseFacebookUtils.userDelegate = userDelegate;

    }

    @After
    public void tearDown() {
        ParseFacebookUtils.controller = null;
        ParseFacebookUtils.userDelegate = null;
    }

    //region testInitialize

    @Test
    public void testInitialize() {
        Context context = mock(Context.class);
        ParseFacebookUtils.initialize(context);
        verify(controller).initialize(context, 0xface);
        verify(userDelegate)
                .registerAuthenticationCallback(eq("facebook"), any(AuthenticationCallback.class));
        assertTrue(ParseFacebookUtils.isInitialized);
    }

    @Test
    public void testInitializeWithCallbackRequestCodeOffset() {
        Context context = mock(Context.class);
        int callbackRequestCodeOffset = 1234;
        ParseFacebookUtils.initialize(context, callbackRequestCodeOffset);
        verify(controller).initialize(context, callbackRequestCodeOffset);
        verify(userDelegate)
                .registerAuthenticationCallback(eq("facebook"), any(AuthenticationCallback.class));
        assertTrue(ParseFacebookUtils.isInitialized);
    }

    //endregion

    //region testRestoreAuthentication

    @Test
    public void testRestoreAuthentication() throws java.text.ParseException {
        ParseFacebookUtils.initialize(null);
        ArgumentCaptor<AuthenticationCallback> callbackCaptor =
                ArgumentCaptor.forClass(AuthenticationCallback.class);
        verify(userDelegate).registerAuthenticationCallback(eq("facebook"), callbackCaptor.capture());
        AuthenticationCallback callback = callbackCaptor.getValue();
        Map<String, String> authData = new HashMap<>();

        assertTrue(callback.onRestore(authData));
        verify(controller).setAuthData(authData);
    }

    @Test
    public void testRestoreAuthenticationFailure() throws java.text.ParseException {
        ParseFacebookUtils.initialize(null);
        ArgumentCaptor<AuthenticationCallback> callbackCaptor =
                ArgumentCaptor.forClass(AuthenticationCallback.class);
        verify(userDelegate).registerAuthenticationCallback(eq("facebook"), callbackCaptor.capture());
        AuthenticationCallback callback = callbackCaptor.getValue();
        Map<String, String> authData = new HashMap<>();
        doThrow(new RuntimeException())
                .when(controller)
                .setAuthData(anyMapOf(String.class, String.class));

        assertFalse(callback.onRestore(authData));
        verify(controller).setAuthData(authData);
    }

    //endregion

    @Test
    public void testIsLinked() {
        ParseUser user = mock(ParseUser.class);
        when(user.isLinked(anyString())).thenReturn(true);

        assertTrue(ParseFacebookUtils.isLinked(user));
        verify(user).isLinked("facebook");
    }

    @Test
    public void testOnActivityResult() {
        int requestCode = 1;
        int resultCode = 2;
        Intent data = mock(Intent.class);
        FacebookController controller = ParseFacebookUtils.controller;
        ParseFacebookUtils.controller = null;
        ParseFacebookUtils.onActivityResult(requestCode, resultCode, data);
        verify(controller, never()).onActivityResult(anyInt(), anyInt(), any(Intent.class));

        ParseFacebookUtils.controller = controller;
        ParseFacebookUtils.onActivityResult(requestCode, resultCode, data);
        verify(controller).onActivityResult(requestCode, resultCode, data);
    }

    //region testLogIn

    @Test
    public void testLogInWithAccessToken() {
        Map<String, String> authData = new HashMap<>();
        when(controller.getAuthData(any(AccessToken.class))).thenReturn(authData);
        ParseFacebookUtils.isInitialized = true;

        ParseUser user = mock(ParseUser.class);
        when(userDelegate.logInWithInBackground(anyString(), anyMapOf(String.class, String.class)))
                .thenReturn(Task.forResult(user));
        AccessToken token = TestUtils.newAccessToken();
        Task<ParseUser> task = ParseFacebookUtils.logInInBackground(token);
        verify(controller).getAuthData(token);
        verify(userDelegate).logInWithInBackground("facebook", authData);
        assertTrue(task.isCompleted());
        assertEquals(user, task.getResult());
    }

    @Test
    public void testLogInWithActivityAndReadPermissions() {
        doLogInWith(
                mock(Activity.class),
                null,
                new LinkedList<String>(),
                FacebookController.LoginAuthorizationType.READ);
    }

    @Test
    public void testLogInWithFragmentAndReadPermissions() {
        doLogInWith(
                null,
                mock(Fragment.class),
                new LinkedList<String>(),
                FacebookController.LoginAuthorizationType.READ);
    }

    @Test
    public void testLogInWithActivityAndPublishPermissions() {
        doLogInWith(
                mock(Activity.class),
                null,
                new LinkedList<String>(),
                FacebookController.LoginAuthorizationType.PUBLISH);
    }

    @Test
    public void testLogInWithFragmentAndPublishPermissions() {
        doLogInWith(
                null,
                mock(Fragment.class),
                new LinkedList<String>(),
                FacebookController.LoginAuthorizationType.PUBLISH);
    }

    private void doLogInWith(
            Activity activity,
            Fragment fragment,
            Collection<String> permissions,
            FacebookController.LoginAuthorizationType type) {
        assertFalse(
                "Cannot run test with both Activity and Fragment", activity != null && fragment != null);

        Map<String, String> authData = new HashMap<>();
        when(controller.authenticateAsync(
                any(Activity.class),
                any(Fragment.class),
                any(FacebookController.LoginAuthorizationType.class),
                anyListOf(String.class))).thenReturn(Task.forResult(authData));
        ParseFacebookUtils.isInitialized = true;

        ParseUser user = mock(ParseUser.class);
        when(userDelegate.logInWithInBackground(anyString(), anyMapOf(String.class, String.class)))
                .thenReturn(Task.forResult(user));
        Task<ParseUser> task;
        if (FacebookController.LoginAuthorizationType.PUBLISH.equals(type)) {
            if (activity != null) {
                task = ParseFacebookUtils.logInWithPublishPermissionsInBackground(activity, permissions);
            } else {
                task = ParseFacebookUtils.logInWithPublishPermissionsInBackground(fragment, permissions);
            }
        } else {
            if (activity != null) {
                task = ParseFacebookUtils.logInWithReadPermissionsInBackground(activity, permissions);
            } else {
                task = ParseFacebookUtils.logInWithReadPermissionsInBackground(fragment, permissions);
            }
        }
        verify(controller).authenticateAsync(activity, fragment, type, permissions);
        verify(userDelegate).logInWithInBackground("facebook", authData);
        assertTrue(task.isCompleted());
        assertEquals(user, task.getResult());
    }

    //endregion

    //region testLink

    @Test
    public void testLinkWithAccessToken() {
        Map<String, String> authData = new HashMap<>();
        when(controller.getAuthData(any(AccessToken.class))).thenReturn(authData);
        ParseFacebookUtils.isInitialized = true;

        ParseUser user = mock(ParseUser.class);
        when(user.linkWithInBackground(anyString(), anyMapOf(String.class, String.class)))
                .thenReturn(Task.<Void>forResult(null));
        AccessToken token = TestUtils.newAccessToken();
        Task<Void> task = ParseFacebookUtils.linkInBackground(user, token);
        verify(controller).getAuthData(token);
        verify(user).linkWithInBackground("facebook", authData);
        assertTrue(task.isCompleted());
    }

    @Test
    public void testLinkWithActivityAndReadPermissions() {
        doLinkWith(
                mock(Activity.class),
                null,
                new LinkedList<String>(),
                FacebookController.LoginAuthorizationType.READ);
    }

    @Test
    public void testLinkWithFragmentAndReadPermissions() {
        doLinkWith(
                null,
                mock(Fragment.class),
                new LinkedList<String>(),
                FacebookController.LoginAuthorizationType.READ);
    }

    @Test
    public void testLinkWithActivityAndPublishPermissions() {
        doLinkWith(
                mock(Activity.class),
                null,
                new LinkedList<String>(),
                FacebookController.LoginAuthorizationType.PUBLISH);
    }

    @Test
    public void testLinkWithFragmentAndPublishPermissions() {
        doLinkWith(
                null,
                mock(Fragment.class),
                new LinkedList<String>(),
                FacebookController.LoginAuthorizationType.PUBLISH);
    }

    private void doLinkWith(
            Activity activity,
            Fragment fragment,
            Collection<String> permissions,
            FacebookController.LoginAuthorizationType type) {
        assertFalse(
                "Cannot run test with both Activity and Fragment", activity != null && fragment != null);

        Map<String, String> authData = new HashMap<>();
        when(controller.authenticateAsync(
                any(Activity.class),
                any(Fragment.class),
                any(FacebookController.LoginAuthorizationType.class),
                anyListOf(String.class))).thenReturn(Task.forResult(authData));
        ParseFacebookUtils.isInitialized = true;

        ParseUser user = mock(ParseUser.class);
        when(user.linkWithInBackground(anyString(), anyMapOf(String.class, String.class)))
                .thenReturn(Task.<Void>forResult(null));
        Task<Void> task;
        if (FacebookController.LoginAuthorizationType.PUBLISH.equals(type)) {
            if (activity != null) {
                task = ParseFacebookUtils.linkWithPublishPermissionsInBackground(
                        user, activity, permissions);
            } else {
                task = ParseFacebookUtils.linkWithPublishPermissionsInBackground(
                        user, fragment, permissions);
            }
        } else {
            if (activity != null) {
                task = ParseFacebookUtils.linkWithReadPermissionsInBackground(user, activity, permissions);
            } else {
                task = ParseFacebookUtils.linkWithReadPermissionsInBackground(user, fragment, permissions);
            }
        }
        verify(controller).authenticateAsync(activity, fragment, type, permissions);
        verify(user).linkWithInBackground("facebook", authData);
        assertTrue(task.isCompleted());
    }

    //endregion

    @Test
    public void testUnlinkInBackground() {
        ParseUser user = mock(ParseUser.class);
        when(user.unlinkFromInBackground(anyString())).thenReturn(Task.<Void>forResult(null));
        ParseFacebookUtils.isInitialized = true;

        ParseFacebookUtils.unlinkInBackground(user);
        verify(user).unlinkFromInBackground("facebook");
    }
}
