/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import bolts.Continuation;
import bolts.Task;
import bolts.TaskCompletionSource;

/**
 * The {@code ParseQuery} class defines a query that is used to fetch {@link ParseObject}s. The most
 * common use case is finding all objects that match a query through the {@link #findInBackground()}
 * method, using a {@link FindCallback}. For example, this sample code fetches all objects of class
 * {@code "MyClass"}. It calls a different function depending on whether the fetch succeeded or not.
 * <p/>
 * <pre>
 * ParseQuery&lt;ParseObject&gt; query = ParseQuery.getQuery("MyClass");
 * query.findInBackground(new FindCallback&lt;ParseObject&gt;() {
 *     public void done(List&lt;ParseObject&gt; objects, ParseException e) {
 *         if (e == null) {
 *             objectsWereRetrievedSuccessfully(objects);
 *         } else {
 *             objectRetrievalFailed();
 *         }
 *     }
 * }
 * </pre>
 * <p/>
 * A {@code ParseQuery} can also be used to retrieve a single object whose id is known, through the
 * {@link #getInBackground(String)} method, using a {@link GetCallback}. For example, this
 * sample code fetches an object of class {@code "MyClass"} and id {@code myId}. It calls
 * a different function depending on whether the fetch succeeded or not.
 * <p/>
 * <pre>
 * ParseQuery&lt;ParseObject&gt; query = ParseQuery.getQuery("MyClass");
 * query.getInBackground(myId, new GetCallback&lt;ParseObject&gt;() {
 *     public void done(ParseObject object, ParseException e) {
 *         if (e == null) {
 *             objectWasRetrievedSuccessfully(object);
 *         } else {
 *             objectRetrievalFailed();
 *         }
 *     }
 * }
 * </pre>
 * <p/>
 * A {@code ParseQuery} can also be used to count the number of objects that match the query without
 * retrieving all of those objects. For example, this sample code counts the number of objects of
 * the class {@code "MyClass"}.
 * <p/>
 * <pre>
 * ParseQuery&lt;ParseObject&gt; query = ParseQuery.getQuery("MyClass");
 * query.countInBackground(new CountCallback() {
 *     public void done(int count, ParseException e) {
 *         if (e == null) {
 *             objectsWereCounted(count);
 *         } else {
 *             objectCountFailed();
 *         }
 *     }
 * }
 * </pre>
 * <p/>
 * Using the callback methods is usually preferred because the network operation will not block the
 * calling thread. However, in some cases it may be easier to use the {@link #find()},
 * {@link #get(String)} or {@link #count()} calls, which do block the calling thread. For example,
 * if your application has already spawned a background task to perform work, that background task
 * could use the blocking calls and avoid the code complexity of callbacks.
 */
public class ParseQuery<T extends ParseObject> {

  private static ParseQueryController getQueryController() {
    return ParseCorePlugins.getInstance().getQueryController();
  }

  private static ParseObjectSubclassingController getSubclassingController() {
    return ParseCorePlugins.getInstance().getSubclassingController();
  }

  /**
   * Constraints for a {@code ParseQuery}'s where clause. A map of field names to constraints. The
   * values can either be actual values to compare with for equality, or instances of
   * {@link KeyConstraints}.
   */
  @SuppressWarnings("serial")
  /* package */ static class QueryConstraints extends HashMap<String, Object> {

    public QueryConstraints() {
      super();
    }

    public QueryConstraints(Map<? extends String, ?> map) {
      super(map);
    }
  }

  /**
   * Constraints for a particular field in a query. If this is used, it's a may where the keys are
   * special operators, such as $greaterThan or $nin. The values are the actual values to compare
   * against.
   */
  @SuppressWarnings("serial")
  /* package */ static class KeyConstraints extends HashMap<String, Object> {
  }

  /**
   * Constraint for a $relatedTo query.
   */
  /* package */ static class RelationConstraint {
    private String key;
    private ParseObject object;

    public RelationConstraint(String key, ParseObject object) {
      if (key == null || object == null) {
        throw new IllegalArgumentException("Arguments must not be null.");
      }
      this.key = key;
      this.object = object;
    }

    public String getKey() {
      return key;
    }

    public ParseObject getObject() {
      return object;
    }

    public ParseRelation<ParseObject> getRelation() {
      return object.getRelation(key);
    }

    /**
     * Encodes the constraint in a format appropriate for including in the query.
     */
    public JSONObject encode(ParseEncoder objectEncoder) {
      JSONObject json = new JSONObject();
      try {
        json.put("key", key);
        json.put("object", objectEncoder.encodeRelatedObject(object));
      } catch (JSONException e) {
        // This can never happen.
        throw new RuntimeException(e);
      }
      return json;
    }
  }

  /**
   * Constructs a query that is the {@code or} of the given queries.
   *
   * @param queries
   *          The list of {@code ParseQuery}s to 'or' together
   * @return A {@code ParseQuery} that is the 'or' of the passed in queries
   */
  public static <T extends ParseObject> ParseQuery<T> or(List<ParseQuery<T>> queries) {
    if (queries.isEmpty()) {
      throw new IllegalArgumentException("Can't take an or of an empty list of queries");
    }

    List<State.Builder<T>> builders = new ArrayList<>();
    for (ParseQuery<T> query : queries) {
      builders.add(query.getBuilder());
    }
    return new ParseQuery<>(State.Builder.or(builders));
  }

  /**
   * Creates a new query for the given {@link ParseObject} subclass type. A default query with no
   * further parameters will retrieve all {@link ParseObject}s of the provided class.
   *
   * @param subclass
   *          The {@link ParseObject} subclass type to retrieve.
   * @return A new {@code ParseQuery}.
   */
  public static <T extends ParseObject> ParseQuery<T> getQuery(Class<T> subclass) {
    return new ParseQuery<>(subclass);
  }

  /**
   * Creates a new query for the given class name. A default query with no further parameters will
   * retrieve all {@link ParseObject}s of the provided class name.
   *
   * @param className
   *          The name of the class to retrieve {@link ParseObject}s for.
   * @return A new {@code ParseQuery}.
   */
  public static <T extends ParseObject> ParseQuery<T> getQuery(String className) {
    return new ParseQuery<>(className);
  }

  /**
   * Constructs a query for {@link ParseUser}s.
   *
   * @deprecated Please use {@link ParseUser#getQuery()} instead.
   */
  @Deprecated
  public static ParseQuery<ParseUser> getUserQuery() {
    return ParseUser.getQuery();
  }

  /**
   * {@code CachePolicy} specifies different caching policies that could be used with
   * {@link ParseQuery}.
   * <p/>
   * This lets you show data when the user's device is offline, or when the app has just started and
   * network requests have not yet had time to complete. Parse takes care of automatically flushing
   * the cache when it takes up too much space.
   * <p/>
   * <strong>Note:</strong> Cache policy can only be set when Local Datastore is not enabled.
   *
   * @see com.parse.ParseQuery
   */
  public enum CachePolicy {
    /**
     * The query does not load from the cache or save results to the cache.
     * <p/>
     * This is the default cache policy.
     */
    IGNORE_CACHE,

    /**
     * The query only loads from the cache, ignoring the network.
     * <p/>
     * If there are no cached results, this causes a {@link ParseException#CACHE_MISS}.
     */
    CACHE_ONLY,

    /**
     * The query does not load from the cache, but it will save results to the cache.
     */
    NETWORK_ONLY,

    /**
     * The query first tries to load from the cache, but if that fails, it loads results from the
     * network.
     * <p/>
     * If there are no cached results, this causes a {@link ParseException#CACHE_MISS}.
     */
    CACHE_ELSE_NETWORK,

    /**
     * The query first tries to load from the network, but if that fails, it loads results from the
     * cache.
     * <p/>
     * If there are no cached results, this causes a {@link ParseException#CACHE_MISS}.
     */
    NETWORK_ELSE_CACHE,

    /**
     * The query first loads from the cache, then loads from the network.
     * The callback will be called twice - first with the cached results, then with the network
     * results. Since it returns two results at different times, this cache policy cannot be used
     * with synchronous or task methods.
     */
    // TODO(grantland): Remove this and come up with a different solution, since it breaks our
    // "callbacks get called at most once" paradigm. (v2)
    CACHE_THEN_NETWORK
  }

  private static void throwIfLDSEnabled() {
    throwIfLDSEnabled(false);
  }

  private static void throwIfLDSDisabled() {
    throwIfLDSEnabled(true);
  }

  private static void throwIfLDSEnabled(boolean enabled) {
    boolean ldsEnabled = Parse.isLocalDatastoreEnabled();
    if (enabled && !ldsEnabled) {
      throw new IllegalStateException("Method requires Local Datastore. " +
          "Please refer to `Parse#enableLocalDatastore(Context)`.");
    }
    if (!enabled && ldsEnabled) {
      throw new IllegalStateException("Unsupported method when Local Datastore is enabled.");
    }
  }

  /* package */ static class State<T extends ParseObject> {

    /* package */ static class Builder<T extends ParseObject> {

      // TODO(grantland): Convert mutable parameter to immutable t6941155
      public static <T extends ParseObject> Builder<T> or(List<Builder<T>> builders) {
        if (builders.isEmpty()) {
          throw new IllegalArgumentException("Can't take an or of an empty list of queries");
        }

        String className = null;
        List<QueryConstraints> constraints = new ArrayList<>();
        for (Builder<T> builder : builders) {
          if (className != null && !builder.className.equals(className)) {
            throw new IllegalArgumentException(
                "All of the queries in an or query must be on the same class ");
          }
          if (builder.limit >= 0) {
            throw new IllegalArgumentException("Cannot have limits in sub queries of an 'OR' query");
          }
          if (builder.skip > 0) {
            throw new IllegalArgumentException("Cannot have skips in sub queries of an 'OR' query");
          }
          if (!builder.order.isEmpty()) {
            throw new IllegalArgumentException("Cannot have an order in sub queries of an 'OR' query");
          }
          if (!builder.includes.isEmpty()) {
            throw new IllegalArgumentException("Cannot have an include in sub queries of an 'OR' query");
          }
          if (builder.selectedKeys != null) {
            throw new IllegalArgumentException(
                "Cannot have an selectKeys in sub queries of an 'OR' query");
          }

          className = builder.className;
          constraints.add(builder.where);
        }

        return new State.Builder<T>(className)
            .whereSatifiesAnyOf(constraints);
      }

      private final String className;
      private final QueryConstraints where = new QueryConstraints();
      private final Set<String> includes = new HashSet<>();
      // This is nullable since we allow unset selectedKeys as well as no selectedKeys
      private Set<String> selectedKeys;
      private int limit = -1; // negative limits mean, do not send a limit
      private int skip = 0; // negative skip means do not send a skip
      private List<String> order = new ArrayList<>();
      private final Map<String, Object> extraOptions = new HashMap<>();

      // TODO(grantland): Move out of State
      private boolean trace;

      // Query Caching
      private CachePolicy cachePolicy = CachePolicy.IGNORE_CACHE;
      private long maxCacheAge = Long.MAX_VALUE; // 292 million years should be enough not to cause issues

      // LDS
      private boolean isFromLocalDatastore = false;
      private String pinName;
      private boolean ignoreACLs;

      public Builder(String className) {
        this.className = className;
      }

      public Builder(Class<T> subclass) {
        this(getSubclassingController().getClassName(subclass));
      }

      public Builder(State state) {
        className = state.className();
        where.putAll(state.constraints());
        includes.addAll(state.includes());
        selectedKeys = state.selectedKeys() != null ? new HashSet(state.selectedKeys()) : null;
        limit = state.limit();
        skip = state.skip();
        order.addAll(state.order());
        extraOptions.putAll(state.extraOptions());
        trace = state.isTracingEnabled();
        cachePolicy = state.cachePolicy();
        maxCacheAge = state.maxCacheAge();
        isFromLocalDatastore = state.isFromLocalDatastore();
        pinName  = state.pinName();
        ignoreACLs = state.ignoreACLs();
      }

      public Builder(Builder<T> builder) {
        className = builder.className;
        where.putAll(builder.where);
        includes.addAll(builder.includes);
        selectedKeys = builder.selectedKeys != null ? new HashSet(builder.selectedKeys) : null;
        limit = builder.limit;
        skip = builder.skip;
        order.addAll(builder.order);
        extraOptions.putAll(builder.extraOptions);
        trace = builder.trace;
        cachePolicy = builder.cachePolicy;
        maxCacheAge = builder.maxCacheAge;
        isFromLocalDatastore = builder.isFromLocalDatastore;
        pinName  = builder.pinName;
        ignoreACLs = builder.ignoreACLs;
      }

      public String getClassName() {
        return className;
      }

      //region Where Constraints

      /**
       * Add a constraint to the query that requires a particular key's value to be equal to the
       * provided value.
       *
       * @param key
       *          The key to check.
       * @param value
       *          The value that the {@link ParseObject} must contain.
       * @return this, so you can chain this call.
       */
      // TODO(grantland): Add typing
      public Builder<T> whereEqualTo(String key, Object value) {
        where.put(key, value);
        return this;
      }

      // TODO(grantland): Convert mutable parameter to immutable t6941155
      public Builder<T> whereDoesNotMatchKeyInQuery(String key, String keyInQuery, Builder<?> builder) {
        Map<String, Object> condition = new HashMap<>();
        condition.put("key", keyInQuery);
        condition.put("query", builder);
        return addConditionInternal(key, "$dontSelect", Collections.unmodifiableMap(condition));
      }

      // TODO(grantland): Convert mutable parameter to immutable t6941155
      public Builder<T> whereMatchesKeyInQuery(String key, String keyInQuery, Builder<?> builder) {
        Map<String, Object> condition = new HashMap<>();
        condition.put("key", keyInQuery);
        condition.put("query", builder);
        return addConditionInternal(key, "$select", Collections.unmodifiableMap(new HashMap<>(condition)));
      }

      // TODO(grantland): Convert mutable parameter to immutable t6941155
      public Builder<T> whereDoesNotMatchQuery(String key, Builder<?> builder) {
        return addConditionInternal(key, "$notInQuery", builder);
      }

      // TODO(grantland): Convert mutable parameter to immutable t6941155
      public Builder<T> whereMatchesQuery(String key, Builder<?> builder) {
        return addConditionInternal(key, "$inQuery", builder);
      }

      public Builder<T> whereNear(String key, ParseGeoPoint point) {
        return addCondition(key, "$nearSphere", point);
      }

      public Builder<T> maxDistance(String key, double maxDistance) {
        return addCondition(key, "$maxDistance", maxDistance);
      }

      public Builder<T> whereWithin(String key, ParseGeoPoint southwest, ParseGeoPoint northeast) {
        List<Object> array = new ArrayList<>();
        array.add(southwest);
        array.add(northeast);
        Map<String, List<Object>> dictionary = new HashMap<>();
        dictionary.put("$box", array);
        return addCondition(key, "$within", dictionary);
      }

      public Builder<T> addCondition(String key, String condition,
          Collection<? extends Object> value) {
        return addConditionInternal(key, condition, Collections.unmodifiableCollection(value));
      }

      // TODO(grantland): Add typing
      public Builder<T> addCondition(String key, String condition, Object value) {
        return addConditionInternal(key, condition, value);
      }

      // Helper for condition queries.
      private Builder<T> addConditionInternal(String key, String condition, Object value) {
        KeyConstraints whereValue = null;

        // Check if we already have some of a condition
        if (where.containsKey(key)) {
          Object existingValue = where.get(key);
          if (existingValue instanceof KeyConstraints) {
            whereValue = (KeyConstraints) existingValue;
          }
        }
        if (whereValue == null) {
          whereValue = new KeyConstraints();
        }

        whereValue.put(condition, value);

        where.put(key, whereValue);
        return this;
      }

      // Used by ParseRelation
      /* package */ Builder<T> whereRelatedTo(ParseObject parent, String key) {
        where.put("$relatedTo", new RelationConstraint(key, parent));
        return this;
      }

      /**
       * Add a constraint that a require matches any one of an array of {@code ParseQuery}s.
       * <p/>
       * The {@code ParseQuery}s passed cannot have any orders, skips, or limits set.
       *
       * @param constraints
       *          The array of queries to or
       *
       * @return this, so you can chain this call.
       */
      private Builder<T> whereSatifiesAnyOf(List<QueryConstraints> constraints) {
        where.put("$or", constraints);
        return this;
      }

      // Used by getInBackground
      /* package */ Builder<T> whereObjectIdEquals(String objectId) {
        where.clear();
        where.put("objectId", objectId);
        return this;
      }

      // Used by clear
      /* package */ Builder<T> clear(String key) {
        where.remove(key);
        return this;
      }

      //endregion

      //region Order

      private Builder<T> setOrder(String key) {
        order.clear();
        order.add(key);
        return this;
      }

      private Builder<T> addOrder(String key) {
        order.add(key);
        return this;
      }

      /**
       * Sorts the results in ascending order by the given key.
       *
       * @param key
       *          The key to order by.
       * @return this, so you can chain this call.
       */
      public Builder<T> orderByAscending(String key) {
        return setOrder(key);
      }

      /**
       * Also sorts the results in ascending order by the given key.
       * <p/>
       * The previous sort keys have precedence over this key.
       *
       * @param key
       *          The key to order by
       * @return this, so you can chain this call.
       */
      public Builder<T> addAscendingOrder(String key) {
        return addOrder(key);
      }

      /**
       * Sorts the results in descending order by the given key.
       *
       * @param key
       *          The key to order by.
       * @return this, so you can chain this call.
       */
      public Builder<T> orderByDescending(String key) {
        return setOrder(String.format("-%s", key));
      }

      /**
       * Also sorts the results in descending order by the given key.
       * <p/>
       * The previous sort keys have precedence over this key.
       *
       * @param key
       *          The key to order by
       * @return this, so you can chain this call.
       */
      public Builder<T> addDescendingOrder(String key) {
        return addOrder(String.format("-%s", key));
      }

      //endregion

      //region Includes

      /**
       * Include nested {@link ParseObject}s for the provided key.
       * <p/>
       * You can use dot notation to specify which fields in the included object that are also fetched.
       *
       * @param key
       *          The key that should be included.
       * @return this, so you can chain this call.
       */
      public Builder<T> include(String key) {
        includes.add(key);
        return this;
      }

      //endregion

      /**
       * Restrict the fields of returned {@link ParseObject}s to only include the provided keys.
       * <p/>
       * If this is called multiple times, then all of the keys specified in each of the calls will be
       * included.
       * <p/>
       * <strong>Note:</strong> This option will be ignored when querying from the local datastore. This
       * is done since all the keys will be in memory anyway and there will be no performance gain from
       * removing them.
       *
       * @param keys
       *          The set of keys to include in the result.
       * @return this, so you can chain this call.
       */
      public Builder<T> selectKeys(Collection<String> keys) {
        if (selectedKeys == null) {
          selectedKeys = new HashSet<>();
        }
        selectedKeys.addAll(keys);
        return this;
      }

      public int getLimit() {
        return limit;
      }

      public Builder<T> setLimit(int limit) {
        this.limit = limit;
        return this;
      }

      public int getSkip() {
        return skip;
      }

      public Builder<T> setSkip(int skip) {
        this.skip = skip;
        return this;
      }

      // Used by ParseRelation
      /* package */ Builder<T> redirectClassNameForKey(String key) {
        extraOptions.put("redirectClassNameForKey", key);
        return this;
      }

      public Builder<T> setTracingEnabled(boolean trace) {
        this.trace = trace;
        return this;
      }

      public CachePolicy getCachePolicy() {
        throwIfLDSEnabled();
        return cachePolicy;
      }

      public Builder<T> setCachePolicy(CachePolicy cachePolicy) {
        throwIfLDSEnabled();
        this.cachePolicy = cachePolicy;
        return this;
      }

      public long getMaxCacheAge() {
        throwIfLDSEnabled();
        return maxCacheAge;
      }

      public Builder<T> setMaxCacheAge(long maxCacheAge) {
        throwIfLDSEnabled();
        this.maxCacheAge = maxCacheAge;
        return this;
      }

      public boolean isFromNetwork() {
        throwIfLDSDisabled();
        return !isFromLocalDatastore;
      }

      public Builder<T> fromNetwork() {
        throwIfLDSDisabled();
        isFromLocalDatastore = false;
        pinName = null;
        return this;
      }

      public Builder<T> fromLocalDatastore() {
        return fromPin(null);
      }

      public boolean isFromLocalDatstore() {
        return isFromLocalDatastore;
      }

      public Builder<T> fromPin() {
        return fromPin(ParseObject.DEFAULT_PIN);
      }

      public Builder<T> fromPin(String pinName) {
        throwIfLDSDisabled();
        isFromLocalDatastore = true;
        this.pinName = pinName;
        return this;
      }

      public Builder<T> ignoreACLs() {
        throwIfLDSDisabled();
        ignoreACLs = true;
        return this;
      }

      public State<T> build() {
        if (!isFromLocalDatastore && ignoreACLs) {
          throw new IllegalStateException("`ignoreACLs` cannot be combined with network queries");
        }
        return new State<>(this);
      }
    }

    private final String className;
    private final QueryConstraints where;
    private final Set<String> include;
    private final Set<String> selectedKeys;
    private final int limit;
    private final int skip;
    private final List<String> order;
    private final Map<String, Object> extraOptions;

    // TODO(grantland): Move out of State
    private final boolean trace;

    // Query Caching
    private final CachePolicy cachePolicy;
    private final long maxCacheAge;

    // LDS
    private final boolean isFromLocalDatastore;
    private final String pinName;
    private final boolean ignoreACLs;

    private State(Builder<T> builder) {
      className = builder.className;
      where = new QueryConstraints(builder.where);
      include = Collections.unmodifiableSet(new HashSet<>(builder.includes));
      selectedKeys = builder.selectedKeys != null
          ? Collections.unmodifiableSet(new HashSet<>(builder.selectedKeys))
          : null;
      limit = builder.limit;
      skip = builder.skip;
      order = Collections.unmodifiableList(new ArrayList<>(builder.order));
      extraOptions = Collections.unmodifiableMap(new HashMap<>(builder.extraOptions));

      trace = builder.trace;

      cachePolicy = builder.cachePolicy;
      maxCacheAge = builder.maxCacheAge;

      isFromLocalDatastore = builder.isFromLocalDatastore;
      pinName = builder.pinName;
      ignoreACLs = builder.ignoreACLs;
    }

    public String className() {
      return className;
    }

    public QueryConstraints constraints() {
      return where;
    }

    public Set<String> includes() {
      return include;
    }

    public Set<String> selectedKeys() {
      return selectedKeys;
    }

    public int limit() {
      return limit;
    }

    public int skip() {
      return skip;
    }

    public List<String> order() {
      return order;
    }

    public Map<String, Object> extraOptions() {
      return extraOptions;
    }

    public boolean isTracingEnabled() {
      return trace;
    }

    public CachePolicy cachePolicy() {
      return cachePolicy;
    }

    public long maxCacheAge() {
      return maxCacheAge;
    }

    public boolean isFromLocalDatastore() {
      return isFromLocalDatastore;
    }

    public String pinName() {
      return pinName;
    }

    public boolean ignoreACLs() {
      return ignoreACLs;
    }

    // Returns the query in JSON REST format for subqueries
    /* package */ JSONObject toJSON(ParseEncoder encoder) {
      JSONObject params = new JSONObject();

      try {
        params.put("className", className);
        params.put("where", encoder.encode(where));

        if (limit >= 0) {
          params.put("limit", limit);
        }
        if (skip > 0) {
          params.put("skip", skip);
        }
        if (!order.isEmpty()) {
          params.put("order", ParseTextUtils.join(",", order));
        }
        if (!include.isEmpty()) {
          params.put("include", ParseTextUtils.join(",", include));
        }
        if (selectedKeys != null) {
          params.put("fields", ParseTextUtils.join(",", selectedKeys));
        }
        if (trace) {
          params.put("trace", 1);
        }

        for (String key : extraOptions.keySet()) {
          params.put(key, encoder.encode(extraOptions.get(key)));
        }
      } catch (JSONException e) {
        throw new RuntimeException(e);
      }

      return params;
    }

    @Override
    public String toString() {
      return String.format(Locale.US, "%s[className=%s, where=%s, include=%s, " +
              "selectedKeys=%s, limit=%s, skip=%s, order=%s, extraOptions=%s, " +
              "cachePolicy=%s, maxCacheAge=%s, " +
              "trace=%s]",
          getClass().getName(),
          className,
          where,
          include,
          selectedKeys,
          limit,
          skip,
          order,
          extraOptions,
          cachePolicy,
          maxCacheAge,
          trace);
    }
  }


  private final State.Builder<T> builder;
  private ParseUser user;

  // Just like ParseFile
  private Set<TaskCompletionSource<?>> currentTasks = Collections.synchronizedSet(
      new HashSet<TaskCompletionSource<?>>());

  /**
   * Constructs a query for a {@link ParseObject} subclass type. A default query with no further
   * parameters will retrieve all {@link ParseObject}s of the provided class.
   *
   * @param subclass
   *          The {@link ParseObject} subclass type to retrieve.
   */
  public ParseQuery(Class<T> subclass) {
    this(getSubclassingController().getClassName(subclass));
  }

  /**
   * Constructs a query. A default query with no further parameters will retrieve all
   * {@link ParseObject}s of the provided class.
   *
   * @param theClassName
   *          The name of the class to retrieve {@link ParseObject}s for.
   */
  public ParseQuery(String theClassName) {
    this(new State.Builder<T>(theClassName));
  }

  /**
   * Constructs a copy of {@code query};
   *
   * @param query
   *          The query to copy.
   */
  public ParseQuery(ParseQuery<T> query) {
    this(new State.Builder<>(query.getBuilder()));
    user = query.user;
  }

  /* package */ ParseQuery(State.Builder<T> builder) {
    this.builder = builder;
  }

  /* package */ State.Builder<T> getBuilder() {
    return builder;
  }

  /**
   * Sets the user to be used for this query.
   *
   *
   * The query will use the user if set, otherwise it will read the current user.
   */
  /* package for tests */ ParseQuery<T> setUser(ParseUser user) {
    this.user = user;
    return this;
  }

  /**
   * Returns the user used for the query. This user is used to filter results based on ACLs on the
   * target objects. Can be {@code null} if the there is no current user or {@link #ignoreACLs} is
   * enabled.
   */
  /* package for tests */ Task<ParseUser> getUserAsync(State<T> state) {
    if (state.ignoreACLs()) {
      return Task.forResult(null);
    }
    if (user != null) {
      return Task.forResult(user);
    }
    return ParseUser.getCurrentUserAsync();
  }

  /**
   * Cancels the current network request(s) (if any is running).
   */
  //TODO (grantland): Deprecate and replace with CancellationTokens
  public void cancel() {
    Set<TaskCompletionSource<?>> tasks = new HashSet<>(currentTasks);
    for (TaskCompletionSource<?> tcs : tasks) {
      tcs.trySetCancelled();
    }
    currentTasks.removeAll(tasks);
  }

  public boolean isRunning() {
    return currentTasks.size() > 0;
  }

  /**
   * Retrieves a list of {@link ParseObject}s that satisfy this query.
   * <p/>
   * @return A list of all {@link ParseObject}s obeying the conditions set in this query.
   * @throws ParseException
   *           Throws a {@link ParseException} if no object is found.
   *
   * @see ParseException#OBJECT_NOT_FOUND
   */
  public List<T> find() throws ParseException {
    return ParseTaskUtils.wait(findInBackground());
  }

  /**
   * Retrieves at most one {@link ParseObject} that satisfies this query.
   * <p/>
   * <strong>Note:</strong>This mutates the {@code ParseQuery}.
   *
   * @return A {@link ParseObject} obeying the conditions set in this query.
   * @throws ParseException
   *           Throws a {@link ParseException} if no object is found.
   *
   * @see ParseException#OBJECT_NOT_FOUND
   */
  public T getFirst() throws ParseException {
    return ParseTaskUtils.wait(getFirstInBackground());
  }

  /**
   * Change the caching policy of this query.
   * <p/>
   * Unsupported when Local Datastore is enabled.
   *
   * @return this, so you can chain this call.
   *
   * @see ParseQuery#fromLocalDatastore()
   * @see ParseQuery#fromPin()
   * @see ParseQuery#fromPin(String)
   */
  public ParseQuery<T> setCachePolicy(CachePolicy newCachePolicy) {
    builder.setCachePolicy(newCachePolicy);
    return this;
  }

  /**
   * @return the caching policy.
   */
  public CachePolicy getCachePolicy() {
    return builder.getCachePolicy();
  }

  /**
   * Change the source of this query to the server.
   * <p/>
   * Requires Local Datastore to be enabled.
   *
   * @return this, so you can chain this call.
   *
   * @see ParseQuery#setCachePolicy(CachePolicy)
   */
  public ParseQuery<T> fromNetwork() {
    builder.fromNetwork();
    return this;
  }

  /* package */ boolean isFromNetwork() {
    return builder.isFromNetwork();
  }

  /**
   * Change the source of this query to all pinned objects.
   * <p/>
   * Requires Local Datastore to be enabled.
   *
   * @return this, so you can chain this call.
   *
   * @see ParseQuery#setCachePolicy(CachePolicy)
   */
  public ParseQuery<T> fromLocalDatastore() {
    builder.fromLocalDatastore();
    return this;
  }

  /**
   * Change the source of this query to the default group of pinned objects.
   * <p/>
   * Requires Local Datastore to be enabled.
   *
   * @return this, so you can chain this call.
   *
   * @see ParseObject#DEFAULT_PIN
   * @see ParseQuery#setCachePolicy(CachePolicy)
   */
  public ParseQuery<T> fromPin() {
    builder.fromPin();
    return this;
  }

  /**
   * Change the source of this query to a specific group of pinned objects.
   * <p/>
   * Requires Local Datastore to be enabled.
   *
   * @param name
   *          the pinned group
   * @return this, so you can chain this call.
   *
   * @see ParseQuery#setCachePolicy(CachePolicy)
   */
  public ParseQuery<T> fromPin(String name) {
    builder.fromPin(name);
    return this;
  }

  /**
   * Ignore ACLs when querying from the Local Datastore.
   * <p/>
   * This is particularly useful when querying for objects with Role based ACLs set on them.
   *
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> ignoreACLs() {
    builder.ignoreACLs();
    return this;
  }

  /**
   * Sets the maximum age of cached data that will be considered in this query.
   *
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> setMaxCacheAge(long maxAgeInMilliseconds) {
    builder.setMaxCacheAge(maxAgeInMilliseconds);
    return this;
  }

  /**
   * Gets the maximum age of cached data that will be considered in this query. The returned value
   * is in milliseconds
   */
  public long getMaxCacheAge() {
    return builder.getMaxCacheAge();
  }


  /**
   * Wraps the runnable operation and keeps it in sync with the given tcs, so we know how many
   * operations are running (currentTasks.size()) and can cancel them.
   */
  private <TResult> Task<TResult> perform(Callable<Task<TResult>> runnable, final TaskCompletionSource<?> tcs) {
    currentTasks.add(tcs);

    Task<TResult> task;
    try {
      task = runnable.call();
    } catch (Exception e) {
      task = Task.forError(e);
    }
    return task.continueWithTask(new Continuation<TResult, Task<TResult>>() {
      @Override
      public Task<TResult> then(Task<TResult> task) throws Exception {
        tcs.trySetResult(null); // release
        currentTasks.remove(tcs);
        return task;
      }
    });
  }

  /**
   * Retrieves a list of {@link ParseObject}s that satisfy this query from the source in a
   * background thread.
   * <p/>
   * This is preferable to using {@link #find()}, unless your code is already running in a
   * background thread.
   *
   * @return A {@link Task} that will be resolved when the find has completed.
   */
  public Task<List<T>> findInBackground() {
    return findAsync(builder.build());
  }

  /**
   * Retrieves a list of {@link ParseObject}s that satisfy this query from the source in a
   * background thread.
   * <p/>
   * This is preferable to using {@link #find()}, unless your code is already running in a
   * background thread.
   *
   * @param callback
   *          callback.done(objectList, e) is called when the find completes.
   */
  public void findInBackground(final FindCallback<T> callback) {
    final State<T> state = builder.build();

    final Task<List<T>> task;
    if (state.cachePolicy() != CachePolicy.CACHE_THEN_NETWORK ||
        state.isFromLocalDatastore()) {
      task = findAsync(state);
    } else {
      task = doCacheThenNetwork(state, callback, new CacheThenNetworkCallable<T, Task<List<T>>>() {
        @Override
        public Task<List<T>> call(State<T> state, ParseUser user, Task<Void> cancellationToken) {
          return findAsync(state, user, cancellationToken);
        }
      });
    }
    ParseTaskUtils.callbackOnMainThreadAsync(task, callback);
  }

  private Task<List<T>> findAsync(final State<T> state) {
    final TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
    return perform(new Callable<Task<List<T>>>() {
      @Override
      public Task<List<T>> call() throws Exception {
        return getUserAsync(state).onSuccessTask(new Continuation<ParseUser, Task<List<T>>>() {
          @Override
          public Task<List<T>> then(Task<ParseUser> task) throws Exception {
            final ParseUser user = task.getResult();
            return findAsync(state, user, tcs.getTask());
          }
        });
      }
    }, tcs);
  }

  /* package */ Task<List<T>> findAsync(State<T> state, ParseUser user, Task<Void> cancellationToken) {
    return ParseQuery.getQueryController().findAsync(state, user, cancellationToken);
  }

  /**
   * Retrieves at most one {@link ParseObject} that satisfies this query from the source in a
   * background thread.
   * <p/>
   * This is preferable to using {@link #getFirst()}, unless your code is already running in a
   * background thread.
   * <p/>
   * <strong>Note:</strong>This mutates the {@code ParseQuery}.
   *
   * @return A {@link Task} that will be resolved when the get has completed.
   */
  public Task<T> getFirstInBackground() {
    final State<T> state = builder.setLimit(1)
        .build();
    return getFirstAsync(state);
  }

  /**
   * Retrieves at most one {@link ParseObject} that satisfies this query from the source in a
   * background thread.
   * <p/>
   * This is preferable to using {@link #getFirst()}, unless your code is already running in a
   * background thread.
   * <p/>
   * <strong>Note:</strong>This mutates the {@code ParseQuery}.
   *
   * @param callback
   *          callback.done(object, e) is called when the find completes.
   */
  public void getFirstInBackground(final GetCallback<T> callback) {
    final State<T> state = builder.setLimit(1)
        .build();

    final Task<T> task;
    if (state.cachePolicy() != CachePolicy.CACHE_THEN_NETWORK ||
        state.isFromLocalDatastore()) {
      task = getFirstAsync(state);
    } else {
      task = doCacheThenNetwork(state, callback, new CacheThenNetworkCallable<T, Task<T>>() {
        @Override
        public Task<T> call(State<T> state, ParseUser user, Task<Void> cancellationToken) {
          return getFirstAsync(state, user, cancellationToken);
        }
      });
    }
    ParseTaskUtils.callbackOnMainThreadAsync(task, callback);
  }

  private Task<T> getFirstAsync(final State<T> state) {
    final TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
    return perform(new Callable<Task<T>>() {
      @Override
      public Task<T> call() throws Exception {
        return getUserAsync(state).onSuccessTask(new Continuation<ParseUser, Task<T>>() {
          @Override
          public Task<T> then(Task<ParseUser> task) throws Exception {
            final ParseUser user = task.getResult();
            return getFirstAsync(state, user, tcs.getTask());
          }
        });
      }
    }, tcs);
  }

  private Task<T> getFirstAsync(State<T> state, ParseUser user, Task<Void> cancellationToken) {
    return ParseQuery.getQueryController().getFirstAsync(state, user, cancellationToken);
  }

  /**
   * Counts the number of objects that match this query. This does not use caching.
   *
   * @throws ParseException
   *           Throws an exception when the network connection fails or when the query is invalid.
   */
  public int count() throws ParseException {
    return ParseTaskUtils.wait(countInBackground());
  }

  /**
   * Counts the number of objects that match this query in a background thread. This does not use
   * caching.
   *
   * @return A {@link Task} that will be resolved when the count has completed.
   */
  public Task<Integer> countInBackground() {
    State.Builder<T> copy = new State.Builder<>(builder);
    final State<T> state = copy.setLimit(0).build();
    return countAsync(state);
  }

  /**
   * Counts the number of objects that match this query in a background thread. This does not use
   * caching.
   *
   * @param callback
   *          callback.done(count, e) will be called when the count completes.
   */
  public void countInBackground(final CountCallback callback) {
    State.Builder<T> copy = new State.Builder<>(builder);
    final State<T> state = copy.setLimit(0).build();

    // Hack to workaround CountCallback's non-uniform signature.
    final ParseCallback2<Integer, ParseException> c = callback != null
        ? new ParseCallback2<Integer, ParseException>() {
            @Override
            public void done(Integer integer, ParseException e) {
              callback.done(e == null ? integer : -1, e);
            }
          }
        : null;

    final Task<Integer> task;
    if (state.cachePolicy() != CachePolicy.CACHE_THEN_NETWORK ||
        state.isFromLocalDatastore()) {
      task = countAsync(state);
    } else {
      task = doCacheThenNetwork(state, c, new CacheThenNetworkCallable<T, Task<Integer>>() {
        @Override
        public Task<Integer> call(State<T> state, ParseUser user, Task<Void> cancellationToken) {
          return countAsync(state, user, cancellationToken);
        }
      });
    }
    ParseTaskUtils.callbackOnMainThreadAsync(task, c);
  }

  private Task<Integer> countAsync(final State<T> state) {
    final TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
    return perform(new Callable<Task<Integer>>() {
      @Override
      public Task<Integer> call() throws Exception {
        return getUserAsync(state).onSuccessTask(new Continuation<ParseUser, Task<Integer>>() {
          @Override
          public Task<Integer> then(Task<ParseUser> task) throws Exception {
            final ParseUser user = task.getResult();
            return countAsync(state, user, tcs.getTask());
          }
        });
      }
    }, tcs);
  }

  private Task<Integer> countAsync(State<T> state, ParseUser user, Task<Void> cancellationToken) {
    return ParseQuery.getQueryController().countAsync(state, user, cancellationToken);
  }

  /**
   * Constructs a {@link ParseObject} whose id is already known by fetching data from the source.
   * <p/>
   * <strong>Note:</strong>This mutates the {@code ParseQuery}.
   *
   * @param objectId
   *          Object id of the {@link ParseObject} to fetch.
   * @throws ParseException
   *           Throws an exception when there is no such object or when the network connection
   *           fails.
   *
   * @see ParseException#OBJECT_NOT_FOUND
   */
  public T get(final String objectId) throws ParseException {
    return ParseTaskUtils.wait(getInBackground(objectId));
  }

  /**
   * Returns whether or not this query has a cached result.
   */
  //TODO (grantland): should be done Async since it does disk i/o & calls through to current user
  public boolean hasCachedResult() {
    throwIfLDSEnabled();

    // TODO(grantland): Is there a more efficient way to accomplish this rather than building a
    // new state just to check it's cacheKey?
    State<T> state = builder.build();

    ParseUser user = null;
    try {
      user = ParseTaskUtils.wait(getUserAsync(state));
    } catch (ParseException e) {
      // do nothing
    }
    String sessionToken = user != null ? user.getSessionToken() : null;

    /*
     * TODO: Once the count queries are cached, only return false when both queries miss in the
     * cache.
     */
    String raw = ParseKeyValueCache.loadFromKeyValueCache(
        ParseRESTQueryCommand.findCommand(state, sessionToken).getCacheKey(), state.maxCacheAge()
    );
    return raw != null;
  }

  /**
   * Removes the previously cached result for this query, forcing the next find() to hit the
   * network. If there is no cached result for this query, then this is a no-op.
   */
  //TODO (grantland): should be done Async since it does disk i/o & calls through to current user
  public void clearCachedResult() {
    throwIfLDSEnabled();

    // TODO(grantland): Is there a more efficient way to accomplish this rather than building a
    // new state just to check it's cacheKey?
    State<T> state = builder.build();

    ParseUser user = null;
    try {
      user = ParseTaskUtils.wait(getUserAsync(state));
    } catch (ParseException e) {
      // do nothing
    }
    String sessionToken = user != null ? user.getSessionToken() : null;

    // TODO: Once the count queries are cached, handle the cached results of the count query.
    ParseKeyValueCache.clearFromKeyValueCache(
        ParseRESTQueryCommand.findCommand(state, sessionToken).getCacheKey()
    );
  }

  /**
   * Clears the cached result for all queries.
   */
  public static void clearAllCachedResults() {
    throwIfLDSEnabled();

    ParseKeyValueCache.clearKeyValueCacheDir();
  }

  /**
   * Constructs a {@link ParseObject} whose id is already known by fetching data from the source in a
   * background thread. This does not use caching.
   * <p/>
   * This is preferable to using the {@link ParseObject#createWithoutData(String, String)}, unless
   * your code is already running in a background thread.
   *
   * @param objectId
   *          Object id of the {@link ParseObject} to fetch.
   *
   * @return A {@link Task} that is resolved when the fetch completes.
   */
  // TODO(grantland): Why is this an instance method? Shouldn't this just be a static method since
  // other parameters don't even make sense here?
  // We'll need to add a version with CancellationToken if we do.
  public Task<T> getInBackground(final String objectId) {
    final State<T> state = builder.setSkip(-1)
        .whereObjectIdEquals(objectId)
        .build();
    return getFirstAsync(state);
  }

  /**
   * Constructs a {@link ParseObject} whose id is already known by fetching data from the source in
   * a background thread. This does not use caching.
   * <p/>
   * This is preferable to using the {@link ParseObject#createWithoutData(String, String)}, unless
   * your code is already running in a background thread.
   *
   * @param objectId
   *          Object id of the {@link ParseObject} to fetch.
   * @param callback
   *          callback.done(object, e) will be called when the fetch completes.
   */
  // TODO(grantland): Why is this an instance method? Shouldn't this just be a static method since
  // other parameters don't even make sense here?
  // We'll need to add a version with CancellationToken if we do.
  public void getInBackground(final String objectId, final GetCallback<T> callback) {
    final State<T> state = builder.setSkip(-1)
        .whereObjectIdEquals(objectId)
        .build();

    final Task<T> task;
    if (state.cachePolicy() != CachePolicy.CACHE_THEN_NETWORK ||
        state.isFromLocalDatastore()) {
      task = getFirstAsync(state);
    } else {
      task = doCacheThenNetwork(state, callback, new CacheThenNetworkCallable<T, Task<T>>() {
        @Override
        public Task<T> call(State<T> state, ParseUser user, Task<Void> cancellationToken) {
          return getFirstAsync(state, user, cancellationToken);
        }
      });
    }
    ParseTaskUtils.callbackOnMainThreadAsync(task, callback);
  }

  //region CACHE_THEN_NETWORK

  /**
   * Helper method for CACHE_THEN_NETWORK.
   *
   * Serially executes the {@code delegate} once in cache with the {@code} callback and then returns
   * a task for the execution of the second {@code delegate} execution on the network for the caller
   * to callback on.
   */
  private <TResult> Task<TResult> doCacheThenNetwork(
      final ParseQuery.State<T> state,
      final ParseCallback2<TResult, ParseException> callback,
      final CacheThenNetworkCallable<T, Task<TResult>> delegate) {

    final TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
    return perform(new Callable<Task<TResult>>() {
      @Override
      public Task<TResult> call() throws Exception {
        return getUserAsync(state).onSuccessTask(new Continuation<ParseUser, Task<TResult>>() {
          @Override
          public Task<TResult> then(Task<ParseUser> task) throws Exception {
            final ParseUser user = task.getResult();
            final State<T> cacheState = new State.Builder<T>(state)
                .setCachePolicy(CachePolicy.CACHE_ONLY)
                .build();
            final State<T> networkState = new State.Builder<T>(state)
                .setCachePolicy(CachePolicy.NETWORK_ONLY)
                .build();

            Task<TResult> executionTask = delegate.call(cacheState, user, tcs.getTask());
            executionTask = ParseTaskUtils.callbackOnMainThreadAsync(executionTask, callback);
            return executionTask.continueWithTask(new Continuation<TResult, Task<TResult>>() {
              @Override
              public Task<TResult> then(Task<TResult> task) throws Exception {
                if (task.isCancelled()) {
                  return task;
                }
                return delegate.call(networkState, user, tcs.getTask());
              }
            });
          }
        });
      }
    }, tcs);
  }

  private interface CacheThenNetworkCallable<T extends ParseObject, TResult> {
    TResult call(ParseQuery.State<T> state, ParseUser user, Task<Void> cancellationToken);
  }

  //endregion

  /**
   * Add a constraint to the query that requires a particular key's value to be equal to the
   * provided value.
   *
   * @param key
   *          The key to check.
   * @param value
   *          The value that the {@link ParseObject} must contain.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereEqualTo(String key, Object value) {
    builder.whereEqualTo(key, value);
    return this;
  }

  /**
   * Add a constraint to the query that requires a particular key's value to be less than the
   * provided value.
   *
   * @param key
   *          The key to check.
   * @param value
   *          The value that provides an upper bound.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereLessThan(String key, Object value) {
    builder.addCondition(key, "$lt", value);
    return this;
  }

  /**
   * Add a constraint to the query that requires a particular key's value to be not equal to the
   * provided value.
   *
   * @param key
   *          The key to check.
   * @param value
   *          The value that must not be equalled.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereNotEqualTo(String key, Object value) {
    builder.addCondition(key, "$ne", value);
    return this;
  }

  /**
   * Add a constraint to the query that requires a particular key's value to be greater than the
   * provided value.
   *
   * @param key
   *          The key to check.
   * @param value
   *          The value that provides an lower bound.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereGreaterThan(String key, Object value) {
    builder.addCondition(key, "$gt", value);
    return this;
  }

  /**
   * Add a constraint to the query that requires a particular key's value to be less than or equal
   * to the provided value.
   *
   * @param key
   *          The key to check.
   * @param value
   *          The value that provides an upper bound.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereLessThanOrEqualTo(String key, Object value) {
    builder.addCondition(key, "$lte", value);
    return this;
  }

  /**
   * Add a constraint to the query that requires a particular key's value to be greater than or
   * equal to the provided value.
   *
   * @param key
   *          The key to check.
   * @param value
   *          The value that provides an lower bound.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereGreaterThanOrEqualTo(String key, Object value) {
    builder.addCondition(key, "$gte", value);
    return this;
  }

  /**
   * Add a constraint to the query that requires a particular key's value to be contained in the
   * provided list of values.
   *
   * @param key
   *          The key to check.
   * @param values
   *          The values that will match.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereContainedIn(String key, Collection<? extends Object> values) {
    builder.addCondition(key, "$in", values);
    return this;
  }

  /**
   * Add a constraint to the query that requires a particular key's value match another
   * {@code ParseQuery}.
   * <p/>
   * This only works on keys whose values are {@link ParseObject}s or lists of {@link ParseObject}s.
   * Add a constraint to the query that requires a particular key's value to contain every one of
   * the provided list of values.
   *
   * @param key
   *          The key to check. This key's value must be an array.
   * @param values
   *          The values that will match.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereContainsAll(String key, Collection<?> values) {
    builder.addCondition(key, "$all", values);
    return this;
  }

  /**
   * Add a constraint to the query that requires a particular key's value match another
   * {@code ParseQuery}.
   * <p/>
   * This only works on keys whose values are {@link ParseObject}s or lists of {@link ParseObject}s.
   *
   * @param key
   *          The key to check.
   * @param query
   *          The query that the value should match
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereMatchesQuery(String key, ParseQuery<?> query) {
    builder.whereMatchesQuery(key, query.getBuilder());
    return this;
  }

  /**
   * Add a constraint to the query that requires a particular key's value does not match another
   * {@code ParseQuery}.
   * <p/>
   * This only works on keys whose values are {@link ParseObject}s or lists of {@link ParseObject}s.
   *
   * @param key
   *          The key to check.
   * @param query
   *          The query that the value should not match
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereDoesNotMatchQuery(String key, ParseQuery<?> query) {
    builder.whereDoesNotMatchQuery(key, query.getBuilder());
    return this;
  }

  /**
   * Add a constraint to the query that requires a particular key's value matches a value for a key
   * in the results of another {@code ParseQuery}.
   *
   * @param key
   *          The key whose value is being checked
   * @param keyInQuery
   *          The key in the objects from the sub query to look in
   * @param query
   *          The sub query to run
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereMatchesKeyInQuery(String key, String keyInQuery, ParseQuery<?> query) {
    builder.whereMatchesKeyInQuery(key, keyInQuery, query.getBuilder());
    return this;
  }

  /**
   * Add a constraint to the query that requires a particular key's value does not match any value
   * for a key in the results of another {@code ParseQuery}.
   *
   * @param key
   *          The key whose value is being checked and excluded
   * @param keyInQuery
   *          The key in the objects from the sub query to look in
   * @param query
   *          The sub query to run
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereDoesNotMatchKeyInQuery(String key, String keyInQuery,
      ParseQuery<?> query) {
    builder.whereDoesNotMatchKeyInQuery(key, keyInQuery, query.getBuilder());
    return this;
  }

  /**
   * Add a constraint to the query that requires a particular key's value not be contained in the
   * provided list of values.
   *
   * @param key
   *          The key to check.
   * @param values
   *          The values that will not match.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereNotContainedIn(String key, Collection<? extends Object> values) {
    builder.addCondition(key, "$nin", values);
    return this;
  }

  /**
   * Add a proximity based constraint for finding objects with key point values near the point
   * given.
   *
   * @param key
   *          The key that the {@link ParseGeoPoint} is stored in.
   * @param point
   *          The reference {@link ParseGeoPoint} that is used.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereNear(String key, ParseGeoPoint point) {
    builder.whereNear(key, point);
    return this;
  }

  /**
   * Add a proximity based constraint for finding objects with key point values near the point given
   * and within the maximum distance given.
   * <p/>
   * Radius of earth used is {@code 3958.8} miles.
   *
   * @param key
   *          The key that the {@link ParseGeoPoint} is stored in.
   * @param point
   *          The reference {@link ParseGeoPoint} that is used.
   * @param maxDistance
   *          Maximum distance (in miles) of results to return.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereWithinMiles(String key, ParseGeoPoint point, double maxDistance) {
    return whereWithinRadians(key, point, maxDistance / ParseGeoPoint.EARTH_MEAN_RADIUS_MILE);
  }

  /**
   * Add a proximity based constraint for finding objects with key point values near the point given
   * and within the maximum distance given.
   * <p/>
   * Radius of earth used is {@code 6371.0} kilometers.
   *
   * @param key
   *          The key that the {@link ParseGeoPoint} is stored in.
   * @param point
   *          The reference {@link ParseGeoPoint} that is used.
   * @param maxDistance
   *          Maximum distance (in kilometers) of results to return.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereWithinKilometers(String key, ParseGeoPoint point, double maxDistance) {
    return whereWithinRadians(key, point, maxDistance / ParseGeoPoint.EARTH_MEAN_RADIUS_KM);
  }

  /**
   * Add a proximity based constraint for finding objects with key point values near the point given
   * and within the maximum distance given.
   *
   * @param key
   *          The key that the {@link ParseGeoPoint} is stored in.
   * @param point
   *          The reference {@link ParseGeoPoint} that is used.
   * @param maxDistance
   *          Maximum distance (in radians) of results to return.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereWithinRadians(String key, ParseGeoPoint point, double maxDistance) {
    builder.whereNear(key, point)
        .maxDistance(key, maxDistance);
    return this;
  }

  /**
   * Add a constraint to the query that requires a particular key's coordinates be contained within
   * a given rectangular geographic bounding box.
   *
   * @param key
   *          The key to be constrained.
   * @param southwest
   *          The lower-left inclusive corner of the box.
   * @param northeast
   *          The upper-right inclusive corner of the box.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereWithinGeoBox(
      String key, ParseGeoPoint southwest, ParseGeoPoint northeast) {
    builder.whereWithin(key, southwest, northeast);
    return this;
  }

  /**
   * Add a regular expression constraint for finding string values that match the provided regular
   * expression.
   * <p/>
   * This may be slow for large datasets.
   *
   * @param key
   *          The key that the string to match is stored in.
   * @param regex
   *          The regular expression pattern to match.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereMatches(String key, String regex) {
    builder.addCondition(key, "$regex", regex);
    return this;
  }

  /**
   * Add a regular expression constraint for finding string values that match the provided regular
   * expression.
   * <p/>
   * This may be slow for large datasets.
   *
   * @param key
   *          The key that the string to match is stored in.
   * @param regex
   *          The regular expression pattern to match.
   * @param modifiers
   *          Any of the following supported PCRE modifiers:<br>
   *          <code>i</code> - Case insensitive search<br>
   *          <code>m</code> - Search across multiple lines of input<br>
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereMatches(String key, String regex, String modifiers) {
    builder.addCondition(key, "$regex", regex);
    if (modifiers.length() != 0) {
      builder.addCondition(key, "$options", modifiers);
    }
    return this;
  }

  /**
   * Add a constraint for finding string values that contain a provided string.
   * <p/>
   * This will be slow for large datasets.
   *
   * @param key
   *          The key that the string to match is stored in.
   * @param substring
   *          The substring that the value must contain.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereContains(String key, String substring) {
    String regex = Pattern.quote(substring);
    whereMatches(key, regex);
    return this;
  }

  /**
   * Add a constraint for finding string values that start with a provided string.
   * <p/>
   * This query will use the backend index, so it will be fast even for large datasets.
   *
   * @param key
   *          The key that the string to match is stored in.
   * @param prefix
   *          The substring that the value must start with.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereStartsWith(String key, String prefix) {
    String regex = "^" + Pattern.quote(prefix);
    whereMatches(key, regex);
    return this;
  }

  /**
   * Add a constraint for finding string values that end with a provided string.
   * <p/>
   * This will be slow for large datasets.
   *
   * @param key
   *          The key that the string to match is stored in.
   * @param suffix
   *          The substring that the value must end with.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereEndsWith(String key, String suffix) {
    String regex = Pattern.quote(suffix) + "$";
    whereMatches(key, regex);
    return this;
  }

  /**
   * Include nested {@link ParseObject}s for the provided key.
   * <p/>
   * You can use dot notation to specify which fields in the included object that are also fetched.
   *
   * @param key
   *          The key that should be included.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> include(String key) {
    builder.include(key);
    return this;
  }

  /**
   * Restrict the fields of returned {@link ParseObject}s to only include the provided keys.
   * <p/>
   * If this is called multiple times, then all of the keys specified in each of the calls will be
   * included.
   * <p/>
   * <strong>Note:</strong> This option will be ignored when querying from the local datastore. This
   * is done since all the keys will be in memory anyway and there will be no performance gain from
   * removing them.
   *
   * @param keys
   *          The set of keys to include in the result.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> selectKeys(Collection<String> keys) {
    builder.selectKeys(keys);
    return this;
  }

  /**
   * Add a constraint for finding objects that contain the given key.
   *
   * @param key
   *          The key that should exist.
   *
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereExists(String key) {
    builder.addCondition(key, "$exists", true);
    return this;
  }

  /**
   * Add a constraint for finding objects that do not contain a given key.
   *
   * @param key
   *          The key that should not exist
   *
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> whereDoesNotExist(String key) {
    builder.addCondition(key, "$exists", false);
    return this;
  }

  /**
   * Sorts the results in ascending order by the given key.
   *
   * @param key
   *          The key to order by.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> orderByAscending(String key) {
    builder.orderByAscending(key);
    return this;
  }

  /**
   * Also sorts the results in ascending order by the given key.
   * <p/>
   * The previous sort keys have precedence over this key.
   *
   * @param key
   *          The key to order by
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> addAscendingOrder(String key) {
    builder.addAscendingOrder(key);
    return this;
  }

  /**
   * Sorts the results in descending order by the given key.
   *
   * @param key
   *          The key to order by.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> orderByDescending(String key) {
    builder.orderByDescending(key);
    return this;
  }

  /**
   * Also sorts the results in descending order by the given key.
   * <p/>
   * The previous sort keys have precedence over this key.
   *
   * @param key
   *          The key to order by
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> addDescendingOrder(String key) {
    builder.addDescendingOrder(key);
    return this;
  }

  /**
   * Controls the maximum number of results that are returned.
   * <p/>
   * Setting a negative limit denotes retrieval without a limit. The default limit is {@code 100},
   * with a maximum of {@code 1000} results being returned at a time.
   *
   * @param newLimit The new limit.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> setLimit(int newLimit) {
    builder.setLimit(newLimit);
    return this;
  }

  /**
   * Accessor for the limit.
   */
  public int getLimit() {
    return builder.getLimit();
  }

  /**
   * Controls the number of results to skip before returning any results.
   * <p/>
   * This is useful for pagination. Default is to skip zero results.
   *
   * @param newSkip The new skip
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> setSkip(int newSkip) {
    builder.setSkip(newSkip);
    return this;
  }

  /**
   * Accessor for the skip value.
   */
  public int getSkip() {
    return builder.getSkip();
  }

  /**
   * Accessor for the class name.
   */
  public String getClassName() {
    return builder.getClassName();
  }

  /**
   * Clears constraints related to the given key, if any was set previously.
   * Order, includes and selected keys are not affected by this operation.
   *
   * @param key key to be cleared from current constraints.
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> clear(String key) {
    builder.clear(key);
    return this;
  }

  /**
   * Turn on performance tracing of finds.
   * <p/>
   * If performance tracing is already turned on this does nothing. In general you don't need to call trace.
   *
   * @return this, so you can chain this call.
   */
  public ParseQuery<T> setTrace(boolean shouldTrace) {
    builder.setTracingEnabled(shouldTrace);
    return this;
  }
}
