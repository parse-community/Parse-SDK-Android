/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.twitter;

import android.content.Context;

import com.parse.AuthenticationCallback;
import com.parse.ParseUser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import com.parse.boltsinternal.Task;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ParseTwitterUtilsTest {

    @Mock
    private TwitterController controller;
    @Mock
    private ParseTwitterUtils.ParseUserDelegate userDelegate;

    @Before
    public void setUp() {
        ParseTwitterUtils.controller = controller;
        ParseTwitterUtils.userDelegate = userDelegate;
    }

    @After
    public void tearDown() {
        ParseTwitterUtils.isInitialized = false;
        ParseTwitterUtils.controller = null;
        ParseTwitterUtils.userDelegate = null;
    }

    @Test
    public void testInitialize() {
        ParseTwitterUtils.initialize("test_key", "test_secret");
        assertTrue(ParseTwitterUtils.isInitialized);
        verify(controller).initialize("test_key", "test_secret");
        verify(userDelegate)
                .registerAuthenticationCallback(eq("twitter"), any(AuthenticationCallback.class));
    }

    //region testRestoreAuthentication

    @Test
    public void testRestoreAuthentication() {
        ParseTwitterUtils.initialize(null, null);
        ArgumentCaptor<AuthenticationCallback> captor =
                ArgumentCaptor.forClass(AuthenticationCallback.class);
        verify(userDelegate).registerAuthenticationCallback(eq("twitter"), captor.capture());

        AuthenticationCallback callback = captor.getValue();
        Map<String, String> authData = new HashMap<>();
        assertTrue(callback.onRestore(authData));
        verify(controller).setAuthData(authData);
    }

    @Test
    public void testRestoreAuthenticationFailure() {
        ParseTwitterUtils.initialize(null, null);
        ArgumentCaptor<AuthenticationCallback> captor =
                ArgumentCaptor.forClass(AuthenticationCallback.class);
        verify(userDelegate).registerAuthenticationCallback(eq("twitter"), captor.capture());

        doThrow(new RuntimeException())
                .when(controller)
                .setAuthData(anyMapOf(String.class, String.class));
        AuthenticationCallback callback = captor.getValue();
        Map<String, String> authData = new HashMap<>();
        assertFalse(callback.onRestore(authData));
        verify(controller).setAuthData(authData);
    }

    //endregion

    @Test
    public void testIsLinked() {
        ParseUser user = mock(ParseUser.class);
        when(user.isLinked(anyString())).thenReturn(true);

        assertTrue(ParseTwitterUtils.isLinked(user));
        verify(user).isLinked("twitter");
    }

    //region testLogIn

    @Test
    @SuppressWarnings("unchecked")
    public void testLogInWithToken() {
        ParseTwitterUtils.isInitialized = true;
        ParseUser user = mock(ParseUser.class);
        when(userDelegate.logInWithInBackground(anyString(), anyMapOf(String.class, String.class)))
                .thenReturn(Task.forResult(user));

        String twitterId = "test_id";
        String screenName = "test_screen_name";
        String authToken = "test_token";
        String authSecret = "test_secret";
        Task<ParseUser> task = ParseTwitterUtils.logInInBackground(
                twitterId, screenName, authToken, authSecret);
        verify(controller).getAuthData(twitterId, screenName, authToken, authSecret);
        verify(userDelegate).logInWithInBackground(eq("twitter"), anyMapOf(String.class, String.class));
        assertTrue(task.isCompleted());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLogInWithContext() {
        ParseTwitterUtils.isInitialized = true;
        ParseUser user = mock(ParseUser.class);
        Map<String, String> authData = new HashMap<>();
        when(controller.authenticateAsync(any(Context.class))).thenReturn(Task.forResult(authData));
        when(userDelegate.logInWithInBackground(anyString(), anyMapOf(String.class, String.class)))
                .thenReturn(Task.forResult(user));

        Context context = mock(Context.class);
        Task<ParseUser> task = ParseTwitterUtils.logInInBackground(context);
        verify(controller).authenticateAsync(context);
        verify(userDelegate).logInWithInBackground("twitter", authData);
        assertTrue(task.isCompleted());
    }

    //endregion

    //region testLink

    @Test
    @SuppressWarnings("unchecked")
    public void testLinkWithToken() {
        ParseTwitterUtils.isInitialized = true;

        ParseUser user = mock(ParseUser.class);
        when(user.linkWithInBackground(anyString(), anyMapOf(String.class, String.class)))
                .thenReturn(Task.<Void>forResult(null));
        String twitterId = "test_id";
        String screenName = "test_screen_name";
        String authToken = "test_token";
        String authSecret = "test_secret";
        Task<Void> task = ParseTwitterUtils.linkInBackground(
                user, twitterId, screenName, authToken, authSecret);
        verify(controller).getAuthData(twitterId, screenName, authToken, authSecret);
        verify(user).linkWithInBackground(eq("twitter"), anyMapOf(String.class, String.class));
        assertTrue(task.isCompleted());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLinkWithContext() {
        ParseTwitterUtils.isInitialized = true;
        Map<String, String> authData = new HashMap<>();
        when(controller.authenticateAsync(any(Context.class))).thenReturn(Task.forResult(authData));

        Context context = mock(Context.class);
        ParseUser user = mock(ParseUser.class);
        when(user.linkWithInBackground(anyString(), anyMapOf(String.class, String.class)))
                .thenReturn(Task.<Void>forResult(null));
        Task<Void> task = ParseTwitterUtils.linkInBackground(context, user);
        verify(controller).authenticateAsync(context);
        verify(user).linkWithInBackground("twitter", authData);
        assertTrue(task.isCompleted());
    }

    //endregion

    @Test
    public void testUnlink() {
        ParseTwitterUtils.isInitialized = true;
        ParseUser user = mock(ParseUser.class);
        when(user.unlinkFromInBackground(anyString())).thenReturn(Task.<Void>forResult(null));
        Task<Void> task = ParseTwitterUtils.unlinkInBackground(user);
        verify(user).unlinkFromInBackground("twitter");
        verifyNoMoreInteractions(user);
        assertTrue(task.isCompleted());
    }
}
