/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Matchers;

import java.util.HashMap;
import java.util.Map;

import com.parse.boltsinternal.Task;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ParseAuthenticationManagerTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private ParseAuthenticationManager manager;
    private ParseCurrentUserController controller;
    private AuthenticationCallback provider;

    @Before
    public void setUp() {
        controller = mock(ParseCurrentUserController.class);
        manager = new ParseAuthenticationManager(controller);
        provider = mock(AuthenticationCallback.class);
    }

    //region testRegister

    @Test
    public void testRegisterMultipleShouldThrow() {
        when(controller.getAsync(false)).thenReturn(Task.<ParseUser>forResult(null));
        AuthenticationCallback provider2 = mock(AuthenticationCallback.class);

        manager.register("test_provider", provider);

        thrown.expect(IllegalStateException.class);
        manager.register("test_provider", provider2);
    }

    @Test
    public void testRegisterAnonymous() {
        manager.register("anonymous", mock(AuthenticationCallback.class));
        verifyNoMoreInteractions(controller);
    }

    @Test
    public void testRegister() {
        ParseUser user = mock(ParseUser.class);
        when(controller.getAsync(false)).thenReturn(Task.forResult(user));

        manager.register("test_provider", provider);
        verify(controller).getAsync(false);
        verify(user).synchronizeAuthDataAsync("test_provider");
    }

    //endregion

    @Test
    public void testRestoreAuthentication() throws ParseException {
        when(controller.getAsync(false)).thenReturn(Task.<ParseUser>forResult(null));
        when(provider.onRestore(Matchers.<Map<String, String>>any()))
                .thenReturn(true);
        manager.register("test_provider", provider);

        Map<String, String> authData = new HashMap<>();
        ParseTaskUtils.wait(manager.restoreAuthenticationAsync("test_provider", authData));

        verify(provider).onRestore(authData);
    }

    @Test
    public void testDeauthenticateAsync() throws ParseException {
        when(controller.getAsync(false)).thenReturn(Task.<ParseUser>forResult(null));
        manager.register("test_provider", provider);

        ParseTaskUtils.wait(manager.deauthenticateAsync("test_provider"));

        verify(provider).onRestore(null);
    }
}
