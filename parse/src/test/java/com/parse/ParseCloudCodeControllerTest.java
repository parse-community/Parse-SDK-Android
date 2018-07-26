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
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

import bolts.Task;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ParseCloudCodeControllerTest {

    @Before
    public void setUp() throws MalformedURLException {
        ParseRESTCommand.server = new URL("https://api.parse.com/1");
    }

    @After
    public void tearDown() {
        ParseRESTCommand.server = null;
    }

    //region testConstructor

    @Test
    public void testConstructor() {
        ParseHttpClient restClient = mock(ParseHttpClient.class);
        ParseCloudCodeController controller = new ParseCloudCodeController(restClient);

        assertSame(restClient, controller.restClient);
    }

    //endregion

    //region testConvertCloudResponse

    @Test
    public void testConvertCloudResponseNullResponse() {
        ParseHttpClient restClient = mock(ParseHttpClient.class);
        ParseCloudCodeController controller = new ParseCloudCodeController(restClient);

        Object result = controller.convertCloudResponse(null);

        assertNull(result);
    }

    @Test
    public void testConvertCloudResponseJsonResponseWithResultField() throws Exception {
        ParseHttpClient restClient = mock(ParseHttpClient.class);
        ParseCloudCodeController controller = new ParseCloudCodeController(restClient);
        JSONObject response = new JSONObject();
        response.put("result", "test");

        Object result = controller.convertCloudResponse(response);

        assertThat(result, instanceOf(String.class));
        assertEquals("test", result);
    }

    @Test
    public void testConvertCloudResponseJsonArrayResponse() throws Exception {
        ParseHttpClient restClient = mock(ParseHttpClient.class);
        ParseCloudCodeController controller = new ParseCloudCodeController(restClient);
        JSONArray response = new JSONArray();
        response.put(0, "test");
        response.put(1, true);
        response.put(2, 2);

        Object result = controller.convertCloudResponse(response);

        assertThat(result, instanceOf(List.class));
        List listResult = (List) result;
        assertEquals(3, listResult.size());
        assertEquals("test", listResult.get(0));
        assertEquals(true, listResult.get(1));
        assertEquals(2, listResult.get(2));
    }

    //endregion

    //region testCallFunctionInBackground

    @Test
    public void testCallFunctionInBackgroundCommand() {
        // TODO(mengyan): Verify proper command is constructed
    }

    @Test
    public void testCallFunctionInBackgroundSuccessWithResult() throws Exception {
        JSONObject json = new JSONObject();
        json.put("result", "test");
        String content = json.toString();

        ParseHttpResponse mockResponse = new ParseHttpResponse.Builder()
                .setStatusCode(200)
                .setTotalSize((long) content.length())
                .setContent(new ByteArrayInputStream(content.getBytes()))
                .build();

        ParseHttpClient restClient = mockParseHttpClientWithReponse(mockResponse);
        ParseCloudCodeController controller = new ParseCloudCodeController(restClient);

        Task<String> cloudCodeTask = controller.callFunctionInBackground(
                "test", new HashMap<String, Object>(), "sessionToken");
        ParseTaskUtils.wait(cloudCodeTask);

        verify(restClient, times(1)).execute(any(ParseHttpRequest.class));
        assertEquals("test", cloudCodeTask.getResult());
    }

    @Test
    public void testCallFunctionInBackgroundSuccessWithoutResult() throws Exception {
        JSONObject json = new JSONObject();
        String content = json.toString();

        ParseHttpResponse mockResponse = new ParseHttpResponse.Builder()
                .setStatusCode(200)
                .setTotalSize((long) content.length())
                .setContent(new ByteArrayInputStream(content.getBytes()))
                .build();

        ParseHttpClient restClient = mockParseHttpClientWithReponse(mockResponse);
        ParseCloudCodeController controller = new ParseCloudCodeController(restClient);

        Task<String> cloudCodeTask = controller.callFunctionInBackground(
                "test", new HashMap<String, Object>(), "sessionToken");
        ParseTaskUtils.wait(cloudCodeTask);

        verify(restClient, times(1)).execute(any(ParseHttpRequest.class));
        assertNull(cloudCodeTask.getResult());
    }

    @Test
    public void testCallFunctionInBackgroundFailure() throws Exception {
        // TODO(mengyan): Remove once we no longer rely on retry logic.
        ParseRequest.setDefaultInitialRetryDelay(1L);

        ParseHttpClient restClient = mock(ParseHttpClient.class);
        when(restClient.execute(any(ParseHttpRequest.class))).thenThrow(new IOException());

        ParseCloudCodeController controller = new ParseCloudCodeController(restClient);

        Task<String> cloudCodeTask =
                controller.callFunctionInBackground("test", new HashMap<String, Object>(), "sessionToken");
        // Do not use ParseTaskUtils.wait() since we do not want to throw the exception
        cloudCodeTask.waitForCompletion();

        // TODO(mengyan): Abstract out command runner so we don't have to account for retries.
        verify(restClient, times(5)).execute(any(ParseHttpRequest.class));
        assertTrue(cloudCodeTask.isFaulted());
        Exception error = cloudCodeTask.getError();
        assertThat(error, instanceOf(ParseException.class));
        assertEquals(ParseException.CONNECTION_FAILED, ((ParseException) error).getCode());
    }

    //endregion

    @Test
    public void testCallFunctionWithNullResult() throws Exception {
        String content = "{ result: null }";

        ParseHttpResponse mockResponse = new ParseHttpResponse.Builder()
                .setStatusCode(200)
                .setTotalSize((long) content.length())
                .setContent(new ByteArrayInputStream(content.getBytes()))
                .build();

        ParseHttpClient restClient = mockParseHttpClientWithReponse(mockResponse);
        ParseCloudCodeController controller = new ParseCloudCodeController(restClient);

        Task<String> cloudCodeTask = controller.callFunctionInBackground(
                "test", new HashMap<String, Object>(), "sessionToken");
        ParseTaskUtils.wait(cloudCodeTask);

        verify(restClient, times(1)).execute(any(ParseHttpRequest.class));
        String result = cloudCodeTask.getResult();
        assertEquals(null, result);
    }

    private ParseHttpClient mockParseHttpClientWithReponse(ParseHttpResponse response)
            throws IOException {
        ParseHttpClient client = mock(ParseHttpClient.class);
        when(client.execute(any(ParseHttpRequest.class))).thenReturn(response);
        return client;
    }
}
