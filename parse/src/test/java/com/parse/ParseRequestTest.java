/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.http.ParseHttpBody;
import com.parse.http.ParseHttpRequest;
import com.parse.http.ParseHttpResponse;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import com.parse.boltsinternal.Task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ParseRequestTest {

    private static byte[] data;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void setUpClass() {
        char[] chars = new char[64 << 10]; // 64KB
        data = new String(chars).getBytes();
    }

    @AfterClass
    public static void tearDownClass() {
        data = null;
    }

    private static void assertProgressCompletedSuccessfully(TestProgressCallback callback) {
        int lastPercentDone = 0;
        boolean incrementalPercentage = false;
        for (int percentDone : callback.history) {
            assertTrue("Progress went backwards", percentDone >= lastPercentDone);
            assertTrue("Invalid percentDone: " + percentDone, percentDone >= 0 && percentDone <= 100);

            if (percentDone > 0 || percentDone < 100) {
                incrementalPercentage = true;
            }

            lastPercentDone = percentDone;
        }
        assertTrue("ProgressCallback was not called with a value between 0 and 100: " + callback.history,
                incrementalPercentage);
        assertEquals(100, callback.history.get(callback.history.size() - 1).intValue());
    }

    @Before
    public void setUp() {
        ParseRequest.setDefaultInitialRetryDelay(1L);
    }

    @After
    public void tearDown() {
        ParseRequest.setDefaultInitialRetryDelay(ParseRequest.DEFAULT_INITIAL_RETRY_DELAY);
    }

    @Test
    public void testRetryLogic() throws Exception {
        ParseHttpClient mockHttpClient = mock(ParseHttpClient.class);
        when(mockHttpClient.execute(any(ParseHttpRequest.class))).thenThrow(new IOException());

        TestParseRequest request = new TestParseRequest(ParseHttpRequest.Method.GET, "http://parse.com");
        Task<String> task = request.executeAsync(mockHttpClient);
        task.waitForCompletion();

        verify(mockHttpClient, times(5)).execute(any(ParseHttpRequest.class));
    }

    // TODO(grantland): Move to ParseFileRequestTest or ParseCountingByteArrayHttpBodyTest
    @Test
    public void testDownloadProgress() throws Exception {
        ParseHttpResponse mockResponse = new ParseHttpResponse.Builder()
                .setStatusCode(200)
                .setTotalSize((long) data.length)
                .setContent(new ByteArrayInputStream(data))
                .build();

        ParseHttpClient mockHttpClient = mock(ParseHttpClient.class);
        when(mockHttpClient.execute(any(ParseHttpRequest.class))).thenReturn(mockResponse);

        File tempFile = temporaryFolder.newFile("test");
        ParseFileRequest request =
                new ParseFileRequest(ParseHttpRequest.Method.GET, "localhost", tempFile);
        TestProgressCallback downloadProgressCallback = new TestProgressCallback();
        Task<Void> task = request.executeAsync(mockHttpClient, null, downloadProgressCallback);

        task.waitForCompletion();
        assertFalse("Download failed: " + task.getError(), task.isFaulted());
        assertEquals(data.length, ParseFileUtils.readFileToByteArray(tempFile).length);

        assertProgressCompletedSuccessfully(downloadProgressCallback);
    }

    private static class TestProgressCallback implements ProgressCallback {
        List<Integer> history = new LinkedList<>();

        @Override
        public void done(Integer percentDone) {
            history.add(percentDone);
        }
    }

    private static class TestParseRequest extends ParseRequest<String> {

        byte[] data;

        public TestParseRequest(ParseHttpRequest.Method method, String url) {
            super(method, url);
        }

        @Override
        protected Task<String> onResponseAsync(
                ParseHttpResponse response, ProgressCallback downloadProgressCallback) {
            return Task.forResult(null);
        }

        @Override
        protected ParseHttpBody newBody(ProgressCallback uploadProgressCallback) {
            if (uploadProgressCallback != null) {
                return new ParseCountingByteArrayHttpBody(data, null, uploadProgressCallback);
            }
            return super.newBody(null);
        }
    }
}
