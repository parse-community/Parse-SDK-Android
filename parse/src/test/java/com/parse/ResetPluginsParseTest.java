package com.parse;

import org.junit.After;
import org.junit.Before;

/**
 * Automatically takes care of setup and teardown of plugins between tests. The order tests run in can be
 * different, so if you see a test failing randomly, the test before it may not be tearing down
 * properly, and you may need to have the test class subclass this class.
 */
class ResetPluginsParseTest {

    @Before
    public void setUp() throws Exception {
        ParseCorePlugins.getInstance().reset();
        ParsePlugins.reset();
    }

    @After
    public void tearDown() throws Exception {
        ParseCorePlugins.getInstance().reset();
        ParsePlugins.reset();
    }
}
