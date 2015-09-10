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

import bolts.Task;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ParseAuthenticationManagerTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private ParseAuthenticationManager manager;
  private ParseCurrentUserController controller;
  private ParseAuthenticationProvider provider;

  @Before
  public void setUp() {
    controller = mock(ParseCurrentUserController.class);
    manager = new ParseAuthenticationManager(controller);
    provider = mock(ParseAuthenticationProvider.class);
    when(provider.getAuthType()).thenReturn("test_provider");
  }

  //region testRegister

  @Test
  public void testRegisterMultipleShouldThrow() {
    when(controller.getAsync(false)).thenReturn(Task.<ParseUser>forResult(null));
    ParseAuthenticationProvider provider2 = mock(ParseAuthenticationProvider.class);
    when(provider2.getAuthType()).thenReturn("test_provider");

    manager.register(provider);

    thrown.expect(IllegalStateException.class);
    manager.register(provider2);
  }

  @Test
  public void testRegisterAnonymous() {
    ParseAuthenticationProvider anonymous = mock(AnonymousAuthenticationProvider.class);
    when(anonymous.getAuthType()).thenReturn("anonymous");

    manager.register(anonymous);
    verifyNoMoreInteractions(controller);
  }

  @Test
  public void testRegister() {
    ParseUser user = mock(ParseUser.class);
    when(controller.getAsync(false)).thenReturn(Task.forResult(user));

    manager.register(provider);
    verify(controller).getAsync(false);
    verify(user).synchronizeAuthDataAsync("test_provider");
  }

  //endregion

  @Test
  public void testRestoreAuthentication() throws ParseException {
    when(controller.getAsync(false)).thenReturn(Task.<ParseUser>forResult(null));
    when(provider.restoreAuthenticationAsync(Matchers.<Map<String, String>>any()))
        .thenReturn(Task.forResult(true));
    manager.register(provider);

    Map<String, String> authData = new HashMap<>();
    assertTrue(ParseTaskUtils.wait(manager.restoreAuthenticationAsync("test_provider", authData)));

    verify(provider).restoreAuthenticationAsync(authData);
  }

  @Test
  public void testDeauthenticateAsync() throws ParseException {
    when(controller.getAsync(false)).thenReturn(Task.<ParseUser>forResult(null));
    when(provider.deauthenticateAsync()).thenReturn(Task.<Void>forResult(null));
    manager.register(provider);

    ParseTaskUtils.wait(manager.deauthenticateAsync("test_provider"));

    verify(provider).deauthenticateAsync();
  }
}
