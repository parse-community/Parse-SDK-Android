@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.parse.ktx

import com.parse.ParseObject

/**
 * Get the object as the passed value. This has a chance of throwing an exception if the object
 * cannot be cast properly
 * @param key the key
 * @return the value
 */
@Suppress("UNCHECKED_CAST")
inline fun <T> ParseObject.getAs(key: String): T = get(key) as T

/**
 * Get the object optionally as null. This has a chance of throwing an exception if the object
 * cannot be cast properly
 * @param key the key
 * @return the value, or null if nothing is there
 */
@Suppress("UNCHECKED_CAST")
inline fun <T> ParseObject.getAsOrNull(key: String): T? {
    if (containsKey(key)) {
        return get(key) as T?
    }
    return null
}

/**
 * [ParseObject.put] the value, doing nothing if the value is null
 */
inline fun ParseObject.putOrIgnore(key: String, value: Any?) {
    if (value != null) {
        put(key, value)
    }
}

/**
 * [ParseObject.put] the value, or [ParseObject.remove] it if the value is null
 */
inline fun ParseObject.putOrRemove(key: String, value: Any?) {
    if (value == null) {
        remove(key)
    } else {
        put(key, value)
    }
}

/**
 * Get the boolean optionally as null
 * @param key the key
 * @return the value, or null if nothing is there
 */
inline fun ParseObject.getBooleanOrNull(key: String): Boolean? {
    if (containsKey(key)) {
        return getBoolean(key)
    }
    return null
}

/**
 * Get the int optionally as null
 * @param key the key
 * @return the value, or null if nothing is there
 */
inline fun ParseObject.getIntOrNull(key: String): Int? {
    return getNumber(key)?.toInt()
}

/**
 * Get the long optionally as null
 * @param key the key
 * @return the value, or null if nothing is there
 */
inline fun ParseObject.getLongOrNull(key: String): Long? {
    return getNumber(key)?.toLong()
}

/**
 * Get the double optionally as null
 * @param key the key
 * @return the value, or null if nothing is there
 */
inline fun ParseObject.getDoubleOrNull(key: String): Double? {
    return getNumber(key)?.toDouble()
}
