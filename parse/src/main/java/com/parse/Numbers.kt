/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import kotlin.math.sign

/**
 * Static utility methods pertaining to [Number] instances.
 */
internal object Numbers {
    /**
     * Add two [Number] instances.
     */
    /* package */
    @JvmStatic
    fun add(first: Number, second: Number): Number {
        return if (first is Double || second is Double) {
            first.toDouble() + second.toDouble()
        } else if (first is Float || second is Float) {
            first.toFloat() + second.toFloat()
        } else if (first is Long || second is Long) {
            first.toLong() + second.toLong()
        } else if (first is Int || second is Int) {
            first.toInt() + second.toInt()
        } else if (first is Short || second is Short) {
            first.toShort() + second.toShort()
        } else if (first is Byte || second is Byte) {
            first.toByte() + second.toByte()
        } else {
            throw RuntimeException("Unknown number type.")
        }
    }

    /**
     * Subtract two [Number] instances.
     */
    /* package */
    fun subtract(first: Number, second: Number): Number {
        return if (first is Double || second is Double) {
            first.toDouble() - second.toDouble()
        } else if (first is Float || second is Float) {
            first.toFloat() - second.toFloat()
        } else if (first is Long || second is Long) {
            first.toLong() - second.toLong()
        } else if (first is Int || second is Int) {
            first.toInt() - second.toInt()
        } else if (first is Short || second is Short) {
            first.toShort() - second.toShort()
        } else if (first is Byte || second is Byte) {
            first.toByte() - second.toByte()
        } else {
            throw RuntimeException("Unknown number type.")
        }
    }

    /**
     * Compare two [Number] instances.
     */
    /* package */
    @JvmStatic
    fun compare(first: Number, second: Number): Int {
        return if (first is Double || second is Double) {
            sign(first.toDouble() - second.toDouble()).toInt()
        } else if (first is Float || second is Float) {
            sign(first.toFloat() - second.toFloat()).toInt()
        } else if (first is Long || second is Long) {
            val diff = first.toLong() - second.toLong()
            if (diff < 0) -1 else if (diff > 0) 1 else 0
        } else if (first is Int || second is Int) {
            first.toInt() - second.toInt()
        } else if (first is Short || second is Short) {
            first.toShort() - second.toShort()
        } else if (first is Byte || second is Byte) {
            first.toByte() - second.toByte()
        } else {
            throw RuntimeException("Unknown number type.")
        }
    }
}