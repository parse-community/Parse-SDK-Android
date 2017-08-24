/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import com.parse.ParseQuery.KeyConstraints;
import com.parse.ParseQuery.QueryConstraints;
import com.parse.ParseQuery.RelationConstraint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bolts.Continuation;
import bolts.Task;

/** package */ class OfflineQueryLogic {
  /**
   * A query is converted into a complex hierarchy of ConstraintMatchers that evaluate whether a
   * ParseObject matches each part of the query. This is done because some parts of the query (such
   * as $inQuery) are much more efficient if we can do some preprocessing. This makes some parts of
   * the query matching stateful.
   */
  /* package */ abstract class ConstraintMatcher<T extends ParseObject> {

    /* package */ final ParseUser user;

    public ConstraintMatcher(ParseUser user) {
      this.user = user;
    }

    /* package */ abstract Task<Boolean> matchesAsync(T object, ParseSQLiteDatabase db);
  }

  private final OfflineStore store;

  /* package */ OfflineQueryLogic(OfflineStore store) {
    this.store = store;
  }

  /**
   * Returns an Object's value for a given key, handling any special keys like objectId. Also
   * handles dot-notation for traversing into objects.
   */
  private static Object getValue(Object container, String key) throws ParseException {
    return getValue(container, key, 0);
  }

  private static Object getValue(Object container, String key, int depth) throws ParseException {
    if (key.contains(".")) {
      String[] parts = key.split("\\.", 2);
      Object value = getValue(container, parts[0], depth + 1);
      /*
       * Only Maps and JSONObjects can be dotted into for getting values, so we should reject
       * anything like ParseObjects and arrays.
       */
      if (!(value == null || value == JSONObject.NULL || value instanceof Map || value instanceof JSONObject)) {
        // Technically, they can search inside the REST representation of some nested objects.
        if (depth > 0) {
          Object restFormat = null;
          try {
            restFormat = PointerEncoder.get().encode(value);
          } catch (Exception e) {
            // Well, if we couldn't encode it, it's not searchable.
          }
          if (restFormat instanceof JSONObject) {
            return getValue(restFormat, parts[1], depth + 1);
          }
        }
        throw new ParseException(ParseException.INVALID_QUERY, String.format("Key %s is invalid.",
            key));
      }
      return getValue(value, parts[1], depth + 1);
    }

    if (container instanceof ParseObject) {
      final ParseObject object = (ParseObject) container;

      // The object needs to have been fetched already if we are going to sort by one of its fields.
      if (!object.isDataAvailable()) {
        throw new ParseException(ParseException.INVALID_NESTED_KEY, String.format("Bad key: %s",
            key));
      }

      // Handle special keys for ParseObjects.
      switch (key) {
        case "objectId":
          return object.getObjectId();
        case "createdAt":
        case "_created_at":
          return object.getCreatedAt();
        case "updatedAt":
        case "_updated_at":
          return object.getUpdatedAt();
        default:
          return object.get(key);
      }

    } else if (container instanceof JSONObject) {
      return ((JSONObject) container).opt(key);

    } else if (container instanceof Map) {
      return ((Map<?, ?>) container).get(key);

    } else if (container == JSONObject.NULL) {
      return null;

    } else if (container == null) {
      return null;

    } else {
      throw new ParseException(ParseException.INVALID_NESTED_KEY, String.format("Bad key: %s", key));
    }
  }

  /**
   * General purpose compareTo that figures out the right types to use. The arguments should be
   * atomic values to compare, such as Dates, Strings, or Numbers -- not composite objects or
   * arrays.
   */
  private static int compareTo(Object lhs, Object rhs) {
    boolean lhsIsNullOrUndefined = (lhs == JSONObject.NULL || lhs == null);
    boolean rhsIsNullOrUndefined = (rhs == JSONObject.NULL || rhs == null);

    if (lhsIsNullOrUndefined || rhsIsNullOrUndefined) {
      if (!lhsIsNullOrUndefined) {
        return 1;
      } else if (!rhsIsNullOrUndefined) {
        return -1;
      } else {
        return 0;
      }
    } else if (lhs instanceof Date && rhs instanceof Date) {
      return ((Date) lhs).compareTo((Date) rhs);
    } else if (lhs instanceof String && rhs instanceof String) {
      return ((String) lhs).compareTo((String) rhs);
    } else if (lhs instanceof Number && rhs instanceof Number) {
      return Numbers.compare((Number) lhs, (Number) rhs);
    } else {
      throw new IllegalArgumentException(
          String.format("Cannot compare %s against %s", lhs, rhs));
    }
  }

  /**
   * A decider decides whether the given value matches the given constraint.
   */
  private interface Decider {
    boolean decide(Object constraint, Object value);
  }

  /**
   * Returns true if decider returns true for any value in the given list.
   */
  private static boolean compareList(Object constraint, List<?> values, Decider decider) {
    for (Object value : values) {
      if (decider.decide(constraint, value)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if decider returns true for any value in the given list.
   */
  private static boolean compareArray(Object constraint, JSONArray values, Decider decider) {
    for (int i = 0; i < values.length(); ++i) {
      try {
        if (decider.decide(constraint, values.get(i))) {
          return true;
        }
      } catch (JSONException e) {
        // This can literally never happen.
        throw new RuntimeException(e);
      }
    }
    return false;
  }

  /**
   *
   * Returns true if the decider returns true for the given value and the given constraint. This
   * method handles Mongo's logic where an item can match either an item itself, or any item within
   * the item, if the item is an array.
   */
  private static boolean compare(Object constraint, Object value, Decider decider) {
    if (value instanceof List) {
      return compareList(constraint, (List<?>) value, decider);
    } else if (value instanceof JSONArray) {
      return compareArray(constraint, (JSONArray) value, decider);
    } else {
      return decider.decide(constraint, value);
    }
  }

  /**
   * Implements simple equality constraints. This emulates Mongo's behavior where "equals" can mean
   * array containment.
   */
  private static boolean matchesEqualConstraint(Object constraint, Object value) {
    if (constraint == null || value == null) {
      return constraint == value;
    }

    if (constraint instanceof Number && value instanceof Number) {
      return compareTo(constraint, value) == 0;
    }

    if (constraint instanceof ParseGeoPoint && value instanceof ParseGeoPoint) {
      ParseGeoPoint lhs = (ParseGeoPoint) constraint;
      ParseGeoPoint rhs = (ParseGeoPoint) value;
      return lhs.getLatitude() == rhs.getLatitude()
          && lhs.getLongitude() == rhs.getLongitude();
    }

    if (constraint instanceof ParsePolygon && value instanceof ParsePolygon) {
      ParsePolygon lhs = (ParsePolygon) constraint;
      ParsePolygon rhs = (ParsePolygon) value;
      return lhs.equals(rhs);
    }

    return compare(constraint, value, new Decider() {
      @Override
      public boolean decide(Object constraint, Object value) {
        return constraint.equals(value);
      }
    });
  }

  /**
   * Matches $ne constraints.
   */
  private static boolean matchesNotEqualConstraint(Object constraint, Object value) {
    return !matchesEqualConstraint(constraint, value);
  }

  /**
   * Matches $lt constraints.
   */
  private static boolean matchesLessThanConstraint(Object constraint, Object value) {
    return compare(constraint, value, new Decider() {
      @Override
      public boolean decide(Object constraint, Object value) {
        if (value == null || value == JSONObject.NULL) {
          return false;
        }
        return compareTo(constraint, value) > 0;
      }
    });
  }

  /**
   * Matches $lte constraints.
   */
  private static boolean matchesLessThanOrEqualToConstraint(Object constraint, Object value) {
    return compare(constraint, value, new Decider() {
      @Override
      public boolean decide(Object constraint, Object value) {
        if (value == null || value == JSONObject.NULL) {
          return false;
        }
        return compareTo(constraint, value) >= 0;
      }
    });
  }

  /**
   * Matches $gt constraints.
   */
  private static boolean matchesGreaterThanConstraint(Object constraint, Object value) {
    return compare(constraint, value, new Decider() {
      @Override
      public boolean decide(Object constraint, Object value) {
        if (value == null || value == JSONObject.NULL) {
          return false;
        }
        return compareTo(constraint, value) < 0;
      }
    });
  }

  /**
   * Matches $gte constraints.
   */
  private static boolean matchesGreaterThanOrEqualToConstraint(Object constraint, Object value) {
    return compare(constraint, value, new Decider() {
      @Override
      public boolean decide(Object constraint, Object value) {
        if (value == null || value == JSONObject.NULL) {
          return false;
        }
        return compareTo(constraint, value) <= 0;
      }
    });
  }

  /**
   * Matches $in constraints.
   * $in returns true if the intersection of value and constraint is not an empty set.
   */
  private static boolean matchesInConstraint(Object constraint, Object value) {
    if (constraint instanceof Collection) {
      for (Object requiredItem : (Collection<?>)constraint) {
        if (matchesEqualConstraint(requiredItem, value)) {
          return true;
        }
      }
      return false;
    }
    throw new IllegalArgumentException("Constraint type not supported for $in queries.");
  }

  /**
   * Matches $nin constraints.
   */
  private static boolean matchesNotInConstraint(Object constraint, Object value) {
    return !matchesInConstraint(constraint, value);
  }

  /**
   * Matches $all constraints.
   */
  private static boolean matchesAllConstraint(Object constraint, Object value) {
    if (value == null || value == JSONObject.NULL) {
      return false;
    }

    if (!(value instanceof Collection)) {
      throw new IllegalArgumentException("Value type not supported for $all queries.");
    }

    if (constraint instanceof Collection) {
      for (Object requiredItem : (Collection<?>) constraint) {
        if (!matchesEqualConstraint(requiredItem, value)) {
          return false;
        }
      }
      return true;
    }
    throw new IllegalArgumentException("Constraint type not supported for $all queries.");
  }

  /**
   * Matches $regex constraints.
   */
  private static boolean matchesRegexConstraint(Object constraint, Object value, String options)
      throws ParseException {
    if (value == null || value == JSONObject.NULL) {
      return false;
    }

    if (options == null) {
      options = "";
    }

    if (!options.matches("^[imxs]*$")) {
      throw new ParseException(ParseException.INVALID_QUERY, String.format(
          "Invalid regex options: %s", options));
    }

    int flags = 0;
    if (options.contains("i")) {
      flags = flags | Pattern.CASE_INSENSITIVE;
    }
    if (options.contains("m")) {
      flags = flags | Pattern.MULTILINE;
    }
    if (options.contains("x")) {
      flags = flags | Pattern.COMMENTS;
    }
    if (options.contains("s")) {
      flags = flags | Pattern.DOTALL;
    }

    String regex = (String) constraint;
    Pattern pattern = Pattern.compile(regex, flags);
    Matcher matcher = pattern.matcher((String) value);
    return matcher.find();
  }

  /**
   * Matches $exists constraints.
   */
  private static boolean matchesExistsConstraint(Object constraint, Object value) {
    /*
     * In the Android SDK, null means "undefined", and JSONObject.NULL means "null".
     */
    if (constraint != null && (Boolean) constraint) {
      return value != null && value != JSONObject.NULL;
    } else {
      return value == null || value == JSONObject.NULL;
    }
  }

  /**
   * Matches $nearSphere constraints.
   */
  private static boolean matchesNearSphereConstraint(Object constraint, Object value,
      Double maxDistance) {
    if (value == null || value == JSONObject.NULL) {
      return false;
    }
    if (maxDistance == null) {
      return true;
    }
    ParseGeoPoint point1 = (ParseGeoPoint) constraint;
    ParseGeoPoint point2 = (ParseGeoPoint) value;
    return point1.distanceInRadiansTo(point2) <= maxDistance;
  }

  /**
   * Matches $within constraints.
   */
  private static boolean matchesWithinConstraint(Object constraint, Object value)
      throws ParseException {
    if (value == null || value == JSONObject.NULL) {
      return false;
    }

    @SuppressWarnings("unchecked")
    HashMap<String, ArrayList<ParseGeoPoint>> constraintMap =
        (HashMap<String, ArrayList<ParseGeoPoint>>) constraint;
    ArrayList<ParseGeoPoint> box = constraintMap.get("$box");
    ParseGeoPoint southwest = box.get(0);
    ParseGeoPoint northeast = box.get(1);
    ParseGeoPoint target = (ParseGeoPoint) value;

    if (northeast.getLongitude() < southwest.getLongitude()) {
      throw new ParseException(ParseException.INVALID_QUERY,
          "whereWithinGeoBox queries cannot cross the International Date Line.");
    }
    if (northeast.getLatitude() < southwest.getLatitude()) {
      throw new ParseException(ParseException.INVALID_QUERY,
          "The southwest corner of a geo box must be south of the northeast corner.");
    }
    if (northeast.getLongitude() - southwest.getLongitude() > 180) {
      throw new ParseException(ParseException.INVALID_QUERY,
          "Geo box queries larger than 180 degrees in longitude are not supported. "
              + "Please check point order.");
    }

    return (target.getLatitude() >= southwest.getLatitude()
        && target.getLatitude() <= northeast.getLatitude()
        && target.getLongitude() >= southwest.getLongitude()
        && target.getLongitude() <= northeast.getLongitude());
  }

  /**
   * Matches $geoIntersects constraints.
   */
  private static boolean matchesGeoIntersectsConstraint(Object constraint, Object value)
      throws ParseException {
    if (value == null || value == JSONObject.NULL) {
      return false;
    }

    @SuppressWarnings("unchecked")
    HashMap<String, ParseGeoPoint> constraintMap =
        (HashMap<String, ParseGeoPoint>) constraint;
    ParseGeoPoint point = constraintMap.get("$point");
    ParsePolygon target = (ParsePolygon) value;
    return target.containsPoint(point);
  }

  /**
   * Matches $geoWithin constraints.
   */
  private static boolean matchesGeoWithinConstraint(Object constraint, Object value)
      throws ParseException {
    if (value == null || value == JSONObject.NULL) {
      return false;
    }

    @SuppressWarnings("unchecked")
    HashMap<String, List<ParseGeoPoint>> constraintMap =
      (HashMap<String, List<ParseGeoPoint>>) constraint;
    List<ParseGeoPoint> points = constraintMap.get("$polygon");
    ParsePolygon polygon = new ParsePolygon(points);
    ParseGeoPoint point = (ParseGeoPoint) value;
    return polygon.containsPoint(point);
  }
  /**
   * Returns true iff the given value matches the given operator and constraint.
   *
   * @throws UnsupportedOperationException
   *           if the operator is not one this function can handle.
   */
  private static boolean matchesStatelessConstraint(String operator, Object constraint,
      Object value, KeyConstraints allKeyConstraints) throws ParseException {
    switch (operator) {
      case "$ne":
        return matchesNotEqualConstraint(constraint, value);

      case "$lt":
        return matchesLessThanConstraint(constraint, value);

      case "$lte":
        return matchesLessThanOrEqualToConstraint(constraint, value);

      case "$gt":
        return matchesGreaterThanConstraint(constraint, value);

      case "$gte":
        return matchesGreaterThanOrEqualToConstraint(constraint, value);

      case "$in":
        return matchesInConstraint(constraint, value);

      case "$nin":
        return matchesNotInConstraint(constraint, value);

      case "$all":
        return matchesAllConstraint(constraint, value);

      case "$regex":
        String regexOptions = (String) allKeyConstraints.get("$options");
        return matchesRegexConstraint(constraint, value, regexOptions);

      case "$options":
        // No need to do anything. This is handled by $regex.
        return true;

      case "$exists":
        return matchesExistsConstraint(constraint, value);

      case "$nearSphere":
        Double maxDistance = (Double) allKeyConstraints.get("$maxDistance");
        return matchesNearSphereConstraint(constraint, value, maxDistance);

      case "$maxDistance":
        // No need to do anything. This is handled by $nearSphere.
        return true;

      case "$within":
        return matchesWithinConstraint(constraint, value);

      case "$geoWithin":
        return matchesGeoWithinConstraint(constraint, value);

      case "$geoIntersects":
        return matchesGeoIntersectsConstraint(constraint, value);

      default:
        throw new UnsupportedOperationException(String.format(
            "The offline store does not yet support the %s operator.", operator));
    }
  }

  private abstract class SubQueryMatcher<T extends ParseObject> extends ConstraintMatcher<T> {
    private final ParseQuery.State<T> subQuery;
    private Task<List<T>> subQueryResults = null;

    public SubQueryMatcher(ParseUser user, ParseQuery.State<T> subQuery) {
      super(user);
      this.subQuery = subQuery;
    }

    @Override
    public Task<Boolean> matchesAsync(final T object, ParseSQLiteDatabase db) {
      /*
       * As an optimization, we do this lazily. Then we may not have to do it at all, if this part
       * of the query gets short-circuited.
       */
      if (subQueryResults == null) {
        //TODO (grantland): We need to pass through the original pin we were limiting the parent
        // query on.
        subQueryResults = store.findAsync(subQuery, user, null, db);
      }
      return subQueryResults.onSuccess(new Continuation<List<T>, Boolean>() {
        @Override
        public Boolean then(Task<List<T>> task) throws ParseException {
          return matches(object, task.getResult());
        }
      });
    }

    protected abstract boolean matches(T object, List<T> results) throws ParseException;
  }

  /**
   * Creates a matcher that handles $inQuery constraints.
   */
  private <T extends ParseObject> ConstraintMatcher<T> createInQueryMatcher(ParseUser user,
      Object constraint, final String key) {
    // TODO(grantland): Convert builder to state t6941155
    @SuppressWarnings("unchecked")
    ParseQuery.State<T> query = ((ParseQuery.State.Builder<T>) constraint).build();
    return new SubQueryMatcher<T>(user, query) {
      @Override
      protected boolean matches(T object, List<T> results) throws ParseException {
        Object value = getValue(object, key);
        return matchesInConstraint(results, value);
      }
    };
  }

  /**
   * Creates a matcher that handles $notInQuery constraints.
   */
  private <T extends ParseObject> ConstraintMatcher<T> createNotInQueryMatcher(ParseUser user,
      Object constraint, final String key) {
    final ConstraintMatcher<T> inQueryMatcher = createInQueryMatcher(user, constraint, key);
    return new ConstraintMatcher<T>(user) {
      @Override
      public Task<Boolean> matchesAsync(T object, ParseSQLiteDatabase db) {
        return inQueryMatcher.matchesAsync(object, db).onSuccess(new Continuation<Boolean, Boolean>() {
          @Override
          public Boolean then(Task<Boolean> task) throws Exception {
            return !task.getResult();
          }
        });
      }
    };
  }

  /**
   * Creates a matcher that handles $select constraints.
   */
  private <T extends ParseObject> ConstraintMatcher<T> createSelectMatcher(ParseUser user,
      Object constraint, final String key) {
    Map<?, ?> constraintMap = (Map<?, ?>) constraint;
    // TODO(grantland): Convert builder to state t6941155
    @SuppressWarnings("unchecked")
    ParseQuery.State<T> query = ((ParseQuery.State.Builder<T>) constraintMap.get("query")).build();
    final String resultKey = (String) constraintMap.get("key");
    return new SubQueryMatcher<T>(user, query) {
      @Override
      protected boolean matches(T object, List<T> results) throws ParseException {
        Object value = getValue(object, key);
        for (T result : results) {
          Object resultValue = getValue(result, resultKey);
          if (matchesEqualConstraint(value, resultValue)) {
            return true;
          }
        }
        return false;
      }
    };
  }

  /**
   * Creates a matcher that handles $dontSelect constraints.
   */
  private <T extends ParseObject> ConstraintMatcher<T> createDontSelectMatcher(ParseUser user,
      Object constraint, final String key) {
    final ConstraintMatcher<T> selectMatcher = createSelectMatcher(user, constraint, key);
    return new ConstraintMatcher<T>(user) {
      @Override
      public Task<Boolean> matchesAsync(T object, ParseSQLiteDatabase db) {
        return selectMatcher.matchesAsync(object, db).onSuccess(new Continuation<Boolean, Boolean>() {
          @Override
          public Boolean then(Task<Boolean> task) throws Exception {
            return !task.getResult();
          }
        });
      }
    };
  }

  /*
   * Creates a matcher for a particular constraint operator.
   */
  private <T extends ParseObject> ConstraintMatcher<T> createMatcher(ParseUser user,
      final String operator, final Object constraint, final String key,
      final KeyConstraints allKeyConstraints) {
    switch (operator) {
      case "$inQuery":
        return createInQueryMatcher(user, constraint, key);

      case "$notInQuery":
        return createNotInQueryMatcher(user, constraint, key);

      case "$select":
        return createSelectMatcher(user, constraint, key);

      case "$dontSelect":
        return createDontSelectMatcher(user, constraint, key);

      default:
      /*
       * All of the other operators we know about are stateless, so return a simple matcher.
       */
        return new ConstraintMatcher<T>(user) {
          @Override
          public Task<Boolean> matchesAsync(T object, ParseSQLiteDatabase db) {
            try {
              Object value = getValue(object, key);
              return Task.forResult(matchesStatelessConstraint(operator, constraint, value,
                  allKeyConstraints));
            } catch (ParseException e) {
              return Task.forError(e);
            }
          }
        };
    }
  }

  /**
   * Handles $or queries.
   */
  private <T extends ParseObject> ConstraintMatcher<T> createOrMatcher(ParseUser user,
      ArrayList<QueryConstraints> queries) {
    // Make a list of all the matchers to OR together.
    final ArrayList<ConstraintMatcher<T>> matchers = new ArrayList<>();
    for (QueryConstraints constraints : queries) {
      ConstraintMatcher<T> matcher = createMatcher(user, constraints);
      matchers.add(matcher);
    }
    /*
     * Now OR together the constraints for each query.
     */
    return new ConstraintMatcher<T>(user) {
      @Override
      public Task<Boolean> matchesAsync(final T object, final ParseSQLiteDatabase db) {
        Task<Boolean> task = Task.forResult(false);
        for (final ConstraintMatcher<T> matcher : matchers) {
          task = task.onSuccessTask(new Continuation<Boolean, Task<Boolean>>() {
            @Override
            public Task<Boolean> then(Task<Boolean> task) throws Exception {
              if (task.getResult()) {
                return task;
              }
              return matcher.matchesAsync(object, db);
            }
          });
        }
        return task;
      }
    };

  }

  /**
   * Returns a ConstraintMatcher that return true iff the object matches QueryConstraints. This
   * takes in a SQLiteDatabase connection because SQLite is finicky about nesting connections, so we
   * want to reuse them whenever possible.
   */
  private <T extends ParseObject> ConstraintMatcher<T> createMatcher(ParseUser user,
      QueryConstraints queryConstraints) {
    // Make a list of all the matchers to AND together.
    final ArrayList<ConstraintMatcher<T>> matchers = new ArrayList<>();
    for (final String key : queryConstraints.keySet()) {
      final Object queryConstraintValue = queryConstraints.get(key);

      if (key.equals("$or")) {
        /*
         * A set of queries to be OR-ed together.
         */
        @SuppressWarnings("unchecked")
        ConstraintMatcher<T> matcher =
            createOrMatcher(user, (ArrayList<QueryConstraints>) queryConstraintValue);
        matchers.add(matcher);

      } else if (queryConstraintValue instanceof KeyConstraints) {
        /*
         * It's a set of constraints that should be AND-ed together.
         */
        KeyConstraints keyConstraints = (KeyConstraints) queryConstraintValue;
        for (String operator : keyConstraints.keySet()) {
          final Object keyConstraintValue = keyConstraints.get(operator);
          ConstraintMatcher<T> matcher =
              createMatcher(user, operator, keyConstraintValue, key, keyConstraints);
          matchers.add(matcher);
        }

      } else if (queryConstraintValue instanceof RelationConstraint) {
        /*
         * It's a $relatedTo constraint.
         */
        final RelationConstraint relation = (RelationConstraint) queryConstraintValue;
        matchers.add(new ConstraintMatcher<T>(user) {
          @Override
          public Task<Boolean> matchesAsync(T object, ParseSQLiteDatabase db) {
            return Task.forResult(relation.getRelation().hasKnownObject(object));
          }
        });

      } else {
        /*
         * It's not a set of constraints, so it's just a value to compare against.
         */
        matchers.add(new ConstraintMatcher<T>(user) {
          @Override
          public Task<Boolean> matchesAsync(T object, ParseSQLiteDatabase db) {
            Object objectValue;
            try {
              objectValue = getValue(object, key);
            } catch (ParseException e) {
              return Task.forError(e);
            }
            return Task.forResult(matchesEqualConstraint(queryConstraintValue, objectValue));
          }
        });
      }
    }

    /*
     * Now AND together the constraints for each key.
     */
    return new ConstraintMatcher<T>(user) {
      @Override
      public Task<Boolean> matchesAsync(final T object, final ParseSQLiteDatabase db) {
        Task<Boolean> task = Task.forResult(true);
        for (final ConstraintMatcher<T> matcher : matchers) {
          task = task.onSuccessTask(new Continuation<Boolean, Task<Boolean>>() {
            @Override
            public Task<Boolean> then(Task<Boolean> task) throws Exception {
              if (!task.getResult()) {
                return task;
              }
              return matcher.matchesAsync(object, db);
            }
          });
        }
        return task;
      }
    };
  }

  /**
   * Returns true iff the object is visible based on its read ACL and the given user objectId.
   */
  /* package */ static <T extends ParseObject> boolean hasReadAccess(ParseUser user, T object) {
    if (user == object) {
      return true;
    }

    ParseACL acl = object.getACL();
    if (acl == null) {
      return true;
    }
    if (acl.getPublicReadAccess()) {
      return true;
    }
    if (user != null && acl.getReadAccess(user)) {
      return true;
    }
    // TODO: Implement roles.
    return false;
  }

  /**
   * Returns true iff the object is visible based on its read ACL and the given user objectId.
   */
  /* package */ static <T extends ParseObject> boolean hasWriteAccess(ParseUser user, T object) {
    if (user == object) {
      return true;
    }

    ParseACL acl = object.getACL();
    if (acl == null) {
      return true;
    }
    if (acl.getPublicWriteAccess()) {
      return true;
    }
    if (user != null && acl.getWriteAccess(user)) {
      return true;
    }
    // TODO: Implement roles.
    return false;
  }

  /**
   * Returns a ConstraintMatcher that return true iff the object matches the given query's
   * constraints. This takes in a SQLiteDatabase connection because SQLite is finicky about nesting
   * connections, so we want to reuse them whenever possible.
   *
   * @param state The query.
   * @param user The user we are testing ACL access for.
   * @param <T> Subclass of ParseObject.
   * @return A new instance of ConstraintMatcher.
   */
  /* package */ <T extends ParseObject> ConstraintMatcher<T> createMatcher(
      ParseQuery.State<T> state, final ParseUser user) {
    final boolean ignoreACLs = state.ignoreACLs();
    final ConstraintMatcher<T> constraintMatcher = createMatcher(user, state.constraints());

    return new ConstraintMatcher<T>(user) {
      @Override
      public Task<Boolean> matchesAsync(T object, ParseSQLiteDatabase db) {
        if (!ignoreACLs && !hasReadAccess(user, object)) {
          return Task.forResult(false);
        }
        return constraintMatcher.matchesAsync(object, db);
      }
    };
  }

  /**
   * Sorts the given array based on the parameters of the given query.
   */
  /* package */ static <T extends ParseObject> void sort(List<T> results, ParseQuery.State<T> state)
      throws ParseException {
    final List<String> keys = state.order();
    // Do some error checking just for maximum compatibility with the server.
    for (String key : state.order()) {
      if (!key.matches("^-?[A-Za-z][A-Za-z0-9_]*$")) {
        if (!"_created_at".equals(key) && !"_updated_at".equals(key)) {
          throw new ParseException(ParseException.INVALID_KEY_NAME, String.format(
              "Invalid key name: \"%s\".", key));
        }
      }
    }

    // See if there's a $nearSphere constraint that will override the other sort parameters.
    String mutableNearSphereKey = null;
    ParseGeoPoint mutableNearSphereValue = null;
    for (String queryKey : state.constraints().keySet()) {
      Object queryKeyConstraints = state.constraints().get(queryKey);
      if (queryKeyConstraints instanceof KeyConstraints) {
        KeyConstraints keyConstraints = (KeyConstraints) queryKeyConstraints;
        if (keyConstraints.containsKey("$nearSphere")) {
          mutableNearSphereKey = queryKey;
          mutableNearSphereValue = (ParseGeoPoint) keyConstraints.get("$nearSphere");
        }
      }
    }
    final String nearSphereKey = mutableNearSphereKey;
    final ParseGeoPoint nearSphereValue = mutableNearSphereValue;

    // If there's nothing to sort based on, then don't do anything.
    if (keys.size() == 0 && mutableNearSphereKey == null) {
      return;
    }

    /*
     * TODO(klimt): Test whether we allow dotting into objects for sorting.
     */

    Collections.sort(results, new Comparator<T>() {
      @Override
      public int compare(T lhs, T rhs) {
        if (nearSphereKey != null) {
          ParseGeoPoint lhsPoint;
          ParseGeoPoint rhsPoint;
          try {
            lhsPoint = (ParseGeoPoint) getValue(lhs, nearSphereKey);
            rhsPoint = (ParseGeoPoint) getValue(rhs, nearSphereKey);
          } catch (ParseException e) {
            throw new RuntimeException(e);
          }

          // GeoPoints can't be null if there's a $nearSphere.
          double lhsDistance = lhsPoint.distanceInRadiansTo(nearSphereValue);
          double rhsDistance = rhsPoint.distanceInRadiansTo(nearSphereValue);
          if (lhsDistance != rhsDistance) {
            return (lhsDistance - rhsDistance > 0) ? 1 : -1;
          }
        }

        for (String key : keys) {
          boolean descending = false;
          if (key.startsWith("-")) {
            descending = true;
            key = key.substring(1);
          }

          Object lhsValue;
          Object rhsValue;
          try {
            lhsValue = getValue(lhs, key);
            rhsValue = getValue(rhs, key);
          } catch (ParseException e) {
            throw new RuntimeException(e);
          }

          int result;
          try {
            result = compareTo(lhsValue, rhsValue);
          } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Unable to sort by key %s.", key), e);
          }
          if (result != 0) {
            return descending ? -result : result;
          }
        }
        return 0;
      }
    });
  }

  /**
   * Makes sure that the object specified by path, relative to container, is fetched.
   */
  private static Task<Void> fetchIncludeAsync(
      final OfflineStore store,
      final Object container,
      final String path,
      final ParseSQLiteDatabase db)
      throws ParseException {
    // If there's no object to include, that's fine.
    if (container == null) {
      return Task.forResult(null);
    }

    // If the container is a list or array, fetch all the sub-items.
    if (container instanceof Collection) {
      Collection<?> collection = (Collection<?>) container;
      // We do the fetches in series because it makes it easier to fail on the first error.
      Task<Void> task = Task.forResult(null);
      for (final Object item : collection) {
        task = task.onSuccessTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return fetchIncludeAsync(store, item, path, db);
          }
        });
      }
      return task;
    } else if (container instanceof JSONArray) {
      final JSONArray array = (JSONArray) container;
      // We do the fetches in series because it makes it easier to fail on the first error.
      Task<Void> task = Task.forResult(null);
      for (int i = 0; i < array.length(); ++i) {
        final int index = i;
        task = task.onSuccessTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return fetchIncludeAsync(store, array.get(index), path, db);
          }
        });
      }
      return task;
    }

    // If we've reached the end of the path, then actually do the fetch.
    if (path == null) {
      if (JSONObject.NULL.equals(container)) {
        // Accept JSONObject.NULL value in included field. We swallow it silently instead of
        // throwing an exception.
        return Task.forResult(null);
      } else if (container instanceof ParseObject) {
        ParseObject object = (ParseObject) container;
        return store.fetchLocallyAsync(object, db).makeVoid();
      } else {
        return Task.forError(new ParseException(
            ParseException.INVALID_NESTED_KEY, "include is invalid for non-ParseObjects"));
      }
    }

    // Descend into the container and try again.

    String[] parts = path.split("\\.", 2);
    final String key = parts[0];
    final String rest = (parts.length > 1 ? parts[1] : null);

    // Make sure the container is fetched.
    return Task.<Void> forResult(null).continueWithTask(new Continuation<Void, Task<Object>>() {
      @Override
      public Task<Object> then(Task<Void> task) throws Exception {
        if (container instanceof ParseObject) {
          // Make sure this object is fetched before descending into it.
          return fetchIncludeAsync(store, container, null, db).onSuccess(new Continuation<Void, Object>() {
            @Override
            public Object then(Task<Void> task) throws Exception {
              return ((ParseObject) container).get(key);
            }
          });
        } else if (container instanceof Map) {
          return Task.forResult(((Map) container).get(key));
        } else if (container instanceof JSONObject) {
          return Task.forResult(((JSONObject) container).opt(key));
        } else if (JSONObject.NULL.equals(container)) {
          // Accept JSONObject.NULL value in included field. We swallow it silently instead of
          // throwing an exception.
          return null;
        } else {
          return Task.forError(new IllegalStateException("include is invalid"));
        }
      }
    }).onSuccessTask(new Continuation<Object, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Object> task) throws Exception {
        return fetchIncludeAsync(store, task.getResult(), rest, db);
      }
    });
  }

  /**
   * Makes sure all of the objects included by the given query get fetched.
   */
  /* package */ static <T extends ParseObject> Task<Void> fetchIncludesAsync(
      final OfflineStore store,
      final T object,
      ParseQuery.State<T> state,
      final ParseSQLiteDatabase db) {
    Set<String> includes = state.includes();
    // We do the fetches in series because it makes it easier to fail on the first error.
    Task<Void> task = Task.forResult(null);
    for (final String include : includes) {
      task = task.onSuccessTask(new Continuation<Void, Task<Void>>() {
        @Override
        public Task<Void> then(Task<Void> task) throws Exception {
          return fetchIncludeAsync(store, object, include, db);
        }
      });
    }
    return task;
  }
}
