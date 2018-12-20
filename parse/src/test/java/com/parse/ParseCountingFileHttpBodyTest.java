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
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ParseCountingFileHttpBodyTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static String getData() {
        char[] chars = new char[64 << 14]; // 1MB
        Arrays.fill(chars, '1');
        return new String(chars);
    }

    private static File makeTestFile(File root) throws IOException {
        File file = new File(root, "test");
        FileWriter writer = new FileWriter(file);
        writer.write(getData());
        writer.close();
        return file;
    }

    @Test
    public void testWriteTo() throws Exception {
        final Semaphore didReportIntermediateProgress = new Semaphore(0);
        final Semaphore finish = new Semaphore(0);

        ParseCountingFileHttpBody body = new ParseCountingFileHttpBody(
                makeTestFile(temporaryFolder.getRoot()), new ProgressCallback() {
            Integer maxProgressSoFar = 0;

            @Override
            public void done(Integer percentDone) {
                if (percentDone > maxProgressSoFar) {
                    maxProgressSoFar = percentDone;
                    assertTrue(percentDone >= 0 && percentDone <= 100);

                    if (percentDone < 100 && percentDone > 0) {
                        didReportIntermediateProgress.release();
                    } else if (percentDone == 100) {
                        finish.release();
                    } else if (percentDone == 0) {
                        // do nothing
                    } else {
                        fail("percentDone should be within 0 - 100");
                    }
                }
            }
        });

        // Check content
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        body.writeTo(output);
        assertArrayEquals(getData().getBytes(), output.toByteArray());
        // Check progress callback
        assertTrue(didReportIntermediateProgress.tryAcquire(5, TimeUnit.SECONDS));
        assertTrue(finish.tryAcquire(5, TimeUnit.SECONDS));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWriteToWithNullOutput() throws Exception {
        ParseCountingFileHttpBody body = new ParseCountingFileHttpBody(
                makeTestFile(temporaryFolder.getRoot()), null);
        body.writeTo(null);
    }
}
