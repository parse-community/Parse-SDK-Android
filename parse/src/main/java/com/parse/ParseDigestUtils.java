/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Static utility helpers to compute {@link MessageDigest}s.
 */
/* package */ class ParseDigestUtils {

    private static final char[] hexArray = "0123456789abcdef".toCharArray();

    private ParseDigestUtils() {
        // no instances allowed
    }

    public static String md5(String string) {
        MessageDigest digester;
        try {
            digester = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        digester.update(string.getBytes());
        byte[] digest = digester.digest();
        return toHex(digest);
    }

    private static String toHex(byte[] bytes) {
        // The returned string will be double the length of the passed array, as it takes two
        // characters to represent any given byte.
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
