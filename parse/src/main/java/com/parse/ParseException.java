/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

/**
 * A ParseException gets raised whenever a {@link ParseObject} issues an invalid request, such as
 * deleting or editing an object that no longer exists on the server, or when there is a network
 * failure preventing communication with the Parse server.
 */
public class ParseException extends Exception {
  private static final long serialVersionUID = 1;
  private int code;

  public static final int OTHER_CAUSE = -1;

  /**
   * Error code indicating the connection to the Parse servers failed.
   */
  public static final int CONNECTION_FAILED = 100;

  /**
   * Error code indicating the specified object doesn't exist.
   */
  public static final int OBJECT_NOT_FOUND = 101;

  /**
   * Error code indicating you tried to query with a datatype that doesn't support it, like exact
   * matching an array or object.
   */
  public static final int INVALID_QUERY = 102;

  /**
   * Error code indicating a missing or invalid classname. Classnames are case-sensitive. They must
   * start with a letter, and a-zA-Z0-9_ are the only valid characters.
   */
  public static final int INVALID_CLASS_NAME = 103;

  /**
   * Error code indicating an unspecified object id.
   */
  public static final int MISSING_OBJECT_ID = 104;

  /**
   * Error code indicating an invalid key name. Keys are case-sensitive. They must start with a
   * letter, and a-zA-Z0-9_ are the only valid characters.
   */
  public static final int INVALID_KEY_NAME = 105;

  /**
   * Error code indicating a malformed pointer. You should not see this unless you have been mucking
   * about changing internal Parse code.
   */
  public static final int INVALID_POINTER = 106;

  /**
   * Error code indicating that badly formed JSON was received upstream. This either indicates you
   * have done something unusual with modifying how things encode to JSON, or the network is failing
   * badly.
   */
  public static final int INVALID_JSON = 107;

  /**
   * Error code indicating that the feature you tried to access is only available internally for
   * testing purposes.
   */
  public static final int COMMAND_UNAVAILABLE = 108;

  /**
   * You must call Parse.initialize before using the Parse library.
   */
  public static final int NOT_INITIALIZED = 109;

  /**
   * Error code indicating that a field was set to an inconsistent type.
   */
  public static final int INCORRECT_TYPE = 111;

  /**
   * Error code indicating an invalid channel name. A channel name is either an empty string (the
   * broadcast channel) or contains only a-zA-Z0-9_ characters and starts with a letter.
   */
  public static final int INVALID_CHANNEL_NAME = 112;

  /**
   * Error code indicating that push is misconfigured.
   */
  public static final int PUSH_MISCONFIGURED = 115;

  /**
   * Error code indicating that the object is too large.
   */
  public static final int OBJECT_TOO_LARGE = 116;

  /**
   * Error code indicating that the operation isn't allowed for clients.
   */
  public static final int OPERATION_FORBIDDEN = 119;

  /**
   * Error code indicating the result was not found in the cache.
   */
  public static final int CACHE_MISS = 120;

  /**
   * Error code indicating that an invalid key was used in a nested JSONObject.
   */
  public static final int INVALID_NESTED_KEY = 121;

  /**
   * Error code indicating that an invalid filename was used for ParseFile. A valid file name
   * contains only a-zA-Z0-9_. characters and is between 1 and 128 characters.
   */
  public static final int INVALID_FILE_NAME = 122;

  /**
   * Error code indicating an invalid ACL was provided.
   */
  public static final int INVALID_ACL = 123;

  /**
   * Error code indicating that the request timed out on the server. Typically this indicates that
   * the request is too expensive to run.
   */
  public static final int TIMEOUT = 124;

  /**
   * Error code indicating that the email address was invalid.
   */
  public static final int INVALID_EMAIL_ADDRESS = 125;

  /**
   * Error code indicating that required field is missing.
   */
  public static final int MISSING_REQUIRED_FIELD_ERROR = 135;

  /**
   * Error code indicating that a unique field was given a value that is already taken.
   */
  public static final int DUPLICATE_VALUE = 137;

  /**
   * Error code indicating that a role's name is invalid.
   */
  public static final int INVALID_ROLE_NAME = 139;

  /**
   * Error code indicating that an application quota was exceeded. Upgrade to resolve.
   */
  public static final int EXCEEDED_QUOTA = 140;
  /**
   * Error code indicating that a Cloud Code script failed.
   */
  public static final int SCRIPT_ERROR = 141;
  /**
   * Error code indicating that cloud code validation failed.
   */
  public static final int VALIDATION_ERROR = 142;

  /**
   * Error code indicating that deleting a file failed.
   */
  public static final int FILE_DELETE_ERROR = 153;

  /**
   * Error code indicating that the application has exceeded its request limit.
   */
  public static final int REQUEST_LIMIT_EXCEEDED = 155;

  /**
   * Error code indicating that the provided event name is invalid.
   */
  public static final int INVALID_EVENT_NAME = 160;

  /**
   * Error code indicating that the username is missing or empty.
   */
  public static final int USERNAME_MISSING = 200;

  /**
   * Error code indicating that the password is missing or empty.
   */
  public static final int PASSWORD_MISSING = 201;

  /**
   * Error code indicating that the username has already been taken.
   */
  public static final int USERNAME_TAKEN = 202;

  /**
   * Error code indicating that the email has already been taken.
   */
  public static final int EMAIL_TAKEN = 203;

  /**
   * Error code indicating that the email is missing, but must be specified.
   */
  public static final int EMAIL_MISSING = 204;

  /**
   * Error code indicating that a user with the specified email was not found.
   */
  public static final int EMAIL_NOT_FOUND = 205;

  /**
   * Error code indicating that a user object without a valid session could not be altered.
   */
  public static final int SESSION_MISSING = 206;

  /**
   * Error code indicating that a user can only be created through signup.
   */
  public static final int MUST_CREATE_USER_THROUGH_SIGNUP = 207;

  /**
   * Error code indicating that an an account being linked is already linked to another user.
   */
  public static final int ACCOUNT_ALREADY_LINKED = 208;

  /**
   * Error code indicating that the current session token is invalid.
   */
  public static final int INVALID_SESSION_TOKEN = 209;

  /**
   * Error code indicating that a user cannot be linked to an account because that account's id
   * could not be found.
   */
  public static final int LINKED_ID_MISSING = 250;

  /**
   * Error code indicating that a user with a linked (e.g. Facebook) account has an invalid session.
   */
  public static final int INVALID_LINKED_SESSION = 251;

  /**
   * Error code indicating that a service being linked (e.g. Facebook or Twitter) is unsupported.
   */
  public static final int UNSUPPORTED_SERVICE = 252;

  /**
   * Construct a new ParseException with a particular error code.
   * 
   * @param theCode
   *          The error code to identify the type of exception.
   * @param theMessage
   *          A message describing the error in more detail.
   */
  public ParseException(int theCode, String theMessage) {
    super(theMessage);
    code = theCode;
  }

  /**
   * Construct a new ParseException with an external cause.
   * 
   * @param message
   *          A message describing the error in more detail.
   * @param cause
   *          The cause of the error.
   */
  public ParseException(int theCode, String message, Throwable cause) {
    super(message, cause);
    code = theCode;
  }

  /**
   * Construct a new ParseException with an external cause.
   * 
   * @param cause
   *          The cause of the error.
   */
  public ParseException(Throwable cause) {
    super(cause);
    code = OTHER_CAUSE;
  }

  /**
   * Access the code for this error.
   * 
   * @return The numerical code for this error.
   */
  public int getCode() {
    return code;
  }
}
