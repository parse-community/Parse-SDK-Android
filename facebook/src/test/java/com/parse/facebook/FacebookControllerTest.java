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

import androidx.fragment.app.Fragment;

import com.facebook.AccessToken;
import com.facebook.AccessTokenSource;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import com.parse.boltsinternal.Task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Config(manifest=Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class FacebookControllerTest {

    private Locale defaultLocale;
    private TimeZone defaultTimeZone;

    @Before
    public void setUp() {
        defaultLocale = Locale.getDefault();
        defaultTimeZone = TimeZone.getDefault();
    }

    @After
    public void tearDown() {
        Locale.setDefault(defaultLocale);
        TimeZone.setDefault(defaultTimeZone);
    }

    @Test
    public void testInitialize() {
        FacebookController.FacebookSdkDelegate facebookSdk =
                mock(FacebookController.FacebookSdkDelegate.class);
        FacebookController controller = new FacebookController(facebookSdk);

        Context context = mock(Context.class);
        int callbackRequestCodeOffset = 1234;
        controller.initialize(context, callbackRequestCodeOffset);
        verify(facebookSdk).initialize(context, callbackRequestCodeOffset);
    }

    @Test
    public void testGetAuthData() {
        Locale.setDefault(new Locale("ar")); // Mimic the device's locale
        TimeZone.setDefault(TimeZone.getTimeZone("PST"));

        FacebookController controller = new FacebookController(null);
        Calendar calendar = new GregorianCalendar(2015, 6, 3);
        Set<String> permissions = new HashSet<String>();
        permissions.add("user_friends");
        permissions.add("profile");

        AccessToken accessToken = new AccessToken(
                "access_token",
                "application_id",
                "user_id",
                permissions,
                null,
                null,
                AccessTokenSource.DEVICE_AUTH,
                calendar.getTime(),
                null, null);

        Map<String, String> authData = controller.getAuthData(accessToken);
        assertEquals("user_id", authData.get("id"));
        assertEquals("access_token", authData.get("access_token"));
        assertEquals("2015-07-03T07:00:00.000Z", authData.get("expiration_date"));
        assertEquals("profile,user_friends", authData.get("permissions"));
    }

    //region testSetAuthData

    @Test
    public void testSetAuthDataWithNull() throws java.text.ParseException {
        FacebookController.FacebookSdkDelegate facebookSdk =
                mock(FacebookController.FacebookSdkDelegate.class);
        LoginManager loginManager = mock(LoginManager.class);
        when(facebookSdk.getLoginManager()).thenReturn(loginManager);
        FacebookController controller = new FacebookController(facebookSdk);

        controller.setAuthData(null);
        verify(facebookSdk).getLoginManager();
        verifyNoMoreInteractions(facebookSdk);
        verify(loginManager).logOut();
        verifyNoMoreInteractions(loginManager);
    }

    @Test
    public void testSetAuthData() throws ParseException {
        Locale.setDefault(new Locale("ar")); // Mimic the device's locale
        TimeZone.setDefault(TimeZone.getTimeZone("PST"));

        FacebookController.FacebookSdkDelegate facebookSdk =
                mock(FacebookController.FacebookSdkDelegate.class);
        when(facebookSdk.getApplicationId()).thenReturn("test_application_id");
        FacebookController controller = new FacebookController(facebookSdk);

        Map<String, String> authData = new HashMap<>();
        authData.put("id", "test_id");
        authData.put("access_token", "test_token");
        authData.put("expiration_date", "2015-07-03T07:00:00.000Z");
        authData.put("last_refresh_date", "2015-07-03T07:00:00.000Z");
        controller.setAuthData(authData);
        ArgumentCaptor<AccessToken> accessTokenCapture = ArgumentCaptor.forClass(AccessToken.class);
        verify(facebookSdk).setCurrentAccessToken(accessTokenCapture.capture());
        AccessToken accessToken = accessTokenCapture.getValue();
        assertEquals("test_id", accessToken.getUserId());
        assertEquals("test_token", accessToken.getToken());
        assertEquals(new GregorianCalendar(2015, 6, 3).getTime(), accessToken.getExpires());
        assertEquals("test_application_id", accessToken.getApplicationId());
    }

    @Test
    public void testSetAuthDataWithDifferentAccessToken() throws ParseException {
        FacebookController.FacebookSdkDelegate facebookSdk =
                mock(FacebookController.FacebookSdkDelegate.class);
        when(facebookSdk.getApplicationId()).thenReturn("test_application_id");
        AccessToken accessToken = TestUtils.newAccessToken();
        when(facebookSdk.getCurrentAccessToken()).thenReturn(accessToken);
        FacebookController controller = new FacebookController(facebookSdk);

        Map<String, String> authData = new HashMap<>();
        authData.put("id", "new_id");
        authData.put("access_token", "test_token");
        authData.put("expiration_date", "2015-07-03T07:00:00.000Z");
        authData.put("last_refresh_date", "2050-07-03T07:00:00.000Z");
        controller.setAuthData(authData);
        verify(facebookSdk, times(1)).setCurrentAccessToken(any(AccessToken.class));

        authData.put("id", "new_id");
        authData.put("access_token", "new_token");
        authData.put("expiration_date", "2015-07-03T07:00:00.000Z");
        controller.setAuthData(authData);
        verify(facebookSdk, times(2)).setCurrentAccessToken(any(AccessToken.class));
    }

    @Test
    public void testSetAuthDataFacebookAccessTokenIsNewer() throws ParseException {
        FacebookController.FacebookSdkDelegate facebookSdk =
                mock(FacebookController.FacebookSdkDelegate.class);
        when(facebookSdk.getApplicationId()).thenReturn("test_application_id");
        AccessToken accessToken = TestUtils.newAccessToken();
        when(facebookSdk.getCurrentAccessToken()).thenReturn(accessToken);
        FacebookController controller = new FacebookController(facebookSdk);

        Map<String, String> authData = new HashMap<>();
        authData.put("id", "new_id");
        authData.put("access_token", "test_token");
        authData.put("expiration_date", "2015-07-03T07:00:00.000Z");
        authData.put("last_refresh_date", "2015-07-03T07:00:00.000Z");
        controller.setAuthData(authData);
        verify(facebookSdk, never()).setCurrentAccessToken(any(AccessToken.class));
    }

    @Test
    public void testSetAuthDataWithSameAccessToken() throws ParseException {
        FacebookController.FacebookSdkDelegate facebookSdk =
                mock(FacebookController.FacebookSdkDelegate.class);
        AccessToken accessToken = TestUtils.newAccessToken();
        when(facebookSdk.getCurrentAccessToken()).thenReturn(accessToken);
        FacebookController controller = new FacebookController(facebookSdk);

        Map<String, String> authData = new HashMap<>();
        authData.put("id", "test_id");
        authData.put("access_token", "test_token");
        authData.put("expiration_date", "2015-07-03T07:00:00.000Z");
        authData.put("last_refresh_date", "2015-07-03T07:00:00.000Z");
        controller.setAuthData(authData);
        verify(facebookSdk, never()).setCurrentAccessToken(any(AccessToken.class));
    }

    @Test
    public void testSetAuthDataWithImpriciseDateFormat() throws ParseException {
        Locale.setDefault(new Locale("ar")); // Mimic the device's locale
        TimeZone.setDefault(TimeZone.getTimeZone("PST"));

        FacebookController.FacebookSdkDelegate facebookSdk =
                mock(FacebookController.FacebookSdkDelegate.class);
        when(facebookSdk.getApplicationId()).thenReturn("test_application_id");
        FacebookController controller = new FacebookController(facebookSdk);

        Map<String, String> authData = new HashMap<>();
        authData.put("id", "test_id");
        authData.put("access_token", "test_token");
        authData.put("expiration_date", "2015-07-03T07:00:00Z");
        authData.put("last_refresh_date", "2015-07-03T07:00:00Z");
        controller.setAuthData(authData);
        ArgumentCaptor<AccessToken> accessTokenCapture = ArgumentCaptor.forClass(AccessToken.class);
        verify(facebookSdk).setCurrentAccessToken(accessTokenCapture.capture());
        AccessToken accessToken = accessTokenCapture.getValue();
        assertEquals("test_id", accessToken.getUserId());
        assertEquals("test_token", accessToken.getToken());
        assertEquals(new GregorianCalendar(2015, 6, 3).getTime(), accessToken.getExpires());
        assertEquals("test_application_id", accessToken.getApplicationId());
    }

    @Test
    public void testSetAuthDataWithNoRefreshDate() throws ParseException {
        Locale.setDefault(new Locale("ar")); // Mimic the device's locale
        TimeZone.setDefault(TimeZone.getTimeZone("PST"));

        FacebookController.FacebookSdkDelegate facebookSdk =
                mock(FacebookController.FacebookSdkDelegate.class);
        when(facebookSdk.getApplicationId()).thenReturn("test_application_id");
        FacebookController controller = new FacebookController(facebookSdk);

        Map<String, String> authData = new HashMap<>();
        authData.put("id", "test_id");
        authData.put("access_token", "test_token");
        authData.put("expiration_date", "2015-07-03T07:00:00Z");
        controller.setAuthData(authData);
        ArgumentCaptor<AccessToken> accessTokenCapture = ArgumentCaptor.forClass(AccessToken.class);
        verify(facebookSdk).setCurrentAccessToken(accessTokenCapture.capture());
        AccessToken accessToken = accessTokenCapture.getValue();
        assertEquals("test_id", accessToken.getUserId());
        assertEquals("test_token", accessToken.getToken());
        assertEquals(new GregorianCalendar(2015, 6, 3).getTime(), accessToken.getExpires());
        assertEquals("test_application_id", accessToken.getApplicationId());
    }

    //endregion

    //region testAuthenticateAsync

    @Test
    public void testAuthenticateAsyncWithActivityAndReadPermissions() {
        doAuthenticateAsyncWith(
                mock(Activity.class),
                null,
                FacebookController.LoginAuthorizationType.READ);
    }

    @Test
    public void testAuthenticateAsyncWithFragmentAndReadPermissions() {
        doAuthenticateAsyncWith(
                null,
                mock(Fragment.class),
                FacebookController.LoginAuthorizationType.READ);
    }

    @Test
    public void testAuthenticateAsyncWithActivityAndPublishPermissions() {
        doAuthenticateAsyncWith(
                mock(Activity.class),
                null,
                FacebookController.LoginAuthorizationType.PUBLISH);
    }

    @Test
    public void testAuthenticateAsyncWithFragmentAndPublishPermissions() {
        doAuthenticateAsyncWith(
                null,
                mock(Fragment.class),
                FacebookController.LoginAuthorizationType.PUBLISH);
    }

    @SuppressWarnings("unchecked")
    public void doAuthenticateAsyncWith(
            Activity activity,
            Fragment fragment,
            FacebookController.LoginAuthorizationType type) {
        FacebookController.FacebookSdkDelegate facebookSdk =
                mock(FacebookController.FacebookSdkDelegate.class);
        LoginManager loginManager = mock(LoginManager.class);
        CallbackManager callbackManager = mock(CallbackManager.class);
        when(facebookSdk.getLoginManager()).thenReturn(loginManager);
        when(facebookSdk.createCallbackManager()).thenReturn(callbackManager);
        FacebookController controller = new FacebookController(facebookSdk);

        Collection<String> permissions = new ArrayList<>();
        Task<Map<String, String>> task = controller.authenticateAsync(
                activity, fragment, type, permissions);
        ArgumentCaptor<FacebookCallback> callbackCapture =
                ArgumentCaptor.forClass(FacebookCallback.class);
        verify(loginManager).registerCallback(eq(callbackManager),
                (FacebookCallback<LoginResult>) callbackCapture.capture());
        if (FacebookController.LoginAuthorizationType.PUBLISH.equals(type)) {
            if (activity != null) {
                verify(loginManager).logInWithPublishPermissions(activity, permissions);
            }
            if (fragment != null) {
                verify(loginManager).logInWithPublishPermissions(fragment, permissions);
            }
        } else {
            if (activity != null) {
                verify(loginManager).logInWithReadPermissions(activity, permissions);
            }
            if (fragment != null) {
                verify(loginManager).logInWithReadPermissions(fragment, permissions);
            }
        }

        controller.onActivityResult(-1, -1, null);
        verify(callbackManager).onActivityResult(-1, -1, null);

        FacebookCallback<LoginResult> callback = callbackCapture.getValue();
        LoginResult loginResult = mock(LoginResult.class);
        AccessToken accessToken = TestUtils.newAccessToken();
        when(loginResult.getAccessToken()).thenReturn(accessToken);
        callback.onSuccess(loginResult);
        assertTrue(task.isCompleted());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAuthenticateAsyncCancel() {
        FacebookController.FacebookSdkDelegate facebookSdk =
                mock(FacebookController.FacebookSdkDelegate.class);
        LoginManager loginManager = mock(LoginManager.class);
        CallbackManager callbackManager = mock(CallbackManager.class);
        when(facebookSdk.getLoginManager()).thenReturn(loginManager);
        when(facebookSdk.createCallbackManager()).thenReturn(callbackManager);
        FacebookController controller = new FacebookController(facebookSdk);

        Collection<String> permissions = new ArrayList<>();
        Task<Map<String, String>> task = controller.authenticateAsync(
                mock(Activity.class), null, FacebookController.LoginAuthorizationType.READ, permissions);
        ArgumentCaptor<FacebookCallback> callbackCapture =
                ArgumentCaptor.forClass(FacebookCallback.class);
        verify(loginManager).registerCallback(eq(callbackManager),
                (FacebookCallback<LoginResult>) callbackCapture.capture());

        FacebookCallback<LoginResult> callback = callbackCapture.getValue();
        callback.onCancel();
        assertTrue(task.isCancelled());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAuthenticateAsyncFailure() {
        FacebookController.FacebookSdkDelegate facebookSdk =
                mock(FacebookController.FacebookSdkDelegate.class);
        LoginManager loginManager = mock(LoginManager.class);
        CallbackManager callbackManager = mock(CallbackManager.class);
        when(facebookSdk.getLoginManager()).thenReturn(loginManager);
        when(facebookSdk.createCallbackManager()).thenReturn(callbackManager);
        FacebookController controller = new FacebookController(facebookSdk);

        Collection<String> permissions = new ArrayList<>();
        Task<Map<String, String>> task = controller.authenticateAsync(
                mock(Activity.class), null, FacebookController.LoginAuthorizationType.READ, permissions);
        ArgumentCaptor<FacebookCallback> callbackCapture =
                ArgumentCaptor.forClass(FacebookCallback.class);
        verify(loginManager).registerCallback(eq(callbackManager),
                (FacebookCallback<LoginResult>) callbackCapture.capture());

        FacebookCallback<LoginResult> callback = callbackCapture.getValue();
        callback.onError(mock(FacebookException.class));
        assertTrue(task.isFaulted());
    }

    //endregion
}
