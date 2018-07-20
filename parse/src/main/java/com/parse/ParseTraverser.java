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
import org.json.JSONException;
import org.json.JSONObject;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Subclass ParseTraverser to make an function to be run recursively on every object pointed to on
 * the given object.
 */
abstract class ParseTraverser {
    // Whether to recurse into ParseObjects that are seen.
    private boolean traverseParseObjects;

    // Whether to call visit with the object passed in.
    private boolean yieldRoot;

    /**
     * Creates a new ParseTraverser.
     */
    public ParseTraverser() {
        traverseParseObjects = false;
        yieldRoot = false;
    }

    /**
     * Override this method to implement your own functionality.
     *
     * @return true if you want the Traverser to continue. false if you want it to cancel.
     */
    protected abstract boolean visit(Object object);

    /**
     * Internal implementation of traverse.
     */
    private void traverseInternal(Object root, boolean yieldRoot, IdentityHashMap<Object, Object> seen) {
        if (root == null || seen.containsKey(root)) {
            return;
        }

        if (yieldRoot) {
            if (!visit(root)) {
                return;
            }
        }

        seen.put(root, root);

        if (root instanceof JSONObject) {
            JSONObject json = (JSONObject) root;
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    traverseInternal(json.get(key), true, seen);
                } catch (JSONException e) {
                    // This should never happen.
                    throw new RuntimeException(e);
                }
            }

        } else if (root instanceof JSONArray) {
            JSONArray array = (JSONArray) root;
            for (int i = 0; i < array.length(); ++i) {
                try {
                    traverseInternal(array.get(i), true, seen);
                } catch (JSONException e) {
                    // This should never happen.
                    throw new RuntimeException(e);
                }
            }

        } else if (root instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) root;
            for (Object value : map.values()) {
                traverseInternal(value, true, seen);
            }

        } else if (root instanceof List) {
            List<?> list = (List<?>) root;
            for (Object value : list) {
                traverseInternal(value, true, seen);
            }

        } else if (root instanceof ParseObject) {
            if (traverseParseObjects) {
                ParseObject object = (ParseObject) root;
                for (String key : object.keySet()) {
                    traverseInternal(object.get(key), true, seen);
                }
            }

        } else if (root instanceof ParseACL) {
            ParseACL acl = (ParseACL) root;
            ParseUser user = acl.getUnresolvedUser();
            if (user != null && user.isCurrentUser()) {
                traverseInternal(user, true, seen);
            }
        }
    }

    /**
     * Sets whether to recurse into ParseObjects that are seen.
     *
     * @return this to enable chaining.
     */
    public ParseTraverser setTraverseParseObjects(boolean newValue) {
        traverseParseObjects = newValue;
        return this;
    }

    /**
     * Sets whether to call visit with the object passed in.
     *
     * @return this to enable chaining.
     */
    public ParseTraverser setYieldRoot(boolean newValue) {
        yieldRoot = newValue;
        return this;
    }

    /**
     * Causes the traverser to traverse all objects pointed to by root, recursively.
     */
    public void traverse(Object root) {
        IdentityHashMap<Object, Object> seen = new IdentityHashMap<>();
        traverseInternal(root, yieldRoot, seen);
    }
}
