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

import com.parse.boltsinternal.Task;
import com.parse.boltsinternal.TaskCompletionSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CachedCurrentInstallationControllerTest {

    private static final String KEY_DEVICE_TYPE = "deviceType";

    @Before
    public void setUp() {
        ParseObject.registerSubclass(ParseInstallation.class);
    }

    @After
    public void tearDown() {
        ParseObject.unregisterSubclass(ParseInstallation.class);
    }

    //region testSetAsync

    @Test
    public void testSetAsyncWithNotCurrentInstallation() throws Exception {
        CachedCurrentInstallationController controller =
                new CachedCurrentInstallationController(null, null);

        ParseInstallation currentInstallationInMemory = mock(ParseInstallation.class);
        controller.currentInstallation = currentInstallationInMemory;
        ParseInstallation testInstallation = mock(ParseInstallation.class);

        ParseTaskUtils.wait(controller.setAsync(testInstallation));

        // Make sure the in memory currentInstallation not change
        assertSame(currentInstallationInMemory, controller.currentInstallation);
        assertNotSame(controller.currentInstallation, testInstallation);
    }

    @Test
    public void testSetAsyncWithCurrentInstallation() throws Exception {
        InstallationId installationId = mock(InstallationId.class);
        //noinspection unchecked
        ParseObjectStore<ParseInstallation> store = mock(ParseObjectStore.class);

        // Create test controller
        CachedCurrentInstallationController controller =
                new CachedCurrentInstallationController(store, installationId);
        ParseInstallation currentInstallation = mock(ParseInstallation.class);
        when(currentInstallation.getInstallationId()).thenReturn("testInstallationId");
        controller.currentInstallation = currentInstallation;

        ParseTaskUtils.wait(controller.setAsync(currentInstallation));

        // Verify that we persist it
        verify(store, times(1)).setAsync(currentInstallation);
        // Make sure installationId is updated
        verify(installationId, times(1)).set("testInstallationId");
    }

    //endregion

    //region testGetAsync

    @Test
    public void testGetAsyncFromMemory() throws Exception {
        // Create test controller
        CachedCurrentInstallationController controller =
                new CachedCurrentInstallationController(null, null);

        ParseInstallation currentInstallationInMemory = new ParseInstallation();
        controller.currentInstallation = currentInstallationInMemory;

        ParseInstallation currentInstallation = ParseTaskUtils.wait(controller.getAsync());

        assertSame(currentInstallationInMemory, currentInstallation);
    }

    @Test
    public void testGetAsyncFromStore() throws Exception {
        // Mock installationId
        InstallationId installationId = mock(InstallationId.class);
        //noinspection unchecked
        ParseObjectStore<ParseInstallation> store = mock(ParseObjectStore.class);
        ParseInstallation installation = mock(ParseInstallation.class);
        when(installation.getInstallationId()).thenReturn("testInstallationId");
        when(store.getAsync()).thenReturn(Task.forResult(installation));

        // Create test controller
        CachedCurrentInstallationController controller =
                new CachedCurrentInstallationController(store, installationId);

        ParseInstallation currentInstallation = ParseTaskUtils.wait(controller.getAsync());

        verify(store, times(1)).getAsync();
        // Make sure installationId is updated
        verify(installationId, times(1)).set("testInstallationId");
        // Make sure controller state is update to date
        assertSame(installation, controller.currentInstallation);
        // Make sure the installation we get is correct
        assertSame(installation, currentInstallation);
    }

    @Test
    public void testGetAsyncWithNoInstallation() throws Exception {
        // Mock installationId
        InstallationId installationId = mock(InstallationId.class);
        when(installationId.get()).thenReturn("testInstallationId");
        //noinspection unchecked
        ParseObjectStore<ParseInstallation> store = mock(ParseObjectStore.class);
        when(store.getAsync()).thenReturn(Task.<ParseInstallation>forResult(null));

        // Create test controller
        CachedCurrentInstallationController controller =
                new CachedCurrentInstallationController(store, installationId);

        ParseInstallation currentInstallation = ParseTaskUtils.wait(controller.getAsync());

        verify(store, times(1)).getAsync();
        // Make sure controller state is update to date
        assertSame(controller.currentInstallation, currentInstallation);
        // Make sure device info is updated
        assertEquals("testInstallationId", currentInstallation.getInstallationId());
        assertEquals("android", currentInstallation.get(KEY_DEVICE_TYPE));
    }

    @Test
    public void testGetAsyncWithNoInstallationRaceCondition() throws ParseException {
        // Mock installationId
        InstallationId installationId = mock(InstallationId.class);
        when(installationId.get()).thenReturn("testInstallationId");
        //noinspection unchecked
        ParseObjectStore<ParseInstallation> store = mock(ParseObjectStore.class);
        TaskCompletionSource<ParseInstallation> tcs = new TaskCompletionSource();
        when(store.getAsync()).thenReturn(tcs.getTask());

        // Create test controller
        CachedCurrentInstallationController controller =
                new CachedCurrentInstallationController(store, installationId);

        Task<ParseInstallation> taskA = controller.getAsync();
        Task<ParseInstallation> taskB = controller.getAsync();

        tcs.setResult(null);
        ParseInstallation installationA = ParseTaskUtils.wait(taskA);
        ParseInstallation installationB = ParseTaskUtils.wait(taskB);

        verify(store, times(1)).getAsync();
        assertSame(controller.currentInstallation, installationA);
        assertSame(controller.currentInstallation, installationB);
        // Make sure device info is updated
        assertEquals("testInstallationId", installationA.getInstallationId());
        assertEquals("android", installationA.get(KEY_DEVICE_TYPE));
    }

    //endregion

    //region testExistsAsync

    @Test
    public void testExistAsyncFromMemory() throws Exception {
        // Create test controller
        CachedCurrentInstallationController controller =
                new CachedCurrentInstallationController(null, null);
        controller.currentInstallation = mock(ParseInstallation.class);

        assertTrue(ParseTaskUtils.wait(controller.existsAsync()));
    }

    @Test
    public void testExistAsyncFromStore() throws Exception {
        //noinspection unchecked
        ParseObjectStore<ParseInstallation> store = mock(ParseObjectStore.class);
        when(store.existsAsync()).thenReturn(Task.forResult(true));

        // Create test controller
        CachedCurrentInstallationController controller =
                new CachedCurrentInstallationController(store, null);

        assertTrue(ParseTaskUtils.wait(controller.existsAsync()));
        verify(store, times(1)).existsAsync();
    }

    //endregion

    @Test
    public void testClearFromMemory() {
        CachedCurrentInstallationController controller =
                new CachedCurrentInstallationController(null, null);
        controller.currentInstallation = mock(ParseInstallation.class);

        controller.clearFromMemory();

        assertNull(controller.currentInstallation);
    }

    @Test
    public void testClearFromDisk() {
        // Mock installationId
        InstallationId installationId = mock(InstallationId.class);
        //noinspection unchecked
        ParseObjectStore<ParseInstallation> store = mock(ParseObjectStore.class);
        when(store.deleteAsync()).thenReturn(Task.<Void>forResult(null));

        CachedCurrentInstallationController controller =
                new CachedCurrentInstallationController(store, installationId);
        controller.currentInstallation = mock(ParseInstallation.class);

        controller.clearFromDisk();

        assertNull(controller.currentInstallation);
        // Make sure the in LDS currentInstallation is cleared
        verify(store, times(1)).deleteAsync();
        // Make sure installationId is cleared
        verify(installationId, times(1)).clear();
    }

    @Test
    public void testIsCurrent() {
        // Create test controller
        CachedCurrentInstallationController controller =
                new CachedCurrentInstallationController(null, null);
        ParseInstallation installation = mock(ParseInstallation.class);
        controller.currentInstallation = installation;

        assertTrue(controller.isCurrent(installation));
        assertFalse(controller.isCurrent(new ParseInstallation()));
    }
}