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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import com.parse.boltsinternal.Task;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseCorePluginsTest extends ResetPluginsParseTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        Parse.Configuration configuration = new Parse.Configuration.Builder(null)
                .applicationId("1234")
                .build();
        ParsePlugins.initialize(null, configuration);
    }

    @Test
    public void testQueryControllerDefaultImpl() {
        ParseQueryController controller = ParseCorePlugins.getInstance().getQueryController();
        assertThat(controller, instanceOf(CacheQueryController.class));
    }

    @Test
    public void testRegisterQueryController() {
        ParseQueryController controller = new TestQueryController();
        ParseCorePlugins.getInstance().registerQueryController(controller);
        assertSame(controller, ParseCorePlugins.getInstance().getQueryController());
    }

    @Test(expected = IllegalStateException.class)
    public void testRegisterQueryControllerWhenAlreadySet() {
        ParseCorePlugins.getInstance().getQueryController(); // sets to default
        ParseQueryController controller = new TestQueryController();
        ParseCorePlugins.getInstance().registerQueryController(controller);
    }

    //TODO(grantland): testFileControllerDefaultImpl with ParseFileController interface

    @Test
    public void testRegisterFileController() {
        ParseFileController controller = new TestFileController();
        ParseCorePlugins.getInstance().registerFileController(controller);
        assertSame(controller, ParseCorePlugins.getInstance().getFileController());
    }

    //TODO(grantland): testRegisterFileControllerWhenAlreadySet when getCacheDir is no longer global

    //TODO(mengyan): testAnalyticsControllerDefaultImpl when getEventuallyQueue is no longer global

    @Test
    public void testRegisterAnalyticsController() {
        ParseAnalyticsController controller = new TestAnalyticsController();
        ParseCorePlugins.getInstance().registerAnalyticsController(controller);
        assertSame(controller, ParseCorePlugins.getInstance().getAnalyticsController());
    }

    //TODO(mengyan): testRegisterAnalyticsControllerWhenAlreadySet when getEventuallyQueue is no longer global

    @Test
    public void testCloudCodeControllerDefaultImpl() {
        ParseCloudCodeController controller = ParseCorePlugins.getInstance().getCloudCodeController();
        assertThat(controller, instanceOf(ParseCloudCodeController.class));
    }

    @Test
    public void testRegisterCloudCodeController() {
        ParseCloudCodeController controller = new TestCloudCodeController();
        ParseCorePlugins.getInstance().registerCloudCodeController(controller);
        assertSame(controller, ParseCorePlugins.getInstance().getCloudCodeController());
    }

    @Test(expected = IllegalStateException.class)
    public void testRegisterCloudCodeControllerWhenAlreadySet() {
        ParseCorePlugins.getInstance().getCloudCodeController(); // sets to default
        ParseCloudCodeController controller = new TestCloudCodeController();
        ParseCorePlugins.getInstance().registerCloudCodeController(controller);
    }

    // TODO(mengyan): testConfigControllerDefaultImpl when getCacheDir is no longer global

    @Test
    public void testRegisterConfigController() {
        ParseConfigController controller = new TestConfigController();
        ParseCorePlugins.getInstance().registerConfigController(controller);
        assertSame(controller, ParseCorePlugins.getInstance().getConfigController());
    }

    // TODO(mengyan): testRegisterConfigControllerWhenAlreadySet when getCacheDir is no longer global

    @Test
    public void testPushControllerDefaultImpl() {
        ParsePushController controller = ParseCorePlugins.getInstance().getPushController();
        assertThat(controller, instanceOf(ParsePushController.class));
    }

    @Test
    public void testRegisterPushController() {
        ParsePushController controller = new TestPushController();
        ParseCorePlugins.getInstance().registerPushController(controller);
        assertSame(controller, ParseCorePlugins.getInstance().getPushController());
    }

    @Test(expected = IllegalStateException.class)
    public void testRegisterPushControllerWhenAlreadySet() {
        ParseCorePlugins.getInstance().getPushController(); // sets to default
        ParsePushController controller = new TestPushController();
        ParseCorePlugins.getInstance().registerPushController(controller);
    }

    public void testPushChannelsControllerDefaultImpl() {
        ParsePushChannelsController controller =
                ParseCorePlugins.getInstance().getPushChannelsController();
        assertThat(controller, instanceOf(ParsePushChannelsController.class));
    }

    @Test
    public void testRegisterPushChannelsController() {
        ParsePushChannelsController controller = new ParsePushChannelsController();
        ParseCorePlugins.getInstance().registerPushChannelsController(controller);
        assertSame(controller, ParseCorePlugins.getInstance().getPushChannelsController());
    }

    @Test(expected = IllegalStateException.class)
    public void testRegisterPushChannelsControllerWhenAlreadySet() {
        ParseCorePlugins.getInstance().getPushChannelsController(); // sets to default
        ParsePushChannelsController controller = new ParsePushChannelsController();
        ParseCorePlugins.getInstance().registerPushChannelsController(controller);
    }

    private static class TestQueryController implements ParseQueryController {
        @Override
        public <T extends ParseObject> Task<List<T>> findAsync(
                ParseQuery.State<T> state, ParseUser user, Task<Void> cancellationToken) {
            return null;
        }

        @Override
        public <T extends ParseObject> Task<Integer> countAsync(
                ParseQuery.State<T> state, ParseUser user, Task<Void> cancellationToken) {
            return null;
        }

        @Override
        public <T extends ParseObject> Task<T> getFirstAsync(
                ParseQuery.State<T> state, ParseUser user, Task<Void> cancellationToken) {
            return null;
        }
    }

    private static class TestFileController extends ParseFileController {
        public TestFileController() {
            super(null, null);
        }
    }

    private static class TestAnalyticsController extends ParseAnalyticsController {
        public TestAnalyticsController() {
            super(null);
        }
    }

    private static class TestCloudCodeController extends ParseCloudCodeController {
        public TestCloudCodeController() {
            super(null);
        }
    }

    private static class TestConfigController extends ParseConfigController {
        public TestConfigController() {
            super(null, null);
        }
    }

    private static class TestPushController extends ParsePushController {
        public TestPushController() {
            super(null);
        }
    }
}
