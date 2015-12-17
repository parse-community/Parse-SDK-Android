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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import bolts.Task;

/** package */ class ParseCurrentConfigController {

  private final Object currentConfigMutex = new Object();
  /* package for test */ ParseConfig currentConfig;
  private File currentConfigFile;

  public ParseCurrentConfigController(File currentConfigFile) {
    this.currentConfigFile = currentConfigFile;
  }

  public Task<Void> setCurrentConfigAsync(final ParseConfig config) {
    return Task.call(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        synchronized (currentConfigMutex) {
          currentConfig = config;
          saveToDisk(config);
        }
        return null;
      }
    }, ParseExecutors.io());
  }

  public Task<ParseConfig> getCurrentConfigAsync() {
    return Task.call(new Callable<ParseConfig>() {
      @Override
      public ParseConfig call() throws Exception {
        synchronized (currentConfigMutex) {
          if (currentConfig == null) {
            ParseConfig config = getFromDisk();
            currentConfig = (config != null) ? config : new ParseConfig();
          }
        }
        return currentConfig;
      }
    }, ParseExecutors.io());
  }

  /**
   * Retrieves a {@code ParseConfig} from a file on disk.
   *
   * @return The {@code ParseConfig} that was retrieved. If the file wasn't found, or the contents
   *          of the file is an invalid {@code ParseConfig}, returns null.
   */
  /* package for test */ ParseConfig getFromDisk() {
    JSONObject json;
    try {
      json = ParseFileUtils.readFileToJSONObject(currentConfigFile);
    } catch (IOException | JSONException e) {
      return null;
    }
    return ParseConfig.decode(json, ParseDecoder.get());
  }

  /* package */ void clearCurrentConfigForTesting() {
    synchronized (currentConfigMutex) {
      currentConfig = null;
    }
  }

  /**
   * Saves the {@code ParseConfig} to the a file on disk as JSON.
   *
   * @param config
   *          The ParseConfig which needs to be saved.
   */
  /* package for test */ void saveToDisk(ParseConfig config) {
    JSONObject object = new JSONObject();
    try {
      JSONObject jsonParams = (JSONObject) NoObjectsEncoder.get().encode(config.getParams());
      object.put("params", jsonParams);
    } catch (JSONException e) {
      throw new RuntimeException("could not serialize config to JSON");
    }
    try {
      ParseFileUtils.writeJSONObjectToFile(currentConfigFile, object);
    } catch (IOException e) {
      //TODO (grantland): We should do something if this fails...
    }
  }
}
