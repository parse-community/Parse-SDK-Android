/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import junit.framework.TestCase;

import org.junit.Rule;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import bolts.Task;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ParseAWSRequestTest extends TestCase {

  @Override
  protected void tearDown() throws Exception {
    ParseRequest.setDefaultInitialRetryDelay(ParseRequest.DEFAULT_INITIAL_RETRY_DELAY);
    super.tearDown();
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  public void test4XXThrowsException() throws Exception {
    ParseRequest.setDefaultInitialRetryDelay(1L);
    InputStream mockInputStream = new ByteArrayInputStream(
        "An Error occurred while saving".getBytes());
    ParseHttpResponse mockResponse = mock(ParseHttpResponse.class);
    when(mockResponse.getStatusCode()).thenReturn(400);
    when(mockResponse.getTotalSize()).thenReturn(0L);
    when(mockResponse.getReasonPhrase()).thenReturn("Bad Request");
    when(mockResponse.getContent()).thenReturn(mockInputStream);

    ParseHttpClient mockHttpClient = mock(ParseHttpClient.class);
    when(mockHttpClient.execute(any(ParseHttpRequest.class))).thenReturn(mockResponse);

    ParseAWSRequest request = new ParseAWSRequest(ParseHttpRequest.Method.GET, "http://parse.com");
    Task<byte[]> task = request.executeAsync(mockHttpClient);
    task.waitForCompletion();

    assertTrue(task.isFaulted());
    assertTrue(task.getError() instanceof ParseException);
    ParseException error = (ParseException) task.getError();
    assertEquals(error.getCode(), ParseException.CONNECTION_FAILED);
    assertTrue(error.getMessage().contains("Download from S3"));
  }
}
