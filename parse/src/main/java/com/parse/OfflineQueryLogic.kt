/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse

import com.parse.Numbers.compare
import com.parse.ParseQuery.*
import com.parse.PointerEncoder.Companion.get
import com.parse.boltsinternal.Task
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.regex.Pattern

internal class OfflineQueryLogic     /* package */(private val store: OfflineStore) {
    /**
     * Creates a matcher that handles $inQuery constraints.
     */
    private fun <T : ParseObject> createInQueryMatcher(
        user: ParseUser,
        constraint: Any?, key: String
    ): ConstraintMatcher<T> {
        // TODO(grantland): Convert builder to state t6941155
        val query = (constraint as State.Builder<T>?)!!.build()
        return object : SubQueryMatcher<T>(user, query) {
            @Throws(ParseException::class)
            override fun matches(`object`: T, results: List<T>?): Boolean {
                val value = getValue(`object`, key)
                return matchesInConstraint(results, value)
            }
        }
    }

    /**
     * Creates a matcher that handles $notInQuery constraints.
     */
    private fun <T : ParseObject> createNotInQueryMatcher(
        user: ParseUser,
        constraint: Any?, key: String
    ): ConstraintMatcher<T> {
        val inQueryMatcher: ConstraintMatcher<T> = createInQueryMatcher(user, constraint, key)
        return object : ConstraintMatcher<T>(user) {
            override fun matchesAsync(`object`: T, db: ParseSQLiteDatabase?): Task<Boolean> {
                return inQueryMatcher.matchesAsync(`object`, db)
                    .onSuccess { task: Task<Boolean> -> !task.result }
            }
        }
    }

    /**
     * Creates a matcher that handles $select constraints.
     */
    private fun <T : ParseObject> createSelectMatcher(
        user: ParseUser,
        constraint: Any?, key: String
    ): ConstraintMatcher<T> {
        val constraintMap = constraint as Map<*, *>
        // TODO(grantland): Convert builder to state t6941155
        val query = (constraintMap["query"] as State.Builder<T>?)!!.build()
        val resultKey = constraintMap["key"] as String?
        return object : SubQueryMatcher<T>(user, query) {
            @Throws(ParseException::class)
            override fun matches(`object`: T, results: List<T>?): Boolean {
                val value = getValue(`object`, key)
                for (result in results!!) {
                    val resultValue = getValue(result, resultKey)
                    if (matchesEqualConstraint(value, resultValue)) {
                        return true
                    }
                }
                return false
            }
        }
    }

    /**
     * Creates a matcher that handles $dontSelect constraints.
     */
    private fun <T : ParseObject> createDontSelectMatcher(
        user: ParseUser,
        constraint: Any?, key: String
    ): ConstraintMatcher<T> {
        val selectMatcher: ConstraintMatcher<T> = createSelectMatcher(user, constraint, key)
        return object : ConstraintMatcher<T>(user) {
            override fun matchesAsync(`object`: T, db: ParseSQLiteDatabase?): Task<Boolean> {
                return selectMatcher.matchesAsync(`object`, db)
                    .onSuccess { task: Task<Boolean> -> !task.result }
            }
        }
    }

    /*
     * Creates a matcher for a particular constraint operator.
     */
    private fun <T : ParseObject> createMatcher(
        user: ParseUser,
        operator: String, constraint: Any?, key: String,
        allKeyConstraints: KeyConstraints
    ): ConstraintMatcher<T> {
        return when (operator) {
            "\$inQuery" -> createInQueryMatcher(user, constraint, key)
            "\$notInQuery" -> createNotInQueryMatcher(user, constraint, key)
            "\$select" -> createSelectMatcher(user, constraint, key)
            "\$dontSelect" -> createDontSelectMatcher(user, constraint, key)
            else ->                 /*
                 * All of the other operators we know about are stateless, so return a simple matcher.
                 */object : ConstraintMatcher<T>(user) {
                override fun matchesAsync(`object`: T, db: ParseSQLiteDatabase?): Task<Boolean> {
                    return try {
                        val value = getValue(`object`, key)
                        Task.forResult(
                            matchesStatelessConstraint(
                                operator, constraint, value,
                                allKeyConstraints
                            )
                        )
                    } catch (e: ParseException) {
                        Task.forError(e)
                    }
                }
            }
        }
    }

    /**
     * Handles $or queries.
     */
    private fun <T : ParseObject> createOrMatcher(
        user: ParseUser,
        queries: ArrayList<QueryConstraints>?
    ): ConstraintMatcher<T> {
        // Make a list of all the matchers to OR together.
        val matchers = ArrayList<ConstraintMatcher<T>>()
        for (constraints in queries!!) {
            val matcher: ConstraintMatcher<T> = createMatcher(user, constraints)
            matchers.add(matcher)
        }
        /*
         * Now OR together the constraints for each query.
         */return object : ConstraintMatcher<T>(user) {
            override fun matchesAsync(`object`: T, db: ParseSQLiteDatabase?): Task<Boolean> {
                var task = Task.forResult(false)
                for (matcher in matchers) {
                    task = task.onSuccessTask { task1: Task<Boolean> ->
                        if (task1.result) {
                            return@onSuccessTask task1
                        }
                        matcher.matchesAsync(`object`, db)
                    }
                }
                return task
            }
        }
    }

    /**
     * Returns a ConstraintMatcher that return true iff the object matches QueryConstraints. This
     * takes in a SQLiteDatabase connection because SQLite is finicky about nesting connections, so we
     * want to reuse them whenever possible.
     */
    private fun <T : ParseObject> createMatcher(
        user: ParseUser,
        queryConstraints: QueryConstraints
    ): ConstraintMatcher<T> {
        // Make a list of all the matchers to AND together.
        val matchers = ArrayList<ConstraintMatcher<T>>()
        for (key in queryConstraints.keys) {
            val queryConstraintValue = queryConstraints[key]
            if (key == "\$or") {
                /*
                 * A set of queries to be OR-ed together.
                 */
                val matcher: ConstraintMatcher<T> =
                    createOrMatcher(user, queryConstraintValue as ArrayList<QueryConstraints>?)
                matchers.add(matcher)
            } else if (queryConstraintValue is KeyConstraints) {
                /*
                 * It's a set of constraints that should be AND-ed together.
                 */
                for (operator in queryConstraintValue.keys) {
                    val keyConstraintValue = queryConstraintValue[operator]
                    val matcher: ConstraintMatcher<T> =
                        createMatcher(user, operator, keyConstraintValue, key, queryConstraintValue)
                    matchers.add(matcher)
                }
            } else if (queryConstraintValue is RelationConstraint) {
                /*
                 * It's a $relatedTo constraint.
                 */
                matchers.add(object : ConstraintMatcher<T>(user) {
                    override fun matchesAsync(
                        `object`: T,
                        db: ParseSQLiteDatabase?
                    ): Task<Boolean> {
                        return Task.forResult(queryConstraintValue.relation.hasKnownObject(`object`))
                    }
                })
            } else {
                /*
                 * It's not a set of constraints, so it's just a value to compare against.
                 */
                matchers.add(object : ConstraintMatcher<T>(user) {
                    override fun matchesAsync(
                        `object`: T,
                        db: ParseSQLiteDatabase?
                    ): Task<Boolean> {
                        val objectValue: Any? = try {
                            getValue(`object`, key)
                        } catch (e: ParseException) {
                            return Task.forError(e)
                        }
                        return Task.forResult(
                            matchesEqualConstraint(
                                queryConstraintValue,
                                objectValue
                            )
                        )
                    }
                })
            }
        }

        /*
         * Now AND together the constraints for each key.
         */return object : ConstraintMatcher<T>(user) {
            override fun matchesAsync(`object`: T, db: ParseSQLiteDatabase?): Task<Boolean> {
                var task = Task.forResult(true)
                for (matcher in matchers) {
                    task = task.onSuccessTask { task1: Task<Boolean> ->
                        if (!task1.result) {
                            return@onSuccessTask task1
                        }
                        matcher.matchesAsync(`object`, db)
                    }
                }
                return task
            }
        }
    }

    /**
     * Returns a ConstraintMatcher that return true iff the object matches the given query's
     * constraints. This takes in a SQLiteDatabase connection because SQLite is finicky about nesting
     * connections, so we want to reuse them whenever possible.
     *
     * @param state The query.
     * @param user  The user we are testing ACL access for.
     * @param <T>   Subclass of ParseObject.
     * @return A new instance of ConstraintMatcher.
    </T> */
    /* package */
    fun <T : ParseObject> createMatcher(
        state: State<T>, user: ParseUser
    ): ConstraintMatcher<T> {
        val ignoreACLs = state.ignoreACLs()
        val constraintMatcher: ConstraintMatcher<T> = createMatcher(user, state.constraints())
        return object : ConstraintMatcher<T>(user) {
            override fun matchesAsync(`object`: T, db: ParseSQLiteDatabase?): Task<Boolean> {
                return if (!ignoreACLs && !hasReadAccess(user, `object`)) {
                    Task.forResult(false)
                } else constraintMatcher.matchesAsync(`object`, db)
            }
        }
    }

    /**
     * A decider decides whether the given value matches the given constraint.
     */
    private interface Decider {
        fun decide(constraint: Any?, value: Any?): Boolean
    }

    /**
     * A query is converted into a complex hierarchy of ConstraintMatchers that evaluate whether a
     * ParseObject matches each part of the query. This is done because some parts of the query (such
     * as $inQuery) are much more efficient if we can do some preprocessing. This makes some parts of
     * the query matching stateful.
     */
    /* package */
    internal abstract class ConstraintMatcher<T : ParseObject>(  /* package */
        val user: ParseUser
    ) {
        /* package */
        abstract fun matchesAsync(`object`: T, db: ParseSQLiteDatabase?): Task<Boolean>
    }

    private abstract inner class SubQueryMatcher<T : ParseObject>(
        user: ParseUser,
        private val subQuery: ParseQuery.State<T>
    ) : ConstraintMatcher<T>(user) {
        private var subQueryResults: Task<List<T>?>? = null
        override fun matchesAsync(`object`: T, db: ParseSQLiteDatabase?): Task<Boolean> {
            /*
             * As an optimization, we do this lazily. Then we may not have to do it at all, if this part
             * of the query gets short-circuited.
             */
            if (subQueryResults == null) {
                //TODO (grantland): We need to pass through the original pin we were limiting the parent
                // query on.
                subQueryResults = store.findAsync(subQuery, user, null, db!!)
            }
            return subQueryResults!!.onSuccess { task: Task<List<T>?> ->
                matches(
                    `object`,
                    task.result
                )
            }
        }

        @Throws(ParseException::class)
        protected abstract fun matches(`object`: T, results: List<T>?): Boolean
    }

    companion object {
        /**
         * Returns an Object's value for a given key, handling any special keys like objectId. Also
         * handles dot-notation for traversing into objects.
         */
        @Throws(ParseException::class)
        private fun <T: ParseObject> getValue(container: T, key: String?): Any? {
            return getValue(container, key, 0)
        }

        @Throws(ParseException::class)
        private fun getValue(container: Any?, key: String?, depth: Int): Any? {
            if (key!!.contains(".")) {
                val parts = key.split("\\.").toTypedArray()
                val value = getValue(container, parts[0], depth + 1)
                /*
             * Only Maps and JSONObjects can be dotted into for getting values, so we should reject
             * anything like ParseObjects and arrays.
             */if (!(value == null || value === JSONObject.NULL || value is Map<*, *> || value is JSONObject)) {
                    // Technically, they can search inside the REST representation of some nested objects.
                    if (depth > 0) {
                        var restFormat: Any? = null
                        try {
                            restFormat = get().encode(value)
                        } catch (e: Exception) {
                            // Well, if we couldn't encode it, it's not searchable.
                        }
                        if (restFormat is JSONObject) {
                            return getValue(restFormat, parts[1], depth + 1)
                        }
                    }
                    throw ParseException(
                        ParseException.INVALID_QUERY, String.format(
                            "Key %s is invalid.",
                            key
                        )
                    )
                }
                return getValue(value, parts[1], depth + 1)
            }
            return when {
                container is ParseObject -> {

                    // The object needs to have been fetched already if we are going to sort by one of its fields.
                    if (!container.isDataAvailable()) {
                        throw ParseException(
                            ParseException.INVALID_NESTED_KEY, String.format(
                                "Bad key: %s",
                                key
                            )
                        )
                    }
                    when (key) {
                        "objectId" -> container.objectId
                        "createdAt", "_created_at" -> container.createdAt
                        "updatedAt", "_updated_at" -> container.updatedAt
                        else -> container[key]
                    }
                }
                container is JSONObject -> {
                    container.opt(key)
                }
                container is Map<*, *> -> {
                    container[key]
                }
                container === JSONObject.NULL -> {
                    null
                }
                container == null -> {
                    null
                }
                else -> {
                    throw ParseException(
                        ParseException.INVALID_NESTED_KEY,
                        String.format("Bad key: %s", key)
                    )
                }
            }
        }

        /**
         * General purpose compareTo that figures out the right types to use. The arguments should be
         * atomic values to compare, such as Dates, Strings, or Numbers -- not composite objects or
         * arrays.
         */
        private fun compareTo(lhs: Any?, rhs: Any?): Int {
            val lhsIsNullOrUndefined = lhs === JSONObject.NULL || lhs == null
            val rhsIsNullOrUndefined = rhs === JSONObject.NULL || rhs == null
            return if (lhsIsNullOrUndefined || rhsIsNullOrUndefined) {
                if (!lhsIsNullOrUndefined) {
                    1
                } else if (!rhsIsNullOrUndefined) {
                    -1
                } else {
                    0
                }
            } else if (lhs is Date && rhs is Date) {
                lhs.compareTo(rhs as Date?)
            } else if (lhs is String && rhs is String) {
                lhs.compareTo((rhs as String?)!!)
            } else if (lhs is Number && rhs is Number) {
                compare((lhs as Number?)!!, (rhs as Number?)!!)
            } else {
                throw IllegalArgumentException(
                    String.format(
                        "Cannot compare %s against %s",
                        lhs,
                        rhs
                    )
                )
            }
        }

        /**
         * Returns true if decider returns true for any value in the given list.
         */
        private fun compareList(constraint: Any?, values: List<*>, decider: Decider): Boolean {
            for (value in values) {
                if (decider.decide(constraint, value)) {
                    return true
                }
            }
            return false
        }

        /**
         * Returns true if decider returns true for any value in the given list.
         */
        private fun compareArray(constraint: Any?, values: JSONArray, decider: Decider): Boolean {
            for (i in 0 until values.length()) {
                try {
                    if (decider.decide(constraint, values[i])) {
                        return true
                    }
                } catch (e: JSONException) {
                    // This can literally never happen.
                    throw RuntimeException(e)
                }
            }
            return false
        }

        /**
         * Returns true if the decider returns true for the given value and the given constraint. This
         * method handles Mongo's logic where an item can match either an item itself, or any item within
         * the item, if the item is an array.
         */
        private fun compare(constraint: Any?, value: Any?, decider: Decider): Boolean {
            return if (value is List<*>) {
                compareList(
                    constraint,
                    value,
                    decider
                )
            } else if (value is JSONArray) {
                compareArray(constraint, value, decider)
            } else {
                decider.decide(constraint, value)
            }
        }

        /**
         * Implements simple equality constraints. This emulates Mongo's behavior where "equals" can mean
         * array containment.
         */
        private fun matchesEqualConstraint(constraint: Any?, value: Any?): Boolean {
            if (constraint == null || value == null) {
                return constraint === value
            }
            if (constraint is Number && value is Number) {
                return compareTo(constraint, value) == 0
            }
            if (constraint is ParseGeoPoint && value is ParseGeoPoint) {
                return (constraint.latitude == value.latitude
                        && constraint.longitude == value.longitude)
            }
            if (constraint is ParsePolygon && value is ParsePolygon) {
                return constraint == value
            }
            val decider: Decider
            if (isStartsWithRegex(constraint)) {
                decider = object : Decider {
                    override fun decide(constraint: Any?, value: Any?): Boolean {
                        return (value as String).matches(
                            Regex((constraint as KeyConstraints)["\$regex"].toString())
                        )
                    }
                }
            } else {
                decider = object : Decider {
                    override fun decide(constraint: Any?, value: Any?): Boolean {
                        return value?.equals(constraint) ?: false
                    }
                }
            }
            return compare(constraint, value, decider)
        }

        /**
         * Matches $ne constraints.
         */
        private fun matchesNotEqualConstraint(constraint: Any?, value: Any?): Boolean {
            return !matchesEqualConstraint(constraint, value)
        }

        /**
         * Matches $lt constraints.
         */
        private fun matchesLessThanConstraint(constraint: Any?, value: Any?): Boolean {
            return compare(constraint, value, object : Decider {
                override fun decide(constraint: Any?, value: Any?): Boolean {
                    if (value == null || value === JSONObject.NULL) {
                        return false
                    }
                    return compareTo(constraint, value) > 0
                }
            })
        }

        /**
         * Matches $lte constraints.
         */
        private fun matchesLessThanOrEqualToConstraint(constraint: Any?, value: Any?): Boolean {
            return compare(constraint, value, object : Decider {
                override fun decide(constraint: Any?, value: Any?): Boolean {
                    if (value == null || value === JSONObject.NULL) {
                        return false
                    }
                    return compareTo(constraint, value) >= 0
                }
            })
        }

        /**
         * Matches $gt constraints.
         */
        private fun matchesGreaterThanConstraint(constraint: Any?, value: Any?): Boolean {
            return compare(constraint, value, object : Decider {
                override fun decide(constraint: Any?, value: Any?): Boolean {
                    if (value == null || value === JSONObject.NULL) {
                        return false
                    }
                    return compareTo(constraint, value) < 0
                }
            })
        }

        /**
         * Matches $gte constraints.
         */
        private fun matchesGreaterThanOrEqualToConstraint(constraint: Any?, value: Any?): Boolean {
            return compare(constraint, value, object : Decider {
                override fun decide(constraint: Any?, value: Any?): Boolean {
                    if (value == null || value === JSONObject.NULL) {
                        return false
                    }
                    return compareTo(constraint, value) <= 0
                }
            })
        }

        /**
         * Matches $in constraints.
         * $in returns true if the intersection of value and constraint is not an empty set.
         */
        private fun matchesInConstraint(constraint: Any?, value: Any?): Boolean {
            if (constraint is Collection<*>) {
                for (requiredItem in constraint) {
                    if (matchesEqualConstraint(requiredItem, value)) {
                        return true
                    }
                }
                return false
            }
            throw IllegalArgumentException("Constraint type not supported for \$in queries.")
        }

        /**
         * Matches $nin constraints.
         */
        private fun matchesNotInConstraint(constraint: Any?, value: Any?): Boolean {
            return !matchesInConstraint(constraint, value)
        }

        /**
         * Matches $all constraints.
         */
        private fun matchesAllConstraint(constraint: Any?, value: Any?): Boolean {
            var constraint = constraint
            if (value == null || value === JSONObject.NULL) {
                return false
            }
            require(value is Collection<*>) { "Value type not supported for \$all queries." }
            if (constraint is Collection<*>) {
                if (isAnyValueRegexStartsWith(constraint)) {
                    constraint = cleanRegexStartsWith(constraint)
                    requireNotNull(constraint) { "All values in \$all queries must be of starting with regex or non regex." }
                }
                for (requiredItem in constraint as Collection<*>) {
                    if (!matchesEqualConstraint(requiredItem, value)) {
                        return false
                    }
                }
                return true
            }
            throw IllegalArgumentException("Constraint type not supported for \$all queries.")
        }

        /**
         * Check if any of the collection constraints is a regex to match strings that starts with another
         * string.
         */
        private fun isAnyValueRegexStartsWith(constraints: Collection<*>): Boolean {
            for (constraint in constraints) {
                if (isStartsWithRegex(constraint)) {
                    return true
                }
            }
            return false
        }

        /**
         * Cleans all regex constraints. If any of the constraints is not a regex, then null is returned.
         * All values in a $all constraint must be a starting with another string regex.
         */
        private fun cleanRegexStartsWith(constraints: Collection<*>): Collection<*>? {
            val cleanedValues = ArrayList<KeyConstraints>()
            for (constraint in constraints) {
                if (constraint !is KeyConstraints) {
                    return null
                }
                val cleanedRegex = cleanRegexStartsWith(constraint)
                    ?: return null
                cleanedValues.add(cleanedRegex)
            }
            return cleanedValues
        }

        /**
         * Creates a regex pattern to match a substring at the beginning of another string.
         *
         *
         * If given string is not a regex to match a string at the beginning of another string, then null
         * is returned.
         */
        private fun cleanRegexStartsWith(regex: KeyConstraints): KeyConstraints? {
            if (!isStartsWithRegex(regex)) {
                return null
            }

            // remove all instances of \Q and \E from the remaining text & escape single quotes
            val literalizedString = (regex["\$regex"] as String)
                .replace("([^\\\\])(\\\\E)".toRegex(), "$1")
                .replace("([^\\\\])(\\\\Q)".toRegex(), "$1")
                .replace("^\\\\E".toRegex(), "")
                .replace("^\\\\Q".toRegex(), "")
                .replace("([^'])'".toRegex(), "$1''")
                .replace("^'([^'])".toRegex(), "''$1")
            regex["\$regex"] = "$literalizedString.*"
            return regex
        }

        /**
         * Check if given constraint is a regex to match strings that starts with another string.
         */
        private fun isStartsWithRegex(constraint: Any?): Boolean {
            if (constraint == null || constraint !is KeyConstraints) {
                return false
            }
            val keyConstraints = constraint
            return keyConstraints.size == 1 && keyConstraints.containsKey("\$regex") &&
                    (keyConstraints["\$regex"] as String?)!!.startsWith("^")
        }

        /**
         * Matches $regex constraints.
         */
        @Throws(ParseException::class)
        private fun matchesRegexConstraint(
            constraint: Any?,
            value: Any?,
            options: String = ""
        ): Boolean {
            if (value == null || value === JSONObject.NULL) {
                return false
            }
            if (!options.matches(Regex("^[imxs]*$"))) {
                throw ParseException(
                    ParseException.INVALID_QUERY, String.format(
                        "Invalid regex options: %s", options
                    )
                )
            }
            var flags = 0
            if (options.contains("i")) {
                flags = flags or Pattern.CASE_INSENSITIVE
            }
            if (options.contains("m")) {
                flags = flags or Pattern.MULTILINE
            }
            if (options.contains("x")) {
                flags = flags or Pattern.COMMENTS
            }
            if (options.contains("s")) {
                flags = flags or Pattern.DOTALL
            }
            val regex = constraint as String?
            val pattern = Pattern.compile(regex, flags)
            val matcher = pattern.matcher(value as String?)
            return matcher.find()
        }

        /**
         * Matches $exists constraints.
         */
        private fun matchesExistsConstraint(constraint: Any?, value: Any?): Boolean {
            /*
         * In the Android SDK, null means "undefined", and JSONObject.NULL means "null".
         */
            return if (constraint != null && constraint as Boolean) {
                value != null && value !== JSONObject.NULL
            } else {
                value == null || value === JSONObject.NULL
            }
        }

        /**
         * Matches $nearSphere constraints.
         */
        private fun matchesNearSphereConstraint(
            constraint: Any?, value: Any?,
            maxDistance: Double?
        ): Boolean {
            if (value == null || value === JSONObject.NULL) {
                return false
            }
            if (maxDistance == null) {
                return true
            }
            val point1 = constraint as ParseGeoPoint?
            val point2 = value as ParseGeoPoint
            return point1!!.distanceInRadiansTo(point2) <= maxDistance
        }

        /**
         * Matches $within constraints.
         */
        @Throws(ParseException::class)
        private fun matchesWithinConstraint(constraint: Any?, value: Any?): Boolean {
            if (value == null || value === JSONObject.NULL) {
                return false
            }
            val constraintMap = constraint as HashMap<String, ArrayList<ParseGeoPoint>>?
            val box = constraintMap!!["\$box"]!!
            val southwest = box[0]
            val northeast = box[1]
            val target = value as ParseGeoPoint
            if (northeast.longitude < southwest.longitude) {
                throw ParseException(
                    ParseException.INVALID_QUERY,
                    "whereWithinGeoBox queries cannot cross the International Date Line."
                )
            }
            if (northeast.latitude < southwest.latitude) {
                throw ParseException(
                    ParseException.INVALID_QUERY,
                    "The southwest corner of a geo box must be south of the northeast corner."
                )
            }
            if (northeast.longitude - southwest.longitude > 180) {
                throw ParseException(
                    ParseException.INVALID_QUERY,
                    "Geo box queries larger than 180 degrees in longitude are not supported. "
                            + "Please check point order."
                )
            }
            return target.latitude >= southwest.latitude && target.latitude <= northeast.latitude && target.longitude >= southwest.longitude && target.longitude <= northeast.longitude
        }

        /**
         * Matches $geoIntersects constraints.
         */
        private fun matchesGeoIntersectsConstraint(constraint: Any?, value: Any?): Boolean {
            if (value == null || value === JSONObject.NULL) {
                return false
            }
            val constraintMap = constraint as HashMap<String, ParseGeoPoint>?
            val point = constraintMap!!["\$point"]
            val target = value as ParsePolygon
            return target.containsPoint(point)
        }

        /**
         * Matches $geoWithin constraints.
         */
        private fun matchesGeoWithinConstraint(constraint: Any?, value: Any?): Boolean {
            if (value == null || value === JSONObject.NULL) {
                return false
            }
            val constraintMap = constraint as HashMap<String, List<ParseGeoPoint>>?
            val points = constraintMap!!["\$polygon"]!!
            val polygon = ParsePolygon(points)
            val point = value as ParseGeoPoint
            return polygon.containsPoint(point)
        }

        /**
         * Returns true iff the given value matches the given operator and constraint.
         *
         * @throws UnsupportedOperationException if the operator is not one this function can handle.
         */
        @Throws(ParseException::class)
        private fun matchesStatelessConstraint(
            operator: String, constraint: Any?,
            value: Any?, allKeyConstraints: KeyConstraints
        ): Boolean {
            return when (operator) {
                "\$ne" -> matchesNotEqualConstraint(constraint, value)
                "\$lt" -> matchesLessThanConstraint(constraint, value)
                "\$lte" -> matchesLessThanOrEqualToConstraint(constraint, value)
                "\$gt" -> matchesGreaterThanConstraint(constraint, value)
                "\$gte" -> matchesGreaterThanOrEqualToConstraint(
                    constraint,
                    value
                )
                "\$in" -> matchesInConstraint(constraint, value)
                "\$nin" -> matchesNotInConstraint(constraint, value)
                "\$all" -> matchesAllConstraint(constraint, value)
                "\$regex" -> {
                    val regexOptions = allKeyConstraints["\$options"] as String
                    matchesRegexConstraint(constraint, value, regexOptions)
                }
                "\$options" ->                 // No need to do anything. This is handled by $regex.
                    true
                "\$exists" -> matchesExistsConstraint(constraint, value)
                "\$nearSphere" -> {
                    val maxDistance = allKeyConstraints["\$maxDistance"] as Double?
                    matchesNearSphereConstraint(constraint, value, maxDistance)
                }
                "\$maxDistance" ->                 // No need to do anything. This is handled by $nearSphere.
                    true
                "\$within" -> matchesWithinConstraint(constraint, value)
                "\$geoWithin" -> matchesGeoWithinConstraint(constraint, value)
                "\$geoIntersects" -> matchesGeoIntersectsConstraint(
                    constraint,
                    value
                )
                else -> throw UnsupportedOperationException(
                    String.format(
                        "The offline store does not yet support the %s operator.", operator
                    )
                )
            }
        }

        /**
         * Returns true iff the object is visible based on its read ACL and the given user objectId.
         */
        /* package */
        @JvmStatic
        fun <T : ParseObject?> hasReadAccess(user: ParseUser?, `object`: T): Boolean {
            if (user === `object`) {
                return true
            }
            val acl = `object`!!.getACL() ?: return true
            return if (acl.publicReadAccess) {
                true
            } else user != null && acl.getReadAccess(user)
            // TODO: Implement roles.
        }

        /**
         * Returns true iff the object is visible based on its read ACL and the given user objectId.
         */
        /* package */
        @JvmStatic
        fun <T : ParseObject?> hasWriteAccess(user: ParseUser?, `object`: T): Boolean {
            if (user === `object`) {
                return true
            }
            val acl = `object`!!.getACL() ?: return true
            return if (acl.publicWriteAccess) {
                true
            } else user != null && acl.getWriteAccess(user)
            // TODO: Implement roles.
        }

        /**
         * Sorts the given array based on the parameters of the given query.
         */
        /* package */
        @JvmStatic
        @Throws(ParseException::class)
        fun <T : ParseObject> sort(results: List<T>?, state: State<T>) {
            val keys = state.order()
            // Do some error checking just for maximum compatibility with the server.
            for (key in state.order()) {
                if (!key.matches(Regex("^-?[A-Za-z][A-Za-z0-9_]*$"))) {
                    if ("_created_at" != key && "_updated_at" != key) {
                        throw ParseException(
                            ParseException.INVALID_KEY_NAME, String.format(
                                "Invalid key name: \"%s\".", key
                            )
                        )
                    }
                }
            }

            // See if there's a $nearSphere constraint that will override the other sort parameters.
            var mutableNearSphereKey: String? = null
            var mutableNearSphereValue: ParseGeoPoint? = null
            for (queryKey in state.constraints().keys) {
                val queryKeyConstraints = state.constraints()[queryKey]
                if (queryKeyConstraints is KeyConstraints) {
                    if (queryKeyConstraints.containsKey("\$nearSphere")) {
                        mutableNearSphereKey = queryKey
                        mutableNearSphereValue =
                            queryKeyConstraints["\$nearSphere"] as ParseGeoPoint?
                    }
                }
            }
            val nearSphereKey = mutableNearSphereKey
            val nearSphereValue = mutableNearSphereValue

            // If there's nothing to sort based on, then don't do anything.
            if (keys.size == 0 && mutableNearSphereKey == null) {
                return
            }

            /*
         * TODO(klimt): Test whether we allow dotting into objects for sorting.
         */
            results?.sortedWith(kotlin.Comparator { lhs: T, rhs: T ->
                if (nearSphereKey != null) {
                    val lhsPoint: ParseGeoPoint
                    val rhsPoint: ParseGeoPoint
                    try {
                        lhsPoint = getValue(lhs, nearSphereKey) as ParseGeoPoint
                        rhsPoint = getValue(rhs, nearSphereKey) as ParseGeoPoint
                    } catch (e: ParseException) {
                        throw RuntimeException(e)
                    }

                    // GeoPoints can't be null if there's a $nearSphere.
                    val lhsDistance = lhsPoint.distanceInRadiansTo(nearSphereValue)
                    val rhsDistance = rhsPoint.distanceInRadiansTo(nearSphereValue)
                    if (lhsDistance != rhsDistance) {
                        return@Comparator if (lhsDistance - rhsDistance > 0) 1 else -1
                    }
                }

                for (key in keys) {
                    var subKey: String? = null

                    var descending = false

                    if (key.startsWith("-")) {
                        descending = true
                        subKey = key.substring(1)
                    }
                    var lhsValue: Any?
                    var rhsValue: Any?

                    try {
                        lhsValue = getValue(lhs, subKey)
                        rhsValue = getValue(rhs, subKey)
                    } catch (e: ParseException) {
                        throw RuntimeException(e)
                    }

                    val result: Int = try {
                        compareTo(lhsValue, rhsValue)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException(
                            String.format(
                                "Unable to sort by key %s.",
                                subKey
                            ), e
                        )
                    }
                    if (result != 0) {
                        return@Comparator if (descending) -result else result
                    }
                }
                0
            })
        }

        /**
         * Makes sure that the object specified by path, relative to container, is fetched.
         */
        private fun fetchIncludeAsync(
            store: OfflineStore,
            container: Any?,
            path: String?,
            db: ParseSQLiteDatabase
        ): Task<Void?> {
            // If there's no object to include, that's fine.
            if (container == null) {
                return Task.forResult(null)
            }

            // If the container is a list or array, fetch all the sub-items.
            if (container is Collection<*>) {
                // We do the fetches in series because it makes it easier to fail on the first error.
                var task = Task.forResult<Void?>(null)
                for (item in container) {
                    task = task.onSuccessTask { task1: Task<Void?>? ->
                        fetchIncludeAsync(
                            store,
                            item,
                            path,
                            db
                        )
                    }
                }
                return task
            } else if (container is JSONArray) {
                val array = container
                // We do the fetches in series because it makes it easier to fail on the first error.
                var task = Task.forResult<Void?>(null)
                for (i in 0 until array.length()) {
                    task = task.onSuccessTask { task12: Task<Void?>? ->
                        fetchIncludeAsync(
                            store,
                            array[i],
                            path,
                            db
                        )
                    }
                }
                return task
            }

            // If we've reached the end of the path, then actually do the fetch.
            if (path == null) {
                return if (JSONObject.NULL == container) {
                    // Accept JSONObject.NULL value in included field. We swallow it silently instead of
                    // throwing an exception.
                    Task.forResult(null)
                } else if (container is ParseObject) {
                    store.fetchLocallyAsync(container, db).makeVoid()
                } else {
                    Task.forError(
                        ParseException(
                            ParseException.INVALID_NESTED_KEY,
                            "include is invalid for non-ParseObjects"
                        )
                    )
                }
            }

            // Descend into the container and try again.
            val parts = path.split("\\.").toTypedArray()
            val key = parts[0]
            val rest = if (parts.size > 1) parts[1] else null

            // Make sure the container is fetched.
            return Task.forResult<Void?>(null).continueWithTask { task: Task<Void?>? ->
                if (container is ParseObject) {
                    // Make sure this object is fetched before descending into it.
                    return@continueWithTask fetchIncludeAsync(
                        store,
                        container,
                        null,
                        db
                    ).onSuccess { task13: Task<Void?>? -> container[key] }
                } else if (container is Map<*, *>) {
                    return@continueWithTask Task.forResult(container[key])
                } else if (container is JSONObject) {
                    return@continueWithTask Task.forResult(container.opt(key))
                } else if (JSONObject.NULL == container) {
                    // Accept JSONObject.NULL value in included field. We swallow it silently instead of
                    // throwing an exception.
                    return@continueWithTask null
                } else {
                    return@continueWithTask Task.forError<Any>(IllegalStateException("include is invalid"))
                }
            }.onSuccessTask { task: Task<Any> -> fetchIncludeAsync(store, task.result, rest, db) }
        }

        /**
         * Makes sure all of the objects included by the given query get fetched.
         */
        /* package */
        @JvmStatic
        fun <T : ParseObject?> fetchIncludesAsync(
            store: OfflineStore,
            `object`: T,
            state: State<T>,
            db: ParseSQLiteDatabase
        ): Task<Void?> {
            val includes = state.includes()
            // We do the fetches in series because it makes it easier to fail on the first error.
            var task = Task.forResult<Void?>(null)
            for (include in includes) {
                task = task.onSuccessTask {
                    fetchIncludeAsync(
                        store,
                        `object`,
                        include,
                        db
                    )
                }
            }
            return task
        }
    }
}