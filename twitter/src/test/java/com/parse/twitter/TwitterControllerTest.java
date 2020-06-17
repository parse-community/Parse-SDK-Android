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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import com.parse.boltsinternal.Task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TwitterControllerTest {

    @Mock
    private Twitter twitter;

    @Before
    public void setUp() {
        when(twitter.setConsumerKey(anyString())).thenReturn(twitter);
        when(twitter.setConsumerSecret(anyString())).thenReturn(twitter);
    }

    @Test
    public void testInitialize() {
        TwitterController controller = new TwitterController(twitter);

        controller.initialize("test_key", "test_secret");
        verify(twitter).setConsumerKey("test_key");
        verify(twitter).setConsumerSecret("test_secret");
    }

    @Test
    public void testGetTwitter() {
        TwitterController controller = new TwitterController(twitter);
        assertSame(twitter, controller.getTwitter());
    }

    //region testAuthenticateAsync

    @Test
    public void testAuthenticateAsync() {
        when(twitter.getConsumerKey()).thenReturn("test_key");
        when(twitter.getConsumerSecret()).thenReturn("test_secret");
        when(twitter.getUserId()).thenReturn("test_id");
        when(twitter.getScreenName()).thenReturn("test_screen_name");
        when(twitter.getAuthToken()).thenReturn("test_token");
        when(twitter.getAuthTokenSecret()).thenReturn("test_secret");
        TwitterController controller = new TwitterController(twitter);

        Context context = mock(Context.class);
        Task<Map<String, String>> task = controller.authenticateAsync(context);
        ArgumentCaptor<AsyncCallback> captor = ArgumentCaptor.forClass(AsyncCallback.class);
        verify(twitter).authorize(eq(context), captor.capture());
        AsyncCallback callback = captor.getValue();
        callback.onSuccess(twitter);
        assertTrue(task.isCompleted());
        Map<String, String> authData = task.getResult();
        assertEquals("test_key", authData.get("consumer_key"));
        assertEquals("test_secret", authData.get("consumer_secret"));
        assertEquals("test_id", authData.get("id"));
        assertEquals("test_screen_name", authData.get("screen_name"));
        assertEquals("test_token", authData.get("auth_token"));
        assertEquals("test_secret", authData.get("auth_token_secret"));
    }

    @Test
    public void testAuthenticateAsyncCancel() {
        TwitterController controller = new TwitterController(twitter);

        Context context = mock(Context.class);
        Task<Map<String, String>> task = controller.authenticateAsync(context);
        ArgumentCaptor<AsyncCallback> captor = ArgumentCaptor.forClass(AsyncCallback.class);
        verify(twitter).authorize(eq(context), captor.capture());
        AsyncCallback callback = captor.getValue();
        callback.onCancel();
        assertTrue(task.isCancelled());
    }

    @Test
    public void testAuthenticateAsyncFailure() {
        TwitterController controller = new TwitterController(twitter);

        Context context = mock(Context.class);
        Task<Map<String, String>> task = controller.authenticateAsync(context);
        ArgumentCaptor<AsyncCallback> captor = ArgumentCaptor.forClass(AsyncCallback.class);
        verify(twitter).authorize(eq(context), captor.capture());
        AsyncCallback callback = captor.getValue();
        callback.onFailure(new RuntimeException());
        assertTrue(task.isFaulted());
    }

    //endregion

    //region testSetAuthData

    @Test
    public void testSetAuthData() {
        TwitterController controller = new TwitterController(twitter);

        Map<String, String> authData = new HashMap<>();
        authData.put("id", "test_id");
        authData.put("screen_name", "test_screen_name");
        authData.put("auth_token", "test_token");
        authData.put("auth_token_secret", "test_secret");
        controller.setAuthData(authData);
        verify(twitter).setUserId("test_id");
        verify(twitter).setScreenName("test_screen_name");
        verify(twitter).setAuthToken("test_token");
        verify(twitter).setAuthTokenSecret("test_secret");
        verifyNoMoreInteractions(twitter);
    }

    @Test
    public void testSetAuthDataNull() {
        TwitterController controller = new TwitterController(twitter);

        controller.setAuthData(null);
        verify(twitter).setUserId(null);
        verify(twitter).setScreenName(null);
        verify(twitter).setAuthToken(null);
        verify(twitter).setAuthTokenSecret(null);
        verifyNoMoreInteractions(twitter);
    }

    //endregion
}
