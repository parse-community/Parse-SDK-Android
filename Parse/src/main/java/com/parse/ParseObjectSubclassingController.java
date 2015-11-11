/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/* package */ class ParseObjectSubclassingController {
  private static class ObjectSubclassInfo {
    public final Class<? extends ParseObject> type;
    public final Constructor<? extends ParseObject> constructor;

    public ObjectSubclassInfo(Class<? extends ParseObject> clazz) throws NoSuchMethodException, IllegalAccessException {
      type = clazz;
      constructor = getConstructor(clazz);
    }

    private static Constructor<? extends ParseObject> getConstructor(Class<? extends  ParseObject> clazz) throws NoSuchMethodException, IllegalAccessException {
      Constructor<? extends ParseObject> constructor = clazz.getDeclaredConstructor();
      if (constructor == null) {
        throw new NoSuchMethodException();
      }
      int modifiers = constructor.getModifiers();
      if (Modifier.isPublic(modifiers) || (clazz.getPackage().getName().equals("com.parse") &&
          !(Modifier.isProtected(modifiers) || Modifier.isPrivate(modifiers)))) {
        return constructor;
      }
      throw new IllegalAccessException();
    }
  }

  private final Object mutex = new Object();
  private final Map<String, ObjectSubclassInfo> registeredSubclasses = new HashMap<>();

  /* package */ String getClassName(Class<? extends ParseObject> clazz) {
    ParseClassName info = clazz.getAnnotation(ParseClassName.class);
    if (info == null) {
      throw new IllegalArgumentException("No ParseClassName annotation provided on " + clazz);
    }
    return info.value();
  }

  /* package */ boolean isSubclassValid(String className, Class<? extends ParseObject> clazz) {
    ObjectSubclassInfo info = null;

    synchronized (mutex) {
      info = registeredSubclasses.get(className);
    }

    return info == null
      ? clazz == ParseObject.class
      : info.type == clazz;
  }

  /* package */ void registerSubclass(Class<? extends ParseObject> clazz) {
    if (!ParseObject.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException("Cannot register a type that is not a subclass of ParseObject");
    }

    String className = getClassName(clazz);
    ObjectSubclassInfo previousInfo = null;

    synchronized (mutex) {
      previousInfo = registeredSubclasses.get(className);
      if (previousInfo != null) {
        if (clazz.isAssignableFrom(previousInfo.type)) {
          // Previous subclass is more specific or equal to the current type, do nothing.
          return;
        } else if (previousInfo.type.isAssignableFrom(clazz)) {
          // Previous subclass is parent of new child subclass, fallthrough and actually
          // register this class.
          /* Do nothing */
        } else {
          throw new IllegalArgumentException(
            "Tried to register both " + previousInfo.type.getName() + " and " +
             clazz.getName() + " as the ParseObject subclass of " + className + ". " +
             "Cannot determine the right class to use because netiher inherits from the other."
          );
        }
      }

      try {
        registeredSubclasses.put(className, new ObjectSubclassInfo(clazz));
      } catch (NoSuchMethodException ex) {
        throw new IllegalArgumentException(
          "Cannot register a type that does not implement the default constructor!"
        );
      } catch (IllegalAccessException ex) {
        throw new IllegalArgumentException(
          "Default constructor for " + clazz + " is not accessible."
        );
      }
    }

    if (previousInfo != null) {
      // TODO: This is super tightly coupled. Let's remove it when automatic registration is in.
      // NOTE: Perform this outside of the mutex, to prevent any potential deadlocks.
      if (className.equals(getClassName(ParseUser.class))) {
        ParseUser.getCurrentUserController().clearFromMemory();
      } else if (className.equals(getClassName(ParseInstallation.class))) {
        ParseInstallation.getCurrentInstallationController().clearFromMemory();
      }
    }
  }

  /* package */ void unregisterSubclass(Class<? extends ParseObject> clazz) {
    String className = getClassName(clazz);

    synchronized (mutex) {
      registeredSubclasses.remove(className);
    }
  }

  /* package */ ParseObject newInstance(String className) {
    ObjectSubclassInfo info = null;

    synchronized (mutex) {
      info = registeredSubclasses.get(className);
    }

    try {
      return info != null
        ? info.constructor.newInstance()
        : new ParseObject(className);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to create instance of subclass.", e);
    }
  }
}
