package com.parse;


import android.content.Intent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import bolts.Task;
import bolts.TaskCompletionSource;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


public class PushServiceUtilsTest {

  @Test
  public void testDefaultHandler() {
    ManifestInfo.setPushType(PushType.NONE);
    PushHandler handler = PushServiceUtils.createPushHandler();
    assertTrue(handler instanceof PushHandler.FallbackHandler);

    ManifestInfo.setPushType(PushType.GCM);
    handler = PushServiceUtils.createPushHandler();
    assertTrue(handler instanceof GcmPushHandler);
  }

}
