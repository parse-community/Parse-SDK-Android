/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import bolts.Task;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NetworkQueryControllerTest {

    private static JSONObject generateBasicMockResponse() throws JSONException {
        JSONObject objectJSON = new JSONObject();
        String createAtStr = "2015-08-09T22:15:13.460Z";
        objectJSON.put("createdAt", createAtStr);
        objectJSON.put("updatedAt", createAtStr);
        objectJSON.put("objectId", "testObjectId");
        objectJSON.put("sessionToken", "testSessionToken");
        objectJSON.put("key", "value");

        createAtStr = "2015-08-10T22:15:13.460Z";
        JSONObject objectJSONAgain = new JSONObject();
        objectJSONAgain.put("createdAt", createAtStr);
        objectJSONAgain.put("updatedAt", createAtStr);
        objectJSONAgain.put("objectId", "testObjectIdAgain");
        objectJSONAgain.put("sessionToken", "testSessionTokenAgain");
        objectJSONAgain.put("keyAgain", "valueAgain");

        JSONArray objectJSONArray = new JSONArray();
        objectJSONArray.put(objectJSON);
        objectJSONArray.put(objectJSONAgain);

        JSONObject mockResponse = new JSONObject();
        mockResponse.put("results", objectJSONArray);
        return mockResponse;
    }

    @Before
    public void setUp() throws MalformedURLException {
        ParseRESTCommand.server = new URL("https://api.parse.com/1");
    }

    //region testConvertFindResponse

    @After
    public void tearDown() {
        ParseRESTCommand.server = null;
    }

    //endregion

    //region testFindAsync

    @Test
    public void testConvertFindResponse() throws Exception {
        // Make mock response
        JSONObject mockResponse = generateBasicMockResponse();
        // Make mock state
        ParseQuery.State mockState = mock(ParseQuery.State.class);
        when(mockState.className()).thenReturn("Test");
        when(mockState.selectedKeys()).thenReturn(null);
        when(mockState.constraints()).thenReturn(new ParseQuery.QueryConstraints());

        NetworkQueryController controller = new NetworkQueryController(mock(ParseHttpClient.class));
        List<ParseObject> objects = controller.convertFindResponse(mockState, mockResponse);

        verifyBasicParseObjects(mockResponse, objects, "Test");
    }

    // TODO(mengyan): Add testFindAsyncWithCachePolicy to verify command is added to
    // ParseKeyValueCache

    //endregion

    //region testCountAsync

    @Test
    public void testFindAsyncWithSessionToken() throws Exception {
        // Make mock response
        JSONObject mockResponse = generateBasicMockResponse();
        mockResponse.put("trace", "serverTrace");
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 200, "OK");
        // Make mock state
        ParseQuery.State mockState = mock(ParseQuery.State.class);
        when(mockState.className()).thenReturn("Test");
        when(mockState.selectedKeys()).thenReturn(null);
        when(mockState.constraints()).thenReturn(new ParseQuery.QueryConstraints());

        NetworkQueryController controller = new NetworkQueryController(restClient);
        Task<List<ParseObject>> findTask = controller.findAsync(mockState, "sessionToken", null);
        ParseTaskUtils.wait(findTask);
        List<ParseObject> objects = findTask.getResult();

        verifyBasicParseObjects(mockResponse, objects, "Test");
        // TODO(mengyan): Verify PLog is called
    }

    // TODO(mengyan): Add testFindAsyncWithCachePolicy to verify command is added to
    // ParseKeyValueCache

    //endregion

    @Test
    public void testCountAsyncWithSessionToken() throws Exception {
        // Make mock response and client
        JSONObject mockResponse = new JSONObject();
        mockResponse.put("count", 2);
        ParseHttpClient restClient =
                ParseTestUtils.mockParseHttpClientWithResponse(mockResponse, 200, "OK");
        // Make mock state
        ParseQuery.State mockState = mock(ParseQuery.State.class);
        when(mockState.className()).thenReturn("Test");
        when(mockState.selectedKeys()).thenReturn(null);
        when(mockState.constraints()).thenReturn(new ParseQuery.QueryConstraints());

        NetworkQueryController controller = new NetworkQueryController(restClient);

        Task<Integer> countTask = controller.countAsync(mockState, "sessionToken", null);
        ParseTaskUtils.wait(countTask);
        int count = countTask.getResult();

        assertEquals(2, count);
    }

    private void verifyBasicParseObjects(
            JSONObject mockResponse, List<ParseObject> objects, String className) throws JSONException {
        JSONArray objectsJSON = mockResponse.getJSONArray("results");
        assertEquals(objectsJSON.length(), objects.size());

        ParseObject object = objects.get(0);
        JSONObject objectJSON = objectsJSON.getJSONObject(0);
        assertEquals(className, object.getClassName());
        long dateLong =
                ParseDateFormat.getInstance().parse(objectJSON.getString("createdAt")).getTime();
        assertEquals(dateLong, object.getState().createdAt());
        dateLong = ParseDateFormat.getInstance().parse(objectJSON.getString("updatedAt")).getTime();
        assertEquals(dateLong, object.getState().updatedAt());
        assertEquals(objectJSON.getString("objectId"), object.getObjectId());
        assertEquals(objectJSON.getString("sessionToken"), object.get("sessionToken"));
        assertEquals(objectJSON.getString("key"), object.getString("key"));

        ParseObject objectAgain = objects.get(1);
        assertEquals(className, objectAgain.getClassName());
        JSONObject objectAgainJSON = objectsJSON.getJSONObject(1);
        dateLong =
                ParseDateFormat.getInstance().parse(objectAgainJSON.getString("createdAt")).getTime();
        assertEquals(dateLong, objectAgain.getState().createdAt());
        dateLong =
                ParseDateFormat.getInstance().parse(objectAgainJSON.getString("updatedAt")).getTime();
        assertEquals(dateLong, objectAgain.getState().updatedAt());
        assertEquals(objectAgainJSON.getString("objectId"), objectAgain.getObjectId());
        assertEquals(objectAgainJSON.getString("sessionToken"), objectAgain.get("sessionToken"));
        assertEquals(objectAgainJSON.getString("keyAgain"), objectAgain.getString("keyAgain"));
    }
}
