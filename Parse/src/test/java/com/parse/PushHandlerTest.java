package com.parse;


import org.junit.Test;

import static org.junit.Assert.*;

public class PushHandlerTest {

  @Test
  public void testFactory() {
    PushHandler handler = PushHandler.Factory.create(PushType.NONE);
    assertTrue(handler instanceof PushHandler.FallbackHandler);

    handler = PushHandler.Factory.create(PushType.GCM);
    assertTrue(handler instanceof GcmPushHandler);
  }

  @Test
  public void testFallbackHandler() {
    PushHandler handler = PushHandler.Factory.create(PushType.NONE);
    assertNull(handler.getWarningMessage(PushHandler.SupportLevel.SUPPORTED));
    assertNull(handler.getWarningMessage(PushHandler.SupportLevel.MISSING_OPTIONAL_DECLARATIONS));
    assertNull(handler.getWarningMessage(PushHandler.SupportLevel.MISSING_REQUIRED_DECLARATIONS));
    assertTrue(handler.initialize().isCompleted());
    assertEquals(handler.isSupported(), PushHandler.SupportLevel.SUPPORTED);
  }
}
