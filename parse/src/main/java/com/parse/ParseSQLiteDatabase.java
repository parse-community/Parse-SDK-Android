/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.parse.boltsinternal.Task;
import com.parse.boltsinternal.TaskCompletionSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class ParseSQLiteDatabase {

    /**
     * Database connections are locked to the thread that they are created in when using
     * transactions. We must use a single thread executor to make sure that all transactional DB
     * actions are on the same thread or else they will block.
     *
     * <p>Symptoms include blocking on db.query, cursor.moveToFirst, etc.
     */
    private static final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    /**
     * Queue for all database sessions. All database sessions must be serialized in order for
     * transactions to work correctly.
     */
    // TODO (grantland): do we have to serialize sessions of different databases?
    private static final TaskQueue taskQueue = new TaskQueue();

    private final Object currentLock = new Object();
    private final TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
    private final int openFlags;
    private SQLiteDatabase db;
    private Task<Void> current = null;

    /** Creates a Session which opens a database connection and begins a transaction */
    private ParseSQLiteDatabase(int flags) {
        // TODO (grantland): if (!writable) -- disable transactions?
        // TODO (grantland): if (!writable) -- do we have to serialize everything?
        openFlags = flags;

        taskQueue.enqueue(
                toAwait -> {
                    synchronized (currentLock) {
                        current = toAwait;
                    }
                    return tcs.getTask();
                });
    }

    /* protected */
    static Task<ParseSQLiteDatabase> openDatabaseAsync(final SQLiteOpenHelper helper, int flags) {
        final ParseSQLiteDatabase db = new ParseSQLiteDatabase(flags);
        return db.open(helper).continueWithTask(task -> Task.forResult(db));
    }

    public Task<Boolean> isReadOnlyAsync() {
        synchronized (currentLock) {
            Task<Boolean> task = current.continueWith(task1 -> db.isReadOnly());
            current = task.makeVoid();
            return task;
        }
    }

    public Task<Boolean> isOpenAsync() {
        synchronized (currentLock) {
            Task<Boolean> task = current.continueWith(task1 -> db.isOpen());
            current = task.makeVoid();
            return task;
        }
    }

    public boolean inTransaction() {
        return db.inTransaction();
    }

    /* package */ Task<Void> open(final SQLiteOpenHelper helper) {
        synchronized (currentLock) {
            current =
                    current.continueWith(
                                    task -> {
                                        // get*Database() is synchronous and calls through
                                        // SQLiteOpenHelper#onCreate, onUpdate,
                                        // etc.
                                        return (openFlags & SQLiteDatabase.OPEN_READONLY)
                                                        == SQLiteDatabase.OPEN_READONLY
                                                ? helper.getReadableDatabase()
                                                : helper.getWritableDatabase();
                                    },
                                    dbExecutor)
                            .continueWithTask(
                                    task -> {
                                        db = task.getResult();
                                        return task.makeVoid();
                                    },
                                    Task.BACKGROUND_EXECUTOR); // We want to jump off the dbExecutor
            return current;
        }
    }

    /**
     * Executes a BEGIN TRANSACTION.
     *
     * @see SQLiteDatabase#beginTransaction
     */
    public Task<Void> beginTransactionAsync() {
        synchronized (currentLock) {
            current =
                    current.continueWithTask(
                            task -> {
                                db.beginTransaction();
                                return task;
                            },
                            dbExecutor);
            return current.continueWithTask(
                    task -> {
                        // We want to jump off the dbExecutor
                        return task;
                    },
                    Task.BACKGROUND_EXECUTOR);
        }
    }

    /**
     * Sets a transaction as successful.
     *
     * @see SQLiteDatabase#setTransactionSuccessful
     */
    public Task<Void> setTransactionSuccessfulAsync() {
        synchronized (currentLock) {
            current =
                    current.onSuccessTask(
                            task -> {
                                db.setTransactionSuccessful();
                                return task;
                            },
                            dbExecutor);
            return current.continueWithTask(
                    task -> {
                        // We want to jump off the dbExecutor
                        return task;
                    },
                    Task.BACKGROUND_EXECUTOR);
        }
    }

    /**
     * Ends a transaction.
     *
     * @see SQLiteDatabase#endTransaction
     */
    public Task<Void> endTransactionAsync() {
        synchronized (currentLock) {
            current =
                    current.continueWith(
                            task -> {
                                db.endTransaction();
                                // We want to swallow any exceptions from our Session task
                                return null;
                            },
                            dbExecutor);
            return current.continueWithTask(
                    task -> {
                        // We want to jump off the dbExecutor
                        return task;
                    },
                    Task.BACKGROUND_EXECUTOR);
        }
    }

    /**
     * Closes this session, sets the transaction as successful if no errors occurred, ends the
     * transaction and closes the database connection.
     */
    public Task<Void> closeAsync() {
        synchronized (currentLock) {
            current =
                    current.continueWithTask(
                            task -> {
                                try {
                                    db.close();
                                } finally {
                                    tcs.setResult(null);
                                }
                                return tcs.getTask();
                            },
                            dbExecutor);
            return current.continueWithTask(
                    task -> {
                        // We want to jump off the dbExecutor
                        return task;
                    },
                    Task.BACKGROUND_EXECUTOR);
        }
    }

    /**
     * Runs a SELECT query.
     *
     * @see SQLiteDatabase#query
     */
    public Task<Cursor> queryAsync(
            final String table, final String[] select, final String where, final String[] args) {
        synchronized (currentLock) {
            Task<Cursor> task =
                    current.onSuccess(
                                    task13 ->
                                            db.query(table, select, where, args, null, null, null),
                                    dbExecutor)
                            .onSuccess(
                                    task12 -> {
                                        Cursor cursor = task12.getResult();
                                        /* Ensure the cursor window is filled on the dbExecutor thread. We need to do this because
                                         * the cursor cannot be filled from a different thread than it was created on.
                                         */
                                        cursor.getCount();
                                        return cursor;
                                    },
                                    dbExecutor);
            current = task.makeVoid();
            return task.continueWithTask(
                    task1 -> {
                        // We want to jump off the dbExecutor
                        return task1;
                    },
                    Task.BACKGROUND_EXECUTOR);
        }
    }

    /**
     * Executes an INSERT.
     *
     * @see SQLiteDatabase#insertWithOnConflict
     */
    public Task<Void> insertWithOnConflict(
            final String table, final ContentValues values, final int conflictAlgorithm) {
        synchronized (currentLock) {
            Task<Long> task =
                    current.onSuccess(
                            task12 ->
                                    db.insertWithOnConflict(table, null, values, conflictAlgorithm),
                            dbExecutor);
            current = task.makeVoid();
            return task.continueWithTask(
                            task1 -> {
                                // We want to jump off the dbExecutor
                                return task1;
                            },
                            Task.BACKGROUND_EXECUTOR)
                    .makeVoid();
        }
    }

    /**
     * Executes an INSERT and throws on SQL errors.
     *
     * @see SQLiteDatabase#insertOrThrow
     */
    public Task<Void> insertOrThrowAsync(final String table, final ContentValues values) {
        synchronized (currentLock) {
            Task<Long> task =
                    current.onSuccess(task12 -> db.insertOrThrow(table, null, values), dbExecutor);
            current = task.makeVoid();
            return task.continueWithTask(
                            task1 -> {
                                // We want to jump off the dbExecutor
                                return task1;
                            },
                            Task.BACKGROUND_EXECUTOR)
                    .makeVoid();
        }
    }

    /**
     * Executes an UPDATE.
     *
     * @see SQLiteDatabase#update
     */
    public Task<Integer> updateAsync(
            final String table,
            final ContentValues values,
            final String where,
            final String[] args) {
        synchronized (currentLock) {
            Task<Integer> task =
                    current.onSuccess(task12 -> db.update(table, values, where, args), dbExecutor);
            current = task.makeVoid();
            return task.continueWithTask(
                    task1 -> {
                        // We want to jump off the dbExecutor
                        return task1;
                    },
                    Task.BACKGROUND_EXECUTOR);
        }
    }

    /**
     * Executes a DELETE.
     *
     * @see SQLiteDatabase#delete
     */
    public Task<Void> deleteAsync(final String table, final String where, final String[] args) {
        synchronized (currentLock) {
            Task<Integer> task =
                    current.onSuccess(task12 -> db.delete(table, where, args), dbExecutor);
            current = task.makeVoid();
            return task.continueWithTask(
                            task1 -> {
                                // We want to jump off the dbExecutor
                                return task1;
                            },
                            Task.BACKGROUND_EXECUTOR)
                    .makeVoid();
        }
    }

    /**
     * Runs a raw query.
     *
     * @see SQLiteDatabase#rawQuery
     */
    public Task<Cursor> rawQueryAsync(final String sql, final String[] args) {
        synchronized (currentLock) {
            Task<Cursor> task =
                    current.onSuccess(task13 -> db.rawQuery(sql, args), dbExecutor)
                            .onSuccess(
                                    task12 -> {
                                        Cursor cursor = task12.getResult();
                                        // Ensure the cursor window is filled on the dbExecutor
                                        // thread. We need to do this because
                                        // the cursor cannot be filled from a different thread than
                                        // it was created on.
                                        cursor.getCount();
                                        return cursor;
                                    },
                                    dbExecutor);
            current = task.makeVoid();
            return task.continueWithTask(
                    task1 -> {
                        // We want to jump off the dbExecutor
                        return task1;
                    },
                    Task.BACKGROUND_EXECUTOR);
        }
    }
}
