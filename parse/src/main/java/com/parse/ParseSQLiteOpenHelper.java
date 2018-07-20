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
import android.database.sqlite.SQLiteOpenHelper;

import bolts.Task;

abstract class ParseSQLiteOpenHelper {

    private final SQLiteOpenHelper helper;

    public ParseSQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory,
                                 int version) {
        helper = new SQLiteOpenHelper(context, name, factory, version) {
            @Override
            public void onOpen(SQLiteDatabase db) {
                super.onOpen(db);
                ParseSQLiteOpenHelper.this.onOpen(db);
            }

            @Override
            public void onCreate(SQLiteDatabase db) {
                ParseSQLiteOpenHelper.this.onCreate(db);
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                ParseSQLiteOpenHelper.this.onUpgrade(db, oldVersion, newVersion);
            }
        };
    }

    public Task<ParseSQLiteDatabase> getReadableDatabaseAsync() {
        return getDatabaseAsync(false);
    }

    public Task<ParseSQLiteDatabase> getWritableDatabaseAsync() {
        return getDatabaseAsync(true);
    }

    private Task<ParseSQLiteDatabase> getDatabaseAsync(final boolean writable) {
        return ParseSQLiteDatabase.openDatabaseAsync(
                helper, !writable ? SQLiteDatabase.OPEN_READONLY : SQLiteDatabase.OPEN_READWRITE);
    }

    public void onOpen(SQLiteDatabase db) {
        // do nothing
    }

    public abstract void onCreate(SQLiteDatabase db);

    public abstract void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);
}
