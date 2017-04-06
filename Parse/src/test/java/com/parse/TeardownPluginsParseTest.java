package com.parse;

import org.junit.After;

/**
 * Automatically takes care of teardown of plugins between tests. The order tests run in can be
 * different, so if you see a test failing randomly, the test before it may not be tearing down
 * properly, and you may need to have the test class subclass this class.
 */
class TeardownPluginsParseTest {

  @After
  public void tearDown() throws Exception {
    ParseCorePlugins.getInstance().reset();
    ParsePlugins.reset();
  }
}
