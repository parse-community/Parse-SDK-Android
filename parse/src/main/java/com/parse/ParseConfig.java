/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;

/**
 * The {@code ParseConfig} is a local representation of configuration data that can be set from the
 * Parse dashboard.
 */
public class ParseConfig {
    /* package for tests */ static final TaskQueue taskQueue = new TaskQueue();

    /* package for tests */ final Map<String, Object> params;

    /* package */ ParseConfig(Map<String, Object> params) {
        this.params = Collections.unmodifiableMap(params);
    }

    /* package */ ParseConfig() {
        params = Collections.unmodifiableMap(new HashMap<String, Object>());
    }

    /* package for tests */
    static ParseConfigController getConfigController() {
        return ParseCorePlugins.getInstance().getConfigController();
    }

    /**
     * Retrieves the most recently-fetched configuration object, either from memory or
     * disk if necessary.
     *
     * @return The most recently-fetched {@code ParseConfig} if it exists, else an empty
     * {@code ParseConfig}
     */
    public static ParseConfig getCurrentConfig() {
        try {
            return ParseTaskUtils.wait(getConfigController().getCurrentConfigController()
                    .getCurrentConfigAsync()
            );
        } catch (ParseException e) {
            // In order to have backward compatibility, we swallow the exception silently.
            return new ParseConfig();
        }
    }

    /**
     * Fetches a new configuration object from the server.
     *
     * @return The {@code ParseConfig} that was fetched.
     * @throws ParseException Throws an exception if the server is inaccessible.
     */
    public static ParseConfig get() throws ParseException {
        return ParseTaskUtils.wait(getInBackground());
    }

    /**
     * Fetches a new configuration object from the server in a background thread. This is preferable
     * to using {@link #get()}, unless your code is already running from a background thread.
     *
     * @param callback callback.done(config, e) is called when the fetch completes.
     */
    public static void getInBackground(ConfigCallback callback) {
        ParseTaskUtils.callbackOnMainThreadAsync(getInBackground(), callback);
    }

    /**
     * Fetches a new configuration object from the server in a background thread. This is preferable
     * to using {@link #get()}, unless your code is already running from a background thread.
     *
     * @return A Task that is resolved when the fetch completes.
     */
    public static Task<ParseConfig> getInBackground() {
        return taskQueue.enqueue(new Continuation<Void, Task<ParseConfig>>() {
            @Override
            public Task<ParseConfig> then(Task<Void> toAwait) {
                return getAsync(toAwait);
            }
        });
    }

    private static Task<ParseConfig> getAsync(final Task<Void> toAwait) {
        return ParseUser.getCurrentSessionTokenAsync().onSuccessTask(new Continuation<String, Task<ParseConfig>>() {
            @Override
            public Task<ParseConfig> then(Task<String> task) {
                final String sessionToken = task.getResult();
                return toAwait.continueWithTask(new Continuation<Void, Task<ParseConfig>>() {
                    @Override
                    public Task<ParseConfig> then(Task<Void> task) {
                        return getConfigController().getAsync(sessionToken);
                    }
                });
            }
        });
    }

    @SuppressWarnings("unchecked")
    /* package */ static ParseConfig decode(JSONObject json, ParseDecoder decoder) {
        Map<String, Object> decodedObject = (Map<String, Object>) decoder.decode(json);
        Map<String, Object> decodedParams = (Map<String, Object>) decodedObject.get("params");
        if (decodedParams == null) {
            throw new RuntimeException("Object did not contain the 'params' key.");
        }
        return new ParseConfig(decodedParams);
    }

    /* package */ Map<String, Object> getParams() {
        return Collections.unmodifiableMap(new HashMap<>(params));
    }

    /**
     * Access a value. In most cases it is more convenient to use a helper function such as
     * {@link #getString} or {@link #getInt}.
     *
     * @param key The key to access the value for.
     * @return Returns {@code null} if there is no such key.
     */
    public Object get(String key) {
        return get(key, null);
    }

    /**
     * Access a value, returning a default value if the key doesn't exist. In most cases it is more
     * convenient to use a helper function such as {@link #getString} or {@link #getInt}.
     *
     * @param key          The key to access the value for.
     * @param defaultValue The value to return if the key is not present in the configuration object.
     * @return The default value if there is no such key.
     */
    public Object get(String key, Object defaultValue) {
        if (!params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value == JSONObject.NULL) {
            return null;
        }
        return params.get(key);
    }

    /**
     * Access a {@code boolean} value.
     *
     * @param key The key to access the value for.
     * @return Returns false if there is no such key or if it is not a {@code boolean}.
     */
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    /**
     * Access a {@code boolean} value, returning a default value if it doesn't exist.
     *
     * @param key          The key to access the value for.
     * @param defaultValue The value to return if the key is not present or has the wrong type.
     * @return The default value if there is no such key or if it is not a {@code boolean}.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        if (!params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);
        return (value instanceof Boolean) ? (Boolean) value : defaultValue;
    }

    /**
     * Access a {@link Date} value.
     *
     * @param key The key to access the value for.
     * @return Returns {@code null} if there is no such key or if it is not a {@link Date}.
     */
    public Date getDate(String key) {
        return getDate(key, null);
    }

    /**
     * Access a {@link Date} value, returning a default value if it doesn't exist.
     *
     * @param key          The key to access the value for.
     * @param defaultValue The value to return if the key is not present or has the wrong type.
     * @return The default value if there is no such key or if it is not a {@link Date}.
     */
    public Date getDate(String key, Date defaultValue) {
        if (!params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        return (value instanceof Date) ? (Date) value : defaultValue;
    }

    /**
     * Access a {@code double} value.
     *
     * @param key The key to access the value for.
     * @return Returns 0 if there is no such key or if it is not a number.
     */
    public double getDouble(String key) {
        return getDouble(key, 0.0);
    }

    /**
     * Access a {@code double} value, returning a default value if it doesn't exist.
     *
     * @param key          The key to access the value for.
     * @param defaultValue The value to return if the key is not present or has the wrong type.
     * @return The default value if there is no such key or if it is not a number.
     */
    public double getDouble(String key, double defaultValue) {
        Number number = getNumber(key);
        return number != null ? number.doubleValue() : defaultValue;
    }

    /**
     * Access an {@code int} value.
     *
     * @param key The key to access the value for.
     * @return Returns 0 if there is no such key or if it is not a number.
     */
    public int getInt(String key) {
        return getInt(key, 0);
    }

    /**
     * Access an {@code int} value, returning a default value if it doesn't exist.
     *
     * @param key          The key to access the value for.
     * @param defaultValue The value to return if the key is not present or has the wrong type.
     * @return The default value if there is no such key or if it is not a number.
     */
    public int getInt(String key, int defaultValue) {
        Number number = getNumber(key);
        return number != null ? number.intValue() : defaultValue;
    }

    /**
     * Access a {@link JSONArray} value.
     *
     * @param key The key to access the value for.
     * @return Returns {@code null} if there is no such key or if it is not a {@link JSONArray}.
     */
    public JSONArray getJSONArray(String key) {
        return getJSONArray(key, null);
    }

    /**
     * Access a {@link JSONArray} value, returning a default value if it doesn't exist.
     *
     * @param key          The key to access the value for.
     * @param defaultValue The value to return if the key is not present or has the wrong type.
     * @return The default value if there is no such key or if it is not a {@link JSONArray}.
     */
    public JSONArray getJSONArray(String key, JSONArray defaultValue) {
        List<Object> list = getList(key);
        Object encoded = (list != null) ? PointerEncoder.get().encode(list) : null;
        //TODO(mengyan) There are actually two cases, getList(key) will return null
        // case 1: key not exist, in this situation, we should return JSONArray defaultValue
        // case 2: key exist but value is Json.NULL, in this situation, we should return null
        // The following line we only cover case 2. We can not revise it since it may break some
        // existing app, but we should do it someday.
        return (encoded == null || encoded instanceof JSONArray) ? (JSONArray) encoded : defaultValue;
    }


    /**
     * Access a {@link JSONObject} value.
     *
     * @param key The key to access the value for.
     * @return Returns {@code null} if there is no such key or if it is not a {@link JSONObject}.
     */
    public JSONObject getJSONObject(String key) {
        return getJSONObject(key, null);
    }

    /**
     * Access a {@link JSONObject} value, returning a default value if it doesn't exist.
     *
     * @param key          The key to access the value for.
     * @param defaultValue The value to return if the key is not present or has the wrong type.
     * @return The default value if there is no such key or if it is not a {@link JSONObject}.
     */
    public JSONObject getJSONObject(String key, JSONObject defaultValue) {
        Map<String, Object> map = getMap(key);
        Object encoded = (map != null) ? PointerEncoder.get().encode(map) : null;
        //TODO(mengyan) There are actually two cases, getList(key) will return null
        // case 1: key not exist, in this situation, we should return JSONArray defaultValue
        // case 2: key exist but value is Json.NULL, in this situation, we should return null
        // The following line we only cover case 2. We can not revise it since it may break some
        // existing app, but we should do it someday.
        return (encoded == null || encoded instanceof JSONObject) ? (JSONObject) encoded : defaultValue;
    }

    /**
     * Access a {@link List} value.
     *
     * @param key The key to access the value for.
     * @return Returns {@code null} if there is no such key or if it cannot be converted to a
     * {@link List}.
     */
    public <T> List<T> getList(String key) {
        return getList(key, null);
    }

    /**
     * Access a {@link List} value, returning a default value if it doesn't exist.
     *
     * @param key          The key to access the value for.
     * @param defaultValue The value to return if the key is not present or has the wrong type.
     * @return The default value if there is no such key or if it cannot be
     * converted to a {@link List}.
     */
    public <T> List<T> getList(String key, List<T> defaultValue) {
        if (!params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);

        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<T> returnValue = (value instanceof List) ? (List<T>) value : defaultValue;
        return returnValue;
    }

    /**
     * Access a {@code long} value.
     *
     * @param key The key to access the value for.
     * @return Returns 0 if there is no such key or if it is not a number.
     */
    public long getLong(String key) {
        return getLong(key, 0);
    }

    /**
     * Access a {@code long} value, returning a default value if it doesn't exist.
     *
     * @param key          The key to access the value for.
     * @param defaultValue The value to return if the key is not present or has the wrong type.
     * @return The default value if there is no such key or if it is not a number.
     */
    public long getLong(String key, long defaultValue) {
        Number number = getNumber(key);
        return number != null ? number.longValue() : defaultValue;
    }

    /**
     * Access a {@link Map} value.
     *
     * @param key The key to access the value for.
     * @return {@code null} if there is no such key or if it cannot be converted to a
     * {@link Map}.
     */
    public <V> Map<String, V> getMap(String key) {
        return getMap(key, null);
    }

    /**
     * Access a {@link Map} value, returning a default value if it doesn't exist.
     *
     * @param key          The key to access the value for.
     * @param defaultValue The value to return if the key is not present or has the wrong type.
     * @return The default value if there is no such key or if it cannot be converted
     * to a {@link Map}.
     */
    public <V> Map<String, V> getMap(String key, Map<String, V> defaultValue) {
        if (!params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);

        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, V> returnValue = (value instanceof Map) ? (Map<String, V>) value : defaultValue;
        return returnValue;
    }

    /**
     * Access a numerical value.
     *
     * @param key The key to access the value for.
     * @return Returns {@code null} if there is no such key or if it is not a {@link Number}.
     */
    public Number getNumber(String key) {
        return getNumber(key, null);
    }

    /**
     * Access a numerical value, returning a default value if it doesn't exist.
     *
     * @param key          The key to access the value for.
     * @param defaultValue The value to return if the key is not present or has the wrong type.
     * @return The default value if there is no such key or if it is not a {@link Number}.
     */
    public Number getNumber(String key, Number defaultValue) {
        if (!params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        return (value instanceof Number) ? (Number) value : defaultValue;
    }

    /**
     * Access a {@link ParseFile} value. This function will not perform a network request. Unless the
     * {@link ParseFile} has been downloaded (e.g. by calling {@link ParseFile#getData()}),
     * {@link ParseFile#isDataAvailable()} will return false.
     *
     * @param key The key to access the value for.
     * @return {@code null} if there is no such key or if it is not a {@link ParseFile}.
     */
    public ParseFile getParseFile(String key) {
        return getParseFile(key, null);
    }

    /**
     * Access a {@link ParseFile} value, returning a default value if it doesn't exist. This function
     * will not perform a network request. Unless the {@link ParseFile} has been downloaded
     * (e.g. by calling {@link ParseFile#getData()}), {@link ParseFile#isDataAvailable()} will return
     * false.
     *
     * @param key          The key to access the value for.
     * @param defaultValue The value to return if the key is not present or has the wrong type.
     * @return The default value if there is no such key or if it is not a {@link ParseFile}.
     */
    public ParseFile getParseFile(String key, ParseFile defaultValue) {
        if (!params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        return (value instanceof ParseFile) ? (ParseFile) value : defaultValue;
    }

    /**
     * Access a {@link ParseGeoPoint} value.
     *
     * @param key The key to access the value for
     * @return {@code null} if there is no such key or if it is not a {@link ParseGeoPoint}.
     */
    public ParseGeoPoint getParseGeoPoint(String key) {
        return getParseGeoPoint(key, null);
    }

    /**
     * Access a {@link ParseGeoPoint} value, returning a default value if it doesn't exist.
     *
     * @param key          The key to access the value for
     * @param defaultValue The value to return if the key is not present or has the wrong type.
     * @return The default value if there is no such key or if it is not a {@link ParseGeoPoint}.
     */
    public ParseGeoPoint getParseGeoPoint(String key, ParseGeoPoint defaultValue) {
        if (!params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        return (value instanceof ParseGeoPoint) ? (ParseGeoPoint) value : defaultValue;
    }

    /**
     * Access a {@link String} value.
     *
     * @param key The key to access the value for.
     * @return Returns {@code null} if there is no such key or if it is not a {@link String}.
     */
    public String getString(String key) {
        return getString(key, null);
    }

    /**
     * Access a {@link String} value, returning a default value if it doesn't exist.
     *
     * @param key          The key to access the value for.
     * @param defaultValue The value to return if the key is not present or has the wrong type.
     * @return The default value if there is no such key or if it is not a {@link String}.
     */
    public String getString(String key, String defaultValue) {
        if (!params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        return (value instanceof String) ? (String) value : defaultValue;
    }

    @Override
    public String toString() {
        return "ParseConfig[" + params.toString() + "]";
    }
}
