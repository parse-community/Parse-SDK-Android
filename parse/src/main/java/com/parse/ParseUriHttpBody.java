/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import static com.parse.Parse.getApplicationContext;

import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import com.parse.http.ParseHttpBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class ParseUriHttpBody extends ParseHttpBody {

    /* package */ final Uri uri;

    public ParseUriHttpBody(Uri uri) {
        this(uri, null);
    }

    public ParseUriHttpBody(Uri uri, String contentType) {
        super(contentType, getUriLength(uri));
        this.uri = uri;
    }

    private static long getUriLength(Uri uri) {
        long length = -1;

        try (Cursor cursor = getApplicationContext().getContentResolver().query(uri, null, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (!cursor.isNull(sizeIndex)) {
                    length = cursor.getLong(sizeIndex);
                }
            }
        }
        if (length == -1) {
            try {
                ParcelFileDescriptor parcelFileDescriptor = getApplicationContext().getContentResolver().openFileDescriptor(uri, "r");
                if (parcelFileDescriptor != null) {
                    length = parcelFileDescriptor.getStatSize();
                    parcelFileDescriptor.close();
                }
            } catch (IOException ignored) {
            }
        }
        if (length == -1) {
            try {
                AssetFileDescriptor assetFileDescriptor = getApplicationContext().getContentResolver().openAssetFileDescriptor(uri, "r");
                if (assetFileDescriptor != null) {
                    length = assetFileDescriptor.getLength();
                    assetFileDescriptor.close();
                }
            } catch (IOException ignored) {
            }
        }
        return length;
    }

    @Override
    public InputStream getContent() throws IOException {
        return getApplicationContext().getContentResolver().openInputStream(uri);
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("Output stream can not be null");
        }

        final InputStream fileInput = getApplicationContext().getContentResolver().openInputStream(uri);
        try {
            ParseIOUtils.copy(fileInput, out);
        } finally {
            ParseIOUtils.closeQuietly(fileInput);
        }
    }
}
