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

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.parse.boltsinternal.Task;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ParseFileRequestTest extends TestCase {

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
        ParseHttpResponse mockResponse = new ParseHttpResponse.Builder()
                .setStatusCode(400)
                .setTotalSize(0L)
                .setReasonPhrase("Bad Request")
                .setContent(mockInputStream)
                .build();

        ParseHttpClient mockHttpClient = mock(ParseHttpClient.class);
        when(mockHttpClient.execute(any(ParseHttpRequest.class))).thenReturn(mockResponse);

        ParseFileRequest request =
                new ParseFileRequest(ParseHttpRequest.Method.GET, "http://parse.com", null);
        Task<Void> task = request.executeAsync(mockHttpClient);
        task.waitForCompletion();

        assertTrue(task.isFaulted());
        assertTrue(task.getError() instanceof ParseException);
        ParseException error = (ParseException) task.getError();
        assertEquals(error.getCode(), ParseException.CONNECTION_FAILED);
        assertTrue(error.getMessage().contains("Download from file server"));
    }
}
