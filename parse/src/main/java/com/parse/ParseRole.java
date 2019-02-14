/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

/**
 * Represents a Role on the Parse server. {@code ParseRole}s represent groupings of
 * {@code ParseUsers} for the purposes of granting permissions (e.g. specifying a {@link ParseACL}
 * for a {@link ParseObject}). Roles are specified by their sets of child users and child roles, all
 * of which are granted any permissions that the parent role has.<br />
 * <br />
 * Roles must have a name (which cannot be changed after creation of the role), and must specify an
 * ACL.
 */
@ParseClassName("_Role")
public class ParseRole extends ParseObject {
    private static final Pattern NAME_PATTERN = Pattern.compile("^[0-9a-zA-Z_\\- ]+$");

    /**
     * Used for the factory methods. Developers will need to set a name on objects created like this,
     * which is why the constructor with a roleName is exposed publicly.
     */
    ParseRole() {
    }

    /**
     * Constructs a new ParseRole with the given name. If no default ACL has been specified, you must
     * provide an ACL for the role.
     *
     * @param name The name of the Role to create.
     */
    public ParseRole(String name) {
        this();
        setName(name);
    }

    /**
     * Constructs a new ParseRole with the given name.
     *
     * @param name The name of the Role to create.
     * @param acl  The ACL for this role. Roles must have an ACL.
     */
    public ParseRole(String name, ParseACL acl) {
        this(name);
        setACL(acl);
    }

    /**
     * Gets a {@link ParseQuery} over the Role collection.
     *
     * @return A new query over the Role collection.
     */
    public static ParseQuery<ParseRole> getQuery() {
        return ParseQuery.getQuery(ParseRole.class);
    }

    /**
     * Gets the name of the role.
     *
     * @return the name of the role.
     */
    public String getName() {
        return this.getString("name");
    }

    /**
     * Sets the name for a role. This value must be set before the role has been saved to the server,
     * and cannot be set once the role has been saved.<br />
     * <br />
     * A role's name can only contain alphanumeric characters, _, -, and spaces.
     *
     * @param name The name of the role.
     * @throws IllegalStateException if the object has already been saved to the server.
     */
    public void setName(String name) {
        this.put("name", name);
    }

    /**
     * Gets the {@link ParseRelation} for the {@link ParseUser}s that are direct children of this
     * role. These users are granted any privileges that this role has been granted (e.g. read or
     * write access through ACLs). You can add or remove users from the role through this relation.
     *
     * @return the relation for the users belonging to this role.
     */
    public ParseRelation<ParseUser> getUsers() {
        return getRelation("users");
    }

    /**
     * Gets the {@link ParseRelation} for the {@link ParseRole}s that are direct children of this
     * role. These roles' users are granted any privileges that this role has been granted (e.g. read
     * or write access through ACLs). You can add or remove child roles from this role through this
     * relation.
     *
     * @return the relation for the roles belonging to this role.
     */
    public ParseRelation<ParseRole> getRoles() {
        return getRelation("roles");
    }

    @Override
        /* package */ void validateSave() {
        synchronized (mutex) {
            if (this.getObjectId() == null && getName() == null) {
                throw new IllegalStateException("New roles must specify a name.");
            }
            super.validateSave();
        }
    }

    @Override
    public void put(@NonNull String key, @NonNull Object value) {
        if ("name".equals(key)) {
            if (this.getObjectId() != null) {
                throw new IllegalArgumentException(
                        "A role's name can only be set before it has been saved.");
            }
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("A role's name must be a String.");
            }
            if (!NAME_PATTERN.matcher((String) value).matches()) {
                throw new IllegalArgumentException(
                        "A role's name can only contain alphanumeric characters, _, -, and spaces.");
            }
        }
        super.put(key, value);
    }
}
