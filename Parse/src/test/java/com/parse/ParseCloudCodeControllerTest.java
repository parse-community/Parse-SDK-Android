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
import org.json.JSONObject;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

public class ParseCloudCodeControllerTest {

  //region testConstructor

  @Test
  public void testConstructor() throws Exception {
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    ParseCloudCodeController controller = new ParseCloudCodeController(restClient);

    assertSame(restClient, controller.restClient);
  }

  //endregion

  //region testConvertCloudResponse

  @Test
  public void testConvertCloudResponseNullResponse() throws Exception {
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    ParseCloudCodeController controller = new ParseCloudCodeController(restClient);

    Object result = controller.convertCloudResponse(null);

    assertNull(result);
  }

  @Test
  public void testConvertCloudResponseJsonResponseWithoutResultField() throws Exception {
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    ParseCloudCodeController controller = new ParseCloudCodeController(restClient);
    JSONObject response = new JSONObject();
    response.put("foo", "bar");
    response.put("yarr", 1);

    Object result = controller.convertCloudResponse(response);

    assertThat(result, instanceOf(JSONObject.class));
    JSONObject jsonResult = (JSONObject)result;
    assertEquals(response, jsonResult, JSONCompareMode.NON_EXTENSIBLE);
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
    List listResult = (List)result;
    assertEquals(3, listResult.size());
    assertEquals("test", listResult.get(0));
    assertEquals(true, listResult.get(1));
    assertEquals(2, listResult.get(2));
  }

  //endregion

  //region testCallFunctionInBackground

  @Test
  public void testCallFunctionInBackgroundCommand() throws Exception {
    // TODO(mengyan): Verify proper command is constructed
  }

  @Test
  public void testCallFunctionInBackgroundSuccess() throws Exception {
    JSONObject json = new JSONObject();
    json.put("result", "test");
    String content = json.toString();

    ParseHttpResponse mockResponse = mock(ParseHttpResponse.class);
    when(mockResponse.getStatusCode()).thenReturn(200);
    when(mockResponse.getContent()).thenReturn(new ByteArrayInputStream(content.getBytes()));
    when(mockResponse.getTotalSize()).thenReturn(content.length());

    ParseHttpClient restClient = mockParseHttpClientWithReponse(mockResponse);
    ParseCloudCodeController controller = new ParseCloudCodeController(restClient);

    Task<String> cloudCodeTask = controller.callFunctionInBackground(
        "test", new HashMap<String, Object>(), "sessionToken");
    ParseTaskUtils.wait(cloudCodeTask);

    verify(restClient, times(1)).execute(any(ParseHttpRequest.class));
    assertEquals("test", cloudCodeTask.getResult());
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

  private ParseHttpClient mockParseHttpClientWithReponse(ParseHttpResponse response)
      throws IOException {
    ParseHttpClient client = mock(ParseHttpClient.class);
    when(client.execute(any(ParseHttpRequest.class))).thenReturn(response);
    return client;
  }
}
