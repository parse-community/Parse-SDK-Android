/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONObject;

import java.util.List;

import bolts.Task;

/** package */ interface ParseObjectController {

  Task<ParseObject.State> fetchAsync(
      ParseObject.State state, String sessionToken, ParseDecoder decoder);

  Task<ParseObject.State> saveAsync(
      ParseObject.State state,
      ParseOperationSet operations,
      String sessionToken,
      ParseDecoder decoder);

  List<Task<ParseObject.State>> saveAllAsync(
      List<ParseObject.State> states,
      List<ParseOperationSet> operationsList,
      String sessionToken,
      List<ParseDecoder> decoders);

  Task<Void> deleteAsync(ParseObject.State state, String sessionToken);

  List<Task<Void>> deleteAllAsync(List<ParseObject.State> states, String sessionToken);
}
