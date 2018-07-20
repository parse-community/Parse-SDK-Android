/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.List;

@ParseClassName("_Pin")
class ParsePin extends ParseObject {

    /* package */ static final String KEY_NAME = "_name";
    private static final String KEY_OBJECTS = "_objects";

    public ParsePin() {
        // do nothing
    }

    @Override
    boolean needsDefaultACL() {
        return false;
    }

    public String getName() {
        return getString(KEY_NAME);
    }

    public void setName(String name) {
        put(KEY_NAME, name);
    }

    public List<ParseObject> getObjects() {
        return getList(KEY_OBJECTS);
    }

    public void setObjects(List<ParseObject> objects) {
        put(KEY_OBJECTS, objects);
    }
}
