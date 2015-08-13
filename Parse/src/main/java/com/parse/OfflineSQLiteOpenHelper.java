/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

/**
 * This class just wraps a SQLiteDatabase with a better API. SQLite has a few limitations that this
 * class works around. The primary problem is that if you call getWritableDatabase from multiple
 * places, they all return the same instance, so you can't call "close" until you are done with all
 * of them. SQLite also doesn't allow multiple transactions at the same time. We don't need
 * transactions yet, but when we do, they will be part of this class. For convenience, this class
 * also wraps database methods with methods that run them on a background thread and return a task.
 */
/** package */ class OfflineSQLiteOpenHelper extends ParseSQLiteOpenHelper {

  /**
   * The table that stores all ParseObjects.
   */
  /* package */ static final String TABLE_OBJECTS = "ParseObjects";

  /**
   * Various keys in the table of ParseObjects.
   */
  /* package */ /* package */ static final String KEY_UUID = "uuid";
  /* package */ static final String KEY_CLASS_NAME = "className";
  /* package */ static final String KEY_OBJECT_ID = "objectId";
  /* package */ static final String KEY_JSON = "json";
  /* package */ static final String KEY_IS_DELETING_EVENTUALLY = "isDeletingEventually";

  /**
   * The table that stores all Dependencies.
   */
  /* package */ static final String TABLE_DEPENDENCIES = "Dependencies";

  /**
   * Various keys in the table of Dependencies.
   */
  //TODO (grantland): rename this since we use UUIDs as keys now. root_uuid?
  /* package */ static final String KEY_KEY = "key";
  // static final String KEY_UUID = "uuid";

  /**
   * The SQLite Database name.
   */
  private static final String DATABASE_NAME = "ParseOfflineStore";
  private static final int DATABASE_VERSION = 4;

  /**
   * Creates a new helper for the database.
   */
  public OfflineSQLiteOpenHelper(Context context) {
    super(context, DATABASE_NAME, null, DATABASE_VERSION);
  }

  /**
   * Initializes the schema for the database.
   */
  private void createSchema(SQLiteDatabase db) {
    String sql;

    sql = "CREATE TABLE " + TABLE_OBJECTS + " (" +
        KEY_UUID + " TEXT PRIMARY KEY, " +
        KEY_CLASS_NAME + " TEXT NOT NULL, " +
        KEY_OBJECT_ID + " TEXT, " +
        KEY_JSON + " TEXT, " +
        KEY_IS_DELETING_EVENTUALLY + " INTEGER DEFAULT 0, " +
        "UNIQUE(" + KEY_CLASS_NAME + ", " + KEY_OBJECT_ID + ")" +
        ");";
    db.execSQL(sql);

    sql = "CREATE TABLE " + TABLE_DEPENDENCIES + " (" +
        KEY_KEY + " TEXT NOT NULL, " +
        KEY_UUID + " TEXT NOT NULL, " +
        "PRIMARY KEY(" + KEY_KEY + ", " + KEY_UUID + ")" +
        ");";
    db.execSQL(sql);
  }

  /**
   * Called when the database is first created.
   */
  @Override
  public void onCreate(SQLiteDatabase db) {
    createSchema(db);
  }

  /**
   * Called when the version number in code doesn't match the one on disk.
   */
  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    // do nothing
  }

  /**
   * Drops all tables and then recreates the schema.
   */
  public void clearDatabase(Context context) {
    context.deleteDatabase(DATABASE_NAME);
  }
}
