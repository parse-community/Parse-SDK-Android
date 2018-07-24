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

import java.lang.ref.WeakReference;

import bolts.Task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ParseDefaultACLControllerTest {

    @Before
    public void setUp() {
        ParseObject.registerSubclass(ParseRole.class);
    }

    @After
    public void tearDown() {
        ParseObject.unregisterSubclass(ParseRole.class);
        ParseCorePlugins.getInstance().reset();
    }

    //region testSetDefaultACL

    @Test
    public void testSetDefaultACLWithACL() {
        ParseACL acl = mock(ParseACL.class);
        ParseACL copiedACL = mock(ParseACL.class);
        when(acl.copy()).thenReturn(copiedACL);
        ParseDefaultACLController controller = new ParseDefaultACLController();

        controller.set(acl, true);

        assertNull(controller.defaultACLWithCurrentUser);
        assertNull(controller.lastCurrentUser);
        assertTrue(controller.defaultACLUsesCurrentUser);
        verify(copiedACL, times(1)).setShared(true);
        assertEquals(copiedACL, controller.defaultACL);
    }

    @Test
    public void testSetDefaultACLWithNull() {
        ParseDefaultACLController controller = new ParseDefaultACLController();

        controller.set(null, true);

        assertNull(controller.defaultACLWithCurrentUser);
        assertNull(controller.lastCurrentUser);
        assertNull(controller.defaultACL);
    }

    //endregion

    //region testGetDefaultACL

    @Test
    public void testGetDefaultACLWithNoDefaultACL() {
        ParseDefaultACLController controller = new ParseDefaultACLController();

        ParseACL defaultACL = controller.get();

        assertNull(defaultACL);
    }

    @Test
    public void testGetDefaultACLWithNoDefaultACLUsesCurrentUser() {
        ParseDefaultACLController controller = new ParseDefaultACLController();
        ParseACL acl = new ParseACL();
        controller.defaultACL = acl;
        controller.defaultACLUsesCurrentUser = false;

        ParseACL defaultACL = controller.get();

        assertSame(acl, defaultACL);
    }

    @Test
    public void testGetDefaultACLWithNoCurrentUser() {
        ParseDefaultACLController controller = new ParseDefaultACLController();
        ParseACL acl = new ParseACL();
        controller.defaultACL = acl;
        controller.defaultACLUsesCurrentUser = true;
        // Register currentUser
        ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
        when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.<ParseUser>forResult(null));
        ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

        ParseACL defaultACL = controller.get();

        assertSame(acl, defaultACL);
    }

    @Test
    public void testGetDefaultACLWithSameCurrentUserAndLastCurrentUser() {
        ParseDefaultACLController controller = new ParseDefaultACLController();
        ParseACL acl = new ParseACL();
        controller.defaultACL = acl;
        controller.defaultACLUsesCurrentUser = true;
        ParseACL aclAgain = new ParseACL();
        controller.defaultACLWithCurrentUser = aclAgain;
        ParseUser currentUser = mock(ParseUser.class);
        controller.lastCurrentUser = new WeakReference<>(currentUser);
        // Register currentUser
        ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
        when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.forResult(currentUser));
        ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

        ParseACL defaultACL = controller.get();

        assertNotSame(acl, defaultACL);
        assertSame(aclAgain, defaultACL);
    }

    @Test
    public void testGetDefaultACLWithCurrentUserAndLastCurrentUserNotSame() {
        ParseDefaultACLController controller = new ParseDefaultACLController();
        ParseACL acl = mock(ParseACL.class);
        ParseACL copiedACL = mock(ParseACL.class);
        when(acl.copy()).thenReturn(copiedACL);
        controller.defaultACL = acl;
        controller.defaultACLUsesCurrentUser = true;
        controller.defaultACLWithCurrentUser = new ParseACL();
        // Register currentUser
        ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
        ParseUser currentUser = mock(ParseUser.class);
        when(currentUserController.getAsync(anyBoolean())).thenReturn(Task.forResult(currentUser));
        ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

        ParseACL defaultACL = controller.get();

        verify(copiedACL, times(1)).setShared(true);
        verify(copiedACL, times(1)).setReadAccess(eq(currentUser), eq(true));
        verify(copiedACL, times(1)).setWriteAccess(eq(currentUser), eq(true));
        assertSame(currentUser, controller.lastCurrentUser.get());
        assertNotSame(acl, defaultACL);
        assertSame(copiedACL, defaultACL);
    }

    //endregion
}
