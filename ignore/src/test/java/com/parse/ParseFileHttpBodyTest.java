/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ParseFileHttpBodyTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testInitializeWithFileAndContentType() throws IOException {
    String contentType = "text/plain";
    File file = makeTestFile(temporaryFolder.getRoot());

    ParseFileHttpBody body = new ParseFileHttpBody(file, contentType);

    assertEquals(file.length(), body.getContentLength());
    assertEquals(contentType, body.getContentType());
    // Verify file content
    InputStream content = body.getContent();
    byte[] contentBytes = ParseIOUtils.toByteArray(content);
    ParseIOUtils.closeQuietly(content);
    verifyTestFileContent(contentBytes);
  }

  @Test
  public void testInitializeWithFile() throws IOException {
    File file = makeTestFile(temporaryFolder.getRoot());

    ParseFileHttpBody body = new ParseFileHttpBody(file);

    assertEquals(file.length(), body.getContentLength());
    assertNull(body.getContentType());
    // Verify file content
    InputStream content = body.getContent();
    byte[] contentBytes = ParseIOUtils.toByteArray(content);
    ParseIOUtils.closeQuietly(content);
    verifyTestFileContent(contentBytes);
  }

  @Test
  public void testWriteTo() throws IOException {
    File file = makeTestFile(temporaryFolder.getRoot());
    ParseFileHttpBody body = new ParseFileHttpBody(file);

    // Check content
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    body.writeTo(output);
    verifyTestFileContent(output.toByteArray());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWriteToWithNullOutput() throws Exception {
    ParseFileHttpBody body = new ParseFileHttpBody(makeTestFile(temporaryFolder.getRoot()));
    body.writeTo(null);
  }

  // Generate a test file used for create ParseFileHttpBody, if you change file's content, make sure
  // you also change the test file content in verifyTestFileContent().
  private static File makeTestFile(File root) throws IOException {
    File file = new File(root, "test");
    String content = "content";
    FileWriter writer = new FileWriter(file);
    writer.write(content);
    writer.close();
    return file;
  }

  private static void verifyTestFileContent(byte[] bytes) throws IOException {
    assertArrayEquals("content".getBytes(), bytes);
  }
}
