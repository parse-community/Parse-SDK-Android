/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.http.ParseHttpRequest;
import com.parse.http.ParseHttpResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import bolts.Task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// For Uri.encode
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class NetworkObjectControllerTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws MalformedURLException {
        ParseRESTCommand.server = new URL("https://api.parse.com/1");
    }

    @After
    public void tearDown() {
        ParseRESTCommand.server = null;
    }

    //region testFetchAsync

    @Test
    public void testFetchAsync() throws Exception {
        // Make mock response and client
        JSONObject mockResponse = new JSONObject();
        String createAtStr = "2015-08-09T22:15:13.460Z";
        long createAtLong = ParseDateFormat.getInstance().parse(createAtStr).getTime();
        String updateAtStr = "2015-08-09T22:15:13.497Z";
        long updateAtLong = ParseDateFormat.getInstance().parse(updateAtStr).getTime();
        mockResponse.put("createdAt", createAtStr);
        mockResponse.put("objectId", "testObjectId");
        mockResponse.put("key", "value");
        mockResponse.put("updatedAt", updateAtStr);
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 200, "OK");
        // Make test state
        ParseObject.State state = new ParseObject.State.Builder("Test")
                .objectId("testObjectId")
                .build();

        NetworkObjectController controller = new NetworkObjectController(restClient);
        ParseObject.State newState =
                ParseTaskUtils.wait(controller.fetchAsync(state, "sessionToken", ParseDecoder.get()));

        assertEquals(createAtLong, newState.createdAt());
        assertEquals(updateAtLong, newState.updatedAt());
        assertEquals("value", newState.get("key"));
        assertEquals("testObjectId", newState.objectId());
        assertTrue(newState.isComplete());
    }

    //endregion

    //region testSaveAsync

    @Test
    public void testSaveAsync() throws Exception {
        // Make mock response and client
        JSONObject mockResponse = new JSONObject();
        String createAtStr = "2015-08-09T22:15:13.460Z";
        long createAtLong = ParseDateFormat.getInstance().parse(createAtStr).getTime();
        String updateAtStr = "2015-08-09T22:15:13.497Z";
        long updateAtLong = ParseDateFormat.getInstance().parse(updateAtStr).getTime();
        mockResponse.put("createdAt", createAtStr);
        mockResponse.put("objectId", "testObjectId");
        mockResponse.put("updatedAt", updateAtStr);
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 200, "OK");
        // Make test state
        ParseObject object = new ParseObject("Test");
        object.put("key", "value");

        NetworkObjectController controller = new NetworkObjectController(restClient);
        ParseObject.State newState = ParseTaskUtils.wait(controller.saveAsync(
                object.getState(),
                object.startSave(),
                "sessionToken",
                ParseDecoder.get()));

        assertEquals(createAtLong, newState.createdAt());
        assertEquals(updateAtLong, newState.updatedAt());
        assertEquals("testObjectId", newState.objectId());
        assertFalse(newState.isComplete());
    }

    //endregion

    //region testDeleteAsync

    @Test
    public void testDeleteAsync() throws Exception {
        // Make mock response and client
        JSONObject mockResponse = new JSONObject();
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 200, "OK");
        // Make test state
        ParseObject.State state = new ParseObject.State.Builder("Test")
                .objectId("testObjectId")
                .build();

        NetworkObjectController controller = new NetworkObjectController(restClient);
        // We just need to verify task is finished since sever returns an empty json here
        ParseTaskUtils.wait(controller.deleteAsync(state, "sessionToken"));
    }

    //endregion

    //region testFailingDeleteAsync

    @Test
    public void testFailingDeleteAsync() throws Exception {
        // Make mock response and client
        JSONObject mockResponse = new JSONObject();
        mockResponse.put("code", 141);
        mockResponse.put("error", "Delete is not allowed");
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 400, "Bad Request");
        // Make test state
        ParseObject.State state = new ParseObject.State.Builder("Test")
                .objectId("testObjectId")
                .build();

        NetworkObjectController controller = new NetworkObjectController(restClient);

        thrown.expect(ParseException.class);
        thrown.expectMessage("Delete is not allowed");

        ParseTaskUtils.wait(controller.deleteAsync(state, "sessionToken"));
    }

    //endregion

    //region testFailingSaveAsync

    @Test
    public void testFailingSaveAsync() throws Exception {
        // Make mock response and client
        JSONObject mockResponse = new JSONObject();
        mockResponse.put("code", 141);
        mockResponse.put("error", "Save is not allowed");
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 400, "Bad Request");

        // Make test object
        ParseObject object = new ParseObject("Test");
        object.put("key", "value");

        NetworkObjectController controller = new NetworkObjectController(restClient);

        thrown.expect(ParseException.class);
        thrown.expectMessage("Save is not allowed");

        ParseTaskUtils.wait(controller.saveAsync(
                object.getState(),
                object.startSave(),
                "sessionToken",
                ParseDecoder.get()));
    }

    //endregion

    //region testSaveAllAsync

    @Test
    public void testSaveAllAsync() throws Exception {
        // Make individual responses
        JSONObject objectSaveResult = new JSONObject();
        String createAtStr = "2015-08-09T22:15:13.460Z";
        long createAtLong = ParseDateFormat.getInstance().parse(createAtStr).getTime();
        String updateAtStr = "2015-08-09T22:15:13.497Z";
        long updateAtLong = ParseDateFormat.getInstance().parse(updateAtStr).getTime();
        objectSaveResult.put("createdAt", createAtStr);
        objectSaveResult.put("objectId", "testObjectId");
        objectSaveResult.put("updatedAt", updateAtStr);
        JSONObject objectResponse = new JSONObject();
        objectResponse.put("success", objectSaveResult);
        JSONObject objectResponseAgain = new JSONObject();
        JSONObject objectSaveResultAgain = new JSONObject();
        objectSaveResultAgain.put("code", 101);
        objectSaveResultAgain.put("error", "Error");
        objectResponseAgain.put("error", objectSaveResultAgain);
        // Make batch response
        JSONArray mockResponse = new JSONArray();
        mockResponse.put(objectResponse);
        mockResponse.put(objectResponseAgain);
        // Make mock response
        byte[] contentBytes = mockResponse.toString().getBytes();
        ParseHttpResponse response = new ParseHttpResponse.Builder()
                .setContent(new ByteArrayInputStream(contentBytes))
                .setStatusCode(200)
                .setTotalSize(contentBytes.length)
                .setContentType("application/json")
                .build();
        // Mock http client
        ParseHttpClient client = mock(ParseHttpClient.class);
        when(client.execute(any(ParseHttpRequest.class))).thenReturn(response);
        // Make test state, operations and decoder
        List<ParseObject.State> states = new ArrayList<>();
        List<ParseOperationSet> operationsList = new ArrayList<>();
        List<ParseDecoder> decoders = new ArrayList<>();
        ParseObject object = new ParseObject("Test");
        object.put("key", "value");
        states.add(object.getState());
        operationsList.add(object.startSave());
        decoders.add(ParseDecoder.get());
        ParseObject objectAgain = new ParseObject("Test");
        object.put("keyAgain", "valueAgain");
        states.add(objectAgain.getState());
        operationsList.add(objectAgain.startSave());
        decoders.add(ParseDecoder.get());

        // Test
        NetworkObjectController controller = new NetworkObjectController(client);
        List<Task<ParseObject.State>> saveTaskList =
                controller.saveAllAsync(states, operationsList, "sessionToken", decoders);
        Task.whenAll(saveTaskList).waitForCompletion();

        // Verify newState
        ParseObject.State newState = saveTaskList.get(0).getResult();
        assertEquals(createAtLong, newState.createdAt());
        assertEquals(updateAtLong, newState.updatedAt());
        assertEquals("testObjectId", newState.objectId());
        assertFalse(newState.isComplete());
        // Verify exception
        assertTrue(saveTaskList.get(1).isFaulted());
        assertTrue(saveTaskList.get(1).getError() instanceof ParseException);
        ParseException parseException = (ParseException) saveTaskList.get(1).getError();
        assertEquals(101, parseException.getCode());
        assertEquals("Error", parseException.getMessage());
    }

    //endregion

    //region testDeleteAsync

    @Test
    public void testDeleteAllAsync() throws Exception {
        // Make individual responses
        JSONObject objectResponse = new JSONObject();
        objectResponse.put("success", new JSONObject());
        JSONObject objectResponseAgain = new JSONObject();
        JSONObject objectDeleteResultAgain = new JSONObject();
        objectDeleteResultAgain.put("code", 101);
        objectDeleteResultAgain.put("error", "Error");
        objectResponseAgain.put("error", objectDeleteResultAgain);
        // Make batch response
        JSONArray mockResponse = new JSONArray();
        mockResponse.put(objectResponse);
        mockResponse.put(objectResponseAgain);
        // Make mock response
        byte[] contentBytes = mockResponse.toString().getBytes();
        ParseHttpResponse response = new ParseHttpResponse.Builder()
                .setContent(new ByteArrayInputStream(contentBytes))
                .setStatusCode(200)
                .setTotalSize(contentBytes.length)
                .setContentType("application/json")
                .build();
        // Mock http client
        ParseHttpClient client = mock(ParseHttpClient.class);
        when(client.execute(any(ParseHttpRequest.class))).thenReturn(response);
        // Make test state, operations and decoder
        List<ParseObject.State> states = new ArrayList<>();
        // Make test state
        ParseObject.State state = new ParseObject.State.Builder("Test")
                .objectId("testObjectId")
                .build();
        states.add(state);
        ParseObject.State stateAgain = new ParseObject.State.Builder("Test")
                .objectId("testObjectIdAgain")
                .build();
        states.add(stateAgain);

        // Test
        NetworkObjectController controller = new NetworkObjectController(client);
        List<Task<Void>> deleteTaskList =
                controller.deleteAllAsync(states, "sessionToken");
        Task.whenAll(deleteTaskList).waitForCompletion();

        // Verify success result
        assertFalse(deleteTaskList.get(0).isFaulted());
        // Verify error result
        assertTrue(deleteTaskList.get(1).isFaulted());
        assertTrue(deleteTaskList.get(1).getError() instanceof ParseException);
        ParseException parseException = (ParseException) deleteTaskList.get(1).getError();
        assertEquals(101, parseException.getCode());
        assertEquals("Error", parseException.getMessage());
    }

    //endregion
}
