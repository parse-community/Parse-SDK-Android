/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.lang.ref.WeakReference;

/** package */ class ParseDefaultACLController {

  /* package for tests */ ParseACL defaultACL;
  /* package for tests */ boolean defaultACLUsesCurrentUser;
  /* package for tests */ WeakReference<ParseUser> lastCurrentUser;
  /* package for tests */ ParseACL defaultACLWithCurrentUser;

  /**
   * Sets a default ACL that will be applied to all {@link ParseObject}s when they are created.
   *
   * @param acl
   *          The ACL to use as a template for all {@link ParseObject}s created after set
   *          has been called. This value will be copied and used as a template for the creation of
   *          new ACLs, so changes to the instance after {@code set(ParseACL, boolean)}
   *          has been called will not be reflected in new {@link ParseObject}s.
   * @param withAccessForCurrentUser
   *          If {@code true}, the {@code ParseACL} that is applied to newly-created
   *          {@link ParseObject}s will provide read and write access to the
   *          {@link ParseUser#getCurrentUser()} at the time of creation. If {@code false}, the
   *          provided ACL will be used without modification. If acl is {@code null}, this value is
   *          ignored.
   */
  public void set(ParseACL acl, boolean withAccessForCurrentUser) {
    defaultACLWithCurrentUser = null;
    lastCurrentUser = null;
    if (acl != null) {
      ParseACL newDefaultACL = acl.copy();
      newDefaultACL.setShared(true);
      defaultACL = newDefaultACL;
      defaultACLUsesCurrentUser = withAccessForCurrentUser;
    } else {
      defaultACL = null;
    }
  }

  public ParseACL get() {
    if (defaultACLUsesCurrentUser && defaultACL != null) {
      ParseUser currentUser = ParseUser.getCurrentUser();
      if (currentUser != null) {
        // If the currentUser has changed, generate a new ACL from the defaultACL.
        ParseUser last = lastCurrentUser != null ? lastCurrentUser.get() : null;
        if (last != currentUser) {
          ParseACL newDefaultACLWithCurrentUser = defaultACL.copy();
          newDefaultACLWithCurrentUser.setShared(true);
          newDefaultACLWithCurrentUser.setReadAccess(currentUser, true);
          newDefaultACLWithCurrentUser.setWriteAccess(currentUser, true);
          defaultACLWithCurrentUser = newDefaultACLWithCurrentUser;
          lastCurrentUser = new WeakReference<>(currentUser);
        }
        return defaultACLWithCurrentUser;
      }
    }
    return defaultACL;
  }

}
