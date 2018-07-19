/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

/**
 * Static utility methods pertaining to {@link Number} instances.
 */
class Numbers {

    /**
     * Add two {@link Number} instances.
     */
    /* package */
    static Number add(Number first, Number second) {
        if (first instanceof Double || second instanceof Double) {
            return first.doubleValue() + second.doubleValue();
        } else if (first instanceof Float || second instanceof Float) {
            return first.floatValue() + second.floatValue();
        } else if (first instanceof Long || second instanceof Long) {
            return first.longValue() + second.longValue();
        } else if (first instanceof Integer || second instanceof Integer) {
            return first.intValue() + second.intValue();
        } else if (first instanceof Short || second instanceof Short) {
            return first.shortValue() + second.shortValue();
        } else if (first instanceof Byte || second instanceof Byte) {
            return first.byteValue() + second.byteValue();
        } else {
            throw new RuntimeException("Unknown number type.");
        }
    }

    /**
     * Subtract two {@link Number} instances.
     */
    /* package */
    static Number subtract(Number first, Number second) {
        if (first instanceof Double || second instanceof Double) {
            return first.doubleValue() - second.doubleValue();
        } else if (first instanceof Float || second instanceof Float) {
            return first.floatValue() - second.floatValue();
        } else if (first instanceof Long || second instanceof Long) {
            return first.longValue() - second.longValue();
        } else if (first instanceof Integer || second instanceof Integer) {
            return first.intValue() - second.intValue();
        } else if (first instanceof Short || second instanceof Short) {
            return first.shortValue() - second.shortValue();
        } else if (first instanceof Byte || second instanceof Byte) {
            return first.byteValue() - second.byteValue();
        } else {
            throw new RuntimeException("Unknown number type.");
        }
    }

    /**
     * Compare two {@link Number} instances.
     */
    /* package */
    static int compare(Number first, Number second) {
        if (first instanceof Double || second instanceof Double) {
            return (int) Math.signum(first.doubleValue() - second.doubleValue());
        } else if (first instanceof Float || second instanceof Float) {
            return (int) Math.signum(first.floatValue() - second.floatValue());
        } else if (first instanceof Long || second instanceof Long) {
            long diff = first.longValue() - second.longValue();
            return (diff < 0) ? -1 : ((diff > 0) ? 1 : 0);
        } else if (first instanceof Integer || second instanceof Integer) {
            return first.intValue() - second.intValue();
        } else if (first instanceof Short || second instanceof Short) {
            return first.shortValue() - second.shortValue();
        } else if (first instanceof Byte || second instanceof Byte) {
            return first.byteValue() - second.byteValue();
        } else {
            throw new RuntimeException("Unknown number type.");
        }
    }
}
