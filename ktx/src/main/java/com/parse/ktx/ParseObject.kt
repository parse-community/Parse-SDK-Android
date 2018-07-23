@file:Suppress("unused")

package com.parse.ktx

import com.parse.ParseObject

/**
 * Get the object as the passed value. This has a chance of throwing an exception if the object
 * cannot be cast properly
 * @param key the key
 * @return the value
 */
@Suppress("UNCHECKED_CAST")
fun <T> ParseObject.getAs(key: String): T = get(key) as T

/**
 * [ParseObject.put] the value, doing nothing if the value is null
 */
fun ParseObject.putOrIgnore(key: String, value: Any?) {
    if (value != null) {
        put(key, value)
    }
}

/**
 * [ParseObject.put] the value, or [ParseObject.remove] it if the value is null
 */
fun ParseObject.putOrRemove(key: String, value: Any?) {
    if (value == null) {
        remove(key)
    } else {
        put(key, value)
    }
}
