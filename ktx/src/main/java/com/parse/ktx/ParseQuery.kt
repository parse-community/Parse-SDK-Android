@file:Suppress("NOTHING_TO_INLINE", "unused")

package com.parse.ktx

import com.parse.ParseException
import com.parse.ParseGeoPoint
import com.parse.ParseObject
import com.parse.ParsePolygon
import com.parse.ParseQuery
import kotlin.reflect.KProperty

/**
 * Parse hard limits [ParseQuery.find] to [ParseQuery.MAX_LIMIT] objects. This will fetch absolutely all the
 * objects. Use with caution, since you could potentially run out of memory if your query is too large.
 * Note that this will modify the current limit of the query
 */
@Throws(ParseException::class)
inline fun <T: ParseObject> ParseQuery<T>.findAll(): List<T> {
    limit = ParseQuery.MAX_LIMIT
    val list = mutableListOf<T>()
    try {
        while (true) {
            val result = find()
            list.addAll(result)
            if (result.size < ParseQuery.MAX_LIMIT) {
                break
            } else {
                setSkip(skip + ParseQuery.MAX_LIMIT)
            }
        }
        return list
    } catch (ex : ParseException) {
        if (ex.code == ParseException.OBJECT_NOT_FOUND) {
            return list
        }
        throw ex
    }
}

/**
 * @see ParseQuery.addDescendingOrder
 */
inline fun <T : ParseObject> ParseQuery<T>.addDescendingOrder(key: KProperty<Any?>): ParseQuery<T> {
    return addDescendingOrder(key.name)
}

/**
 * @see ParseQuery.include
 */
fun <T : ParseObject> ParseQuery<T>.include(vararg properties: KProperty<Any?>): ParseQuery<T> {
    return include(properties.joinToString(".") { it.name })
}

/**
 * @see ParseQuery.orderByAscending
 */
inline fun <T : ParseObject> ParseQuery<T>.orderByAscending(key: KProperty<Any?>): ParseQuery<T> {
    return orderByAscending(key.name)
}

/**
 * @see ParseQuery.orderByDescending
 */
inline fun <T : ParseObject> ParseQuery<T>.orderByDescending(key: KProperty<Any?>): ParseQuery<T> {
    return orderByDescending(key.name)
}

/**
 * @see ParseQuery.selectKeys
 */
fun <T : ParseObject> ParseQuery<T>.selectKeys(keys: Collection<KProperty<Any?>>): ParseQuery<T> {
    return selectKeys(keys.map { it.name })
}

/**
 * @see ParseQuery.whereContainedIn
 */
inline fun <T : ParseObject> ParseQuery<T>.whereContainedIn(key: KProperty<Any?>, values: Collection<Any?>): ParseQuery<T> {
    return whereContainedIn(key.name, values)
}

/**
 * @see ParseQuery.whereContains
 */
inline fun <T : ParseObject> ParseQuery<T>.whereContains(key: KProperty<Any?>, substring: String): ParseQuery<T> {
    return whereContains(key.name, substring)
}

/**
 * @see ParseQuery.whereContainsAll
 */
inline fun <T : ParseObject> ParseQuery<T>.whereContainsAll(key: KProperty<Any?>, values: Collection<ParseObject>): ParseQuery<T> {
    return whereContainsAll(key.name, values)
}

/**
 * @see ParseQuery.whereContainsAllStartsWith
 */
inline fun <T : ParseObject> ParseQuery<T>.whereContainsAllStartsWith(key: KProperty<Any?>, values: Collection<String>): ParseQuery<T> {
    return whereContainsAllStartsWith(key.name, values)
}

/**
 * @see ParseQuery.whereDoesNotExist
 */
inline fun <T : ParseObject> ParseQuery<T>.whereDoesNotExist(key: KProperty<Any?>): ParseQuery<T> {
    return whereDoesNotExist(key.name)
}

/**
 * @see ParseQuery.whereDoesNotMatchKeyInQuery
 */
inline fun <T : ParseObject> ParseQuery<T>.whereDoesNotMatchKeyInQuery(key: KProperty<Any?>, keyInQuery: KProperty<Any?>, query: ParseQuery<ParseObject>): ParseQuery<T> {
    return whereDoesNotMatchKeyInQuery(key.name, keyInQuery.name, query)
}

/**
 * @see ParseQuery.whereDoesNotMatchQuery
 */
inline fun <T : ParseObject> ParseQuery<T>.whereDoesNotMatchQuery(key: KProperty<Any?>, query: ParseQuery<out ParseObject>): ParseQuery<T> {
    return whereDoesNotMatchQuery(key.name, query)
}

/**
 * @see ParseQuery.whereEndsWith
 */
inline fun <T : ParseObject> ParseQuery<T>.whereEndsWith(key: KProperty<Any?>, suffix: String): ParseQuery<T> {
    return whereEndsWith(key.name, suffix)
}

/**
 * @see ParseQuery.whereEqualTo
 */
inline fun <T : ParseObject> ParseQuery<T>.whereEqualTo(key: KProperty<Any?>, value: Any?): ParseQuery<T> {
    return whereEqualTo(key.name, value)
}

/**
 * @see ParseQuery.whereExists
 */
inline fun <T : ParseObject> ParseQuery<T>.whereExists(key: KProperty<Any?>): ParseQuery<T> {
    return whereExists(key.name)
}

/**
 * @see ParseQuery.whereFullText
 */
inline fun <T : ParseObject> ParseQuery<T>.whereFullText(key: KProperty<Any?>, text: String): ParseQuery<T> {
    return whereFullText(key.name, text)
}

/**
 * @see ParseQuery.whereGreaterThan
 */
inline fun <T : ParseObject> ParseQuery<T>.whereGreaterThan(key: KProperty<Any?>, value: Any): ParseQuery<T> {
    return whereGreaterThan(key.name, value)
}

/**
 * @see ParseQuery.whereGreaterThanOrEqualTo
 */
inline fun <T : ParseObject> ParseQuery<T>.whereGreaterThanOrEqualTo(key: KProperty<Any?>, value: Any): ParseQuery<T> {
    return whereGreaterThanOrEqualTo(key.name, value)
}

/**
 * @see ParseQuery.whereLessThan
 */
inline fun <T : ParseObject> ParseQuery<T>.whereLessThan(key: KProperty<Any?>, value: Any): ParseQuery<T> {
    return whereLessThan(key.name, value)
}

/**
 * @see ParseQuery.whereLessThanOrEqualTo
 */
inline fun <T : ParseObject> ParseQuery<T>.whereLessThanOrEqualTo(key: KProperty<Any?>, value: Any): ParseQuery<T> {
    return whereLessThanOrEqualTo(key.name, value)
}

/**
 * @see ParseQuery.whereMatches
 */
inline fun <T : ParseObject> ParseQuery<T>.whereMatches(key: KProperty<Any?>, regex: String): ParseQuery<T> {
    return whereMatches(key.name, regex)
}

/**
 * @see ParseQuery.whereMatches
 */
inline fun <T : ParseObject> ParseQuery<T>.whereMatches(key: KProperty<Any?>, regex: String, modifiers: String): ParseQuery<T> {
    return whereMatches(key.name, regex, modifiers)
}

/**
 * @see ParseQuery.whereMatchesKeyInQuery
 */
inline fun <T : ParseObject> ParseQuery<T>.whereMatchesKeyInQuery(key: KProperty<Any?>, keyInQuery: KProperty<Any?>, query: ParseQuery<ParseObject>): ParseQuery<T> {
    return whereMatchesKeyInQuery(key.name, keyInQuery.name, query)
}

/**
 * @see ParseQuery.whereMatchesQuery
 */
inline fun <T : ParseObject> ParseQuery<T>.whereMatchesQuery(key: KProperty<Any?>, query: ParseQuery<out ParseObject>): ParseQuery<T> {
    return whereMatchesQuery(key.name, query)
}

/**
 * @see ParseQuery.whereNear
 */
inline fun <T : ParseObject> ParseQuery<T>.whereNear(key: KProperty<Any?>, point: ParseGeoPoint): ParseQuery<T> {
    return whereNear(key.name, point)
}

/**
 * @see ParseQuery.whereNotContainedIn
 */
inline fun <T : ParseObject> ParseQuery<T>.whereNotContainedIn(key: KProperty<Any?>, values: Collection<Any?>): ParseQuery<T> {
    return whereNotContainedIn(key.name, values)
}

/**
 * @see ParseQuery.whereNotEqualTo
 */
inline fun <T : ParseObject> ParseQuery<T>.whereNotEqualTo(key: KProperty<Any?>, value: Any?): ParseQuery<T> {
    return whereNotEqualTo(key.name, value)
}

/**
 * @see ParseQuery.wherePolygonContains
 */
inline fun <T : ParseObject> ParseQuery<T>.wherePolygonContains(key: KProperty<Any?>,  point: ParseGeoPoint): ParseQuery<T> {
    return wherePolygonContains(key.name, point)
}

/**
 * @see ParseQuery.whereStartsWith
 */
inline fun <T : ParseObject> ParseQuery<T>.whereStartsWith(key: KProperty<Any?>, prefix: String): ParseQuery<T> {
    return whereStartsWith(key.name, prefix)
}

/**
 * @see ParseQuery.whereWithinGeoBox
 */
inline fun <T : ParseObject> ParseQuery<T>.whereWithinGeoBox(key: KProperty<Any?>, southwest: ParseGeoPoint, northeast: ParseGeoPoint): ParseQuery<T> {
    return whereWithinGeoBox(key.name, southwest, northeast)
}

/**
 * @see ParseQuery.whereWithinKilometers
 */
inline fun <T : ParseObject> ParseQuery<T>.whereWithinKilometers(key: KProperty<Any?>, point: ParseGeoPoint, maxDistance: Double): ParseQuery<T> {
    return whereWithinKilometers(key.name, point, maxDistance)
}

/**
 * @see ParseQuery.whereWithinMiles
 */
inline fun <T : ParseObject> ParseQuery<T>.whereWithinMiles(key: KProperty<Any?>, point: ParseGeoPoint, maxDistance: Double): ParseQuery<T> {
    return whereWithinMiles(key.name, point, maxDistance)
}

/**
 * @see ParseQuery.whereWithinPolygon
 */
inline fun <T : ParseObject> ParseQuery<T>.whereWithinPolygon(key: KProperty<Any?>, points: List<ParseGeoPoint>): ParseQuery<T> {
    return whereWithinPolygon(key.name, points)
}

/**
 * @see ParseQuery.whereWithinPolygon
 */
inline fun <T : ParseObject> ParseQuery<T>.whereWithinPolygon(key: KProperty<Any?>, polygon: ParsePolygon): ParseQuery<T> {
    return whereWithinPolygon(key.name, polygon)
}

/**
 * @see ParseQuery.whereWithinRadians
 */
inline fun <T : ParseObject> ParseQuery<T>.whereWithinRadians(key: KProperty<Any?>, point: ParseGeoPoint, maxDistance: Double): ParseQuery<T> {
    return whereWithinRadians(key.name, point, maxDistance)
}
