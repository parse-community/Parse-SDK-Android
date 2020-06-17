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

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import com.parse.boltsinternal.Task;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// For org.json
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class ParseFileControllerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws MalformedURLException {
        ParseRESTCommand.server = new URL("https://api.parse.com/1");
    }

    @After
    public void tearDown() {
        // TODO(grantland): Remove once we no longer rely on retry logic.
        ParseRequest.setDefaultInitialRetryDelay(ParseRequest.DEFAULT_INITIAL_RETRY_DELAY);
        ParseRESTCommand.server = null;
    }

    @Test
    public void testGetCacheFile() {
        File root = temporaryFolder.getRoot();
        ParseFileController controller = new ParseFileController(null, root);

        ParseFile.State state = new ParseFile.State.Builder().name("test_file").build();
        File cacheFile = controller.getCacheFile(state);
        assertEquals(new File(root, "test_file"), cacheFile);
    }

    @Test
    public void testIsDataAvailable() throws IOException {
        File root = temporaryFolder.getRoot();
        ParseFileController controller = new ParseFileController(null, root);

        temporaryFolder.newFile("test_file");

        ParseFile.State state = new ParseFile.State.Builder().name("test_file").build();
        assertTrue(controller.isDataAvailable(state));
    }

    @Test
    public void testClearCache() throws IOException {
        File root = temporaryFolder.getRoot();
        ParseFileController controller = new ParseFileController(null, root);

        File file1 = temporaryFolder.newFile("test_file_1");
        File file2 = temporaryFolder.newFile("test_file_2");
        controller.clearCache();
        assertFalse(file1.exists());
        assertFalse(file2.exists());
    }

    //region testSaveAsync

    @Test
    public void testSaveAsyncRequest() {
        // TODO(grantland): Verify proper command is constructed
    }

    @Test
    public void testSaveAsyncNotDirty() throws Exception {
        ParseHttpClient restClient = mock(ParseHttpClient.class);
        ParseFileController controller = new ParseFileController(restClient, null);

        ParseFile.State state = new ParseFile.State.Builder()
                .url("http://example.com")
                .build();
        Task<ParseFile.State> task = controller.saveAsync(state, (byte[]) null, null, null, null);
        task.waitForCompletion();

        verify(restClient, times(0)).execute(any(ParseHttpRequest.class));
        assertFalse(task.isFaulted());
        assertFalse(task.isCancelled());
        assertSame(state, task.getResult());
    }

    @Test
    public void testSaveAsyncAlreadyCancelled() throws Exception {
        ParseHttpClient restClient = mock(ParseHttpClient.class);
        ParseFileController controller = new ParseFileController(restClient, null);

        ParseFile.State state = new ParseFile.State.Builder().build();
        Task<Void> cancellationToken = Task.cancelled();
        Task<ParseFile.State> task = controller.saveAsync(state, (byte[]) null, null, null, cancellationToken);
        task.waitForCompletion();

        verify(restClient, times(0)).execute(any(ParseHttpRequest.class));
        assertTrue(task.isCancelled());
    }

    @Test
    public void testSaveAsyncSuccessWithByteArray() throws Exception {
        JSONObject json = new JSONObject();
        json.put("name", "new_file_name");
        json.put("url", "http://example.com");
        String content = json.toString();

        ParseHttpResponse mockResponse = new ParseHttpResponse.Builder()
                .setStatusCode(200)
                .setTotalSize((long) content.length())
                .setContent(new ByteArrayInputStream(content.getBytes()))
                .build();

        ParseHttpClient restClient = mock(ParseHttpClient.class);
        when(restClient.execute(any(ParseHttpRequest.class))).thenReturn(mockResponse);

        File root = temporaryFolder.getRoot();
        ParseFileController controller = new ParseFileController(restClient, root);

        byte[] data = "hello".getBytes();
        ParseFile.State state = new ParseFile.State.Builder()
                .name("file_name")
                .mimeType("mime_type")
                .build();
        Task<ParseFile.State> task = controller.saveAsync(state, data, null, null, null);
        ParseFile.State result = ParseTaskUtils.wait(task);

        verify(restClient, times(1)).execute(any(ParseHttpRequest.class));
        assertEquals("new_file_name", result.name());
        assertEquals("http://example.com", result.url());
        File file = new File(root, "new_file_name");
        assertTrue(file.exists());
        assertEquals("hello", ParseFileUtils.readFileToString(file, "UTF-8"));
    }

    @Test
    public void testSaveAsyncSuccessWithFile() throws Exception {
        JSONObject json = new JSONObject();
        json.put("name", "new_file_name");
        json.put("url", "http://example.com");
        String content = json.toString();

        ParseHttpResponse mockResponse = new ParseHttpResponse.Builder()
                .setStatusCode(200)
                .setTotalSize((long) content.length())
                .setContent(new ByteArrayInputStream(content.getBytes()))
                .build();

        ParseHttpClient restClient = mock(ParseHttpClient.class);
        when(restClient.execute(any(ParseHttpRequest.class))).thenReturn(mockResponse);

        File root = temporaryFolder.getRoot();
        ParseFileController controller = new ParseFileController(restClient, root);

        File file = new File(root, "test");
        ParseFileUtils.writeStringToFile(file, "content", "UTF-8");
        ParseFile.State state = new ParseFile.State.Builder()
                .name("file_name")
                .mimeType("mime_type")
                .build();
        Task<ParseFile.State> task = controller.saveAsync(state, file, null, null, null);
        ParseFile.State result = ParseTaskUtils.wait(task);

        verify(restClient, times(1)).execute(any(ParseHttpRequest.class));
        assertEquals("new_file_name", result.name());
        assertEquals("http://example.com", result.url());
        File cachedFile = new File(root, "new_file_name");
        assertTrue(cachedFile.exists());
        assertTrue(file.exists());
        assertEquals("content", ParseFileUtils.readFileToString(cachedFile, "UTF-8"));
    }

    @Test
    public void testSaveAsyncFailureWithByteArray() throws Exception {
        // TODO(grantland): Remove once we no longer rely on retry logic.
        ParseRequest.setDefaultInitialRetryDelay(1L);

        ParseHttpClient restClient = mock(ParseHttpClient.class);
        when(restClient.execute(any(ParseHttpRequest.class))).thenThrow(new IOException());

        File root = temporaryFolder.getRoot();
        ParseFileController controller = new ParseFileController(restClient, root);

        byte[] data = "hello".getBytes();
        ParseFile.State state = new ParseFile.State.Builder()
                .build();
        Task<ParseFile.State> task = controller.saveAsync(state, data, null, null, null);
        task.waitForCompletion();

        // TODO(grantland): Abstract out command runner so we don't have to account for retries.
        verify(restClient, times(5)).execute(any(ParseHttpRequest.class));
        assertTrue(task.isFaulted());
        Exception error = task.getError();
        assertThat(error, instanceOf(ParseException.class));
        assertEquals(ParseException.CONNECTION_FAILED, ((ParseException) error).getCode());
        assertEquals(0, root.listFiles().length);
    }

    @Test
    public void testSaveAsyncFailureWithFile() throws Exception {
        // TODO(grantland): Remove once we no longer rely on retry logic.
        ParseRequest.setDefaultInitialRetryDelay(1L);

        ParseHttpClient restClient = mock(ParseHttpClient.class);
        when(restClient.execute(any(ParseHttpRequest.class))).thenThrow(new IOException());

        File root = temporaryFolder.getRoot();
        ParseFileController controller = new ParseFileController(restClient, root);

        File file = temporaryFolder.newFile("test");
        ParseFile.State state = new ParseFile.State.Builder()
                .build();
        Task<ParseFile.State> task = controller.saveAsync(state, file, null, null, null);
        task.waitForCompletion();

        // TODO(grantland): Abstract out command runner so we don't have to account for retries.
        verify(restClient, times(5)).execute(any(ParseHttpRequest.class));
        assertTrue(task.isFaulted());
        Exception error = task.getError();
        assertThat(error, instanceOf(ParseException.class));
        assertEquals(ParseException.CONNECTION_FAILED, ((ParseException) error).getCode());
        // Make sure the original file is not deleted and there is no cache file in the folder
        assertEquals(1, root.listFiles().length);
        assertTrue(file.exists());
    }

    //endregion

    //region testFetchAsync

    @Test
    public void testFetchAsyncRequest() {
        // TODO(grantland): Verify proper command is constructed
    }

    @Test
    public void testFetchAsyncAlreadyCancelled() throws Exception {
        ParseHttpClient fileClient = mock(ParseHttpClient.class);
        ParseFileController controller = new ParseFileController(null, null).fileClient(fileClient);

        ParseFile.State state = new ParseFile.State.Builder().build();
        Task<Void> cancellationToken = Task.cancelled();
        Task<File> task = controller.fetchAsync(state, null, null, cancellationToken);
        task.waitForCompletion();

        verify(fileClient, times(0)).execute(any(ParseHttpRequest.class));
        assertTrue(task.isCancelled());
    }

    @Test
    public void testFetchAsyncCached() throws Exception {
        ParseHttpClient fileClient = mock(ParseHttpClient.class);
        File root = temporaryFolder.getRoot();
        ParseFileController controller = new ParseFileController(null, root).fileClient(fileClient);

        File file = new File(root, "cached_file_name");
        ParseFileUtils.writeStringToFile(file, "hello", "UTF-8");

        ParseFile.State state = new ParseFile.State.Builder()
                .name("cached_file_name")
                .build();
        Task<File> task = controller.fetchAsync(state, null, null, null);
        File result = ParseTaskUtils.wait(task);

        verify(fileClient, times(0)).execute(any(ParseHttpRequest.class));
        assertEquals(file, result);
        assertEquals("hello", ParseFileUtils.readFileToString(result, "UTF-8"));
    }

    @Test
    public void testFetchAsyncSuccess() throws Exception {
        byte[] data = "hello".getBytes();
        ParseHttpResponse mockResponse = new ParseHttpResponse.Builder()
                .setStatusCode(200)
                .setTotalSize((long) data.length)
                .setContent(new ByteArrayInputStream(data))
                .build();

        ParseHttpClient fileClient = mock(ParseHttpClient.class);
        when(fileClient.execute(any(ParseHttpRequest.class))).thenReturn(mockResponse);
        // Make sure cache dir does not exist
        File root = new File(temporaryFolder.getRoot(), "cache");
        assertFalse(root.exists());
        ParseFileController controller = new ParseFileController(null, root).fileClient(fileClient);

        ParseFile.State state = new ParseFile.State.Builder()
                .name("file_name")
                .url("url")
                .build();
        Task<File> task = controller.fetchAsync(state, null, null, null);
        File result = ParseTaskUtils.wait(task);

        verify(fileClient, times(1)).execute(any(ParseHttpRequest.class));
        assertTrue(result.exists());
        assertEquals("hello", ParseFileUtils.readFileToString(result, "UTF-8"));
        assertFalse(controller.getTempFile(state).exists());
    }

    @Test
    public void testFetchAsyncFailure() throws Exception {
        // TODO(grantland): Remove once we no longer rely on retry logic.
        ParseRequest.setDefaultInitialRetryDelay(1L);

        ParseHttpClient fileClient = mock(ParseHttpClient.class);
        when(fileClient.execute(any(ParseHttpRequest.class))).thenThrow(new IOException());

        File root = temporaryFolder.getRoot();
        ParseFileController controller = new ParseFileController(null, root).fileClient(fileClient);

        // We need to set url to make getTempFile() work and check it
        ParseFile.State state = new ParseFile.State.Builder()
                .url("test")
                .build();
        Task<File> task = controller.fetchAsync(state, null, null, null);
        task.waitForCompletion();

        // TODO(grantland): Abstract out command runner so we don't have to account for retries.
        verify(fileClient, times(5)).execute(any(ParseHttpRequest.class));
        assertTrue(task.isFaulted());
        Exception error = task.getError();
        assertThat(error, instanceOf(ParseException.class));
        assertEquals(ParseException.CONNECTION_FAILED, ((ParseException) error).getCode());
        assertEquals(0, root.listFiles().length);
        assertFalse(controller.getTempFile(state).exists());
    }

    //endregion
}
