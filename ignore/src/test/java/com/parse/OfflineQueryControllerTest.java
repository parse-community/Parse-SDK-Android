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
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import bolts.Task;

import static org.junit.Assert.assertTrue;

public class OfflineQueryControllerTest {

  @After
  public void tearDown() {
    Parse.disableLocalDatastore();
  }

  @Test
  public void testFindFromNetwork() {
    TestNetworkQueryController networkController = new TestNetworkQueryController();
    OfflineQueryController controller = new OfflineQueryController(null, networkController);

    ParseQuery.State<ParseObject> state = new ParseQuery.State.Builder<>("TestObject")
        .build();
    controller.findAsync(state, null, null);
    networkController.verifyFind();
  }

  @Test
  public void testCountFromNetwork() {
    TestNetworkQueryController networkController = new TestNetworkQueryController();
    OfflineQueryController controller = new OfflineQueryController(null, networkController);

    ParseQuery.State<ParseObject> state = new ParseQuery.State.Builder<>("TestObject")
        .build();
    controller.countAsync(state, null, null);
    networkController.verifyCount();
  }

  @Test
  public void testGetFromNetwork() {
    TestNetworkQueryController networkController = new TestNetworkQueryController();
    OfflineQueryController controller = new OfflineQueryController(null, networkController);

    ParseQuery.State<ParseObject> state = new ParseQuery.State.Builder<>("TestObject")
        .build();
    controller.getFirstAsync(state, null, null);
    networkController.verifyFind();
  }

  @Test
  public void testFindFromLDS() {
    Parse.enableLocalDatastore(null);
    TestOfflineStore offlineStore = new TestOfflineStore();
    OfflineQueryController controller = new OfflineQueryController(offlineStore, null);

    ParseQuery.State<ParseObject> state = new ParseQuery.State.Builder<>("TestObject")
        .fromLocalDatastore()
        .build();
    controller.findAsync(state, null, null);
    offlineStore.verifyFind();
  }

  @Test
  public void testCountFromLDS() {
    Parse.enableLocalDatastore(null);
    TestOfflineStore offlineStore = new TestOfflineStore();
    OfflineQueryController controller = new OfflineQueryController(offlineStore, null);

    ParseQuery.State<ParseObject> state = new ParseQuery.State.Builder<>("TestObject")
        .fromLocalDatastore()
        .build();
    controller.countAsync(state, null, null);
    offlineStore.verifyCount();
  }

  @Test
  public void testGetFromLDS() {
    Parse.enableLocalDatastore(null);
    TestOfflineStore offlineStore = new TestOfflineStore();
    OfflineQueryController controller = new OfflineQueryController(offlineStore, null);

    ParseQuery.State<ParseObject> state = new ParseQuery.State.Builder<>("TestObject")
        .fromLocalDatastore()
        .build();
    controller.getFirstAsync(state, null, null);
    offlineStore.verifyFind();
  }

  private static class TestOfflineStore extends OfflineStore {

    private AtomicBoolean findCalled = new AtomicBoolean();
    private AtomicBoolean countCalled = new AtomicBoolean();

    TestOfflineStore() {
      super((OfflineSQLiteOpenHelper) null);
    }

    @Override
    <T extends ParseObject> Task<List<T>> findFromPinAsync(
        String name, ParseQuery.State<T> state, ParseUser user) {
      findCalled.set(true);
      return Task.forResult(null);
    }

    @Override
    <T extends ParseObject> Task<Integer> countFromPinAsync(
        String name, ParseQuery.State<T> state, ParseUser user) {
      countCalled.set(true);
      return Task.forResult(null);
    }

    public void verifyFind() {
      assertTrue(findCalled.get());
    }

    public void verifyCount() {
      assertTrue(countCalled.get());
    }
  }

  private static class TestNetworkQueryController implements ParseQueryController {

    private AtomicBoolean findCalled = new AtomicBoolean();
    private AtomicBoolean countCalled = new AtomicBoolean();

    @Override
    public <T extends ParseObject> Task<List<T>> findAsync(
        ParseQuery.State<T> state, ParseUser user, Task<Void> cancellationToken) {
      findCalled.set(true);
      return Task.forResult(null);
    }

    @Override
    public <T extends ParseObject> Task<Integer> countAsync(
        ParseQuery.State<T> state, ParseUser user, Task<Void> cancellationToken) {
      countCalled.set(true);
      return Task.forResult(null);
    }

    @Override
    public <T extends ParseObject> Task<T> getFirstAsync(
        ParseQuery.State<T> state, ParseUser user, Task<Void> cancellationToken) {
      throw new IllegalStateException("Should not be called");
    }

    public void verifyFind() {
      assertTrue(findCalled.get());
    }

    public void verifyCount() {
      assertTrue(countCalled.get());
    }
  }
}
