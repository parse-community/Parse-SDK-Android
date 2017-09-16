package com.parse;


import android.content.Context;
import android.content.Intent;

import com.google.protobuf.Service;
import com.ibm.icu.impl.IllegalIcuArgumentException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import bolts.Task;
import bolts.TaskCompletionSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class PushServiceTest extends ResetPluginsParseTest {

  private PushService service;
  private ServiceController<PushService> controller;
  private PushHandler handler;
  private PushService.ServiceLifecycleCallbacks callbacks;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    callbacks = mock(PushService.ServiceLifecycleCallbacks.class);
    PushService.registerServiceLifecycleCallbacks(callbacks);

    controller = Robolectric.buildService(PushService.class);
    service = controller.get();
    handler = mock(PushHandler.class);
    service.setPushHandler(handler);

    Parse.Configuration.Builder builder = new Parse.Configuration.Builder(service);
    ParsePlugins.Android.initialize(service, builder.build());
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    PushService.unregisterServiceLifecycleCallbacks(callbacks);
  }

  @Test
  public void testOnCreateWithoutInit() {
    ParsePlugins.reset();
    controller.create();
    verify(callbacks, never()).onServiceCreated(service);
  }

  @Test
  public void testOnCreate() {
    controller.create();
    verify(callbacks, times(1)).onServiceCreated(service);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCannotBind() {
    controller.create().bind();
  }

  @Test
  public void testStartCommand() throws Exception {
    controller.create();
    service.setPushHandler(handler); // reset handler to our mock

    final TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
    final Task<Void> handleTask = tcs.getTask();
    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        tcs.setResult(null);
        return null;
      }
    }).when(handler).handlePush(any(Intent.class));

    controller.startCommand(0, 0);
    handleTask.waitForCompletion();

    verify(callbacks, times(1)).onServiceCreated(service);
    verify(handler, times(1)).handlePush(any(Intent.class));
  }

  @Test
  public void testDestroy() {
    controller.create();
    controller.startCommand(0, 0);
    controller.destroy();
    verify(callbacks, times(1)).onServiceDestroyed(service);
  }
}
