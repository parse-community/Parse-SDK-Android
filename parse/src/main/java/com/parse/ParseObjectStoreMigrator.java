package com.parse;

import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;

import java.util.Arrays;

/**
 * Use this utility class to migrate from one {@link ParseObjectStore} to another
 */
class ParseObjectStoreMigrator<T extends ParseObject> implements ParseObjectStore<T> {

    private final ParseObjectStore<T> store;
    private final ParseObjectStore<T> legacy;

    /**
     * @param store  the new {@link ParseObjectStore} to migrate to
     * @param legacy the old {@link ParseObjectStore} to migrate from
     */
    public ParseObjectStoreMigrator(ParseObjectStore<T> store, ParseObjectStore<T> legacy) {
        this.store = store;
        this.legacy = legacy;
    }

    @Override
    public Task<T> getAsync() {
        return store.getAsync().continueWithTask(new Continuation<T, Task<T>>() {
            @Override
            public Task<T> then(Task<T> task) throws Exception {
                if (task.getResult() != null) return task;
                return legacy.getAsync().continueWithTask(new Continuation<T, Task<T>>() {
                    @Override
                    public Task<T> then(Task<T> task) throws Exception {
                        T object = task.getResult();
                        if (object == null) return task;
                        return legacy.deleteAsync().continueWith(task1 -> ParseTaskUtils.wait(store.setAsync(object))).onSuccess(task1 -> object);
                    }
                });
            }
        });
    }

    @Override
    public Task<Void> setAsync(T object) {
        return store.setAsync(object);
    }

    @Override
    public Task<Boolean> existsAsync() {
        return store.existsAsync().continueWithTask(new Continuation<Boolean, Task<Boolean>>() {
            @Override
            public Task<Boolean> then(Task<Boolean> task) throws Exception {
                if (task.getResult()) return Task.forResult(true);
                return legacy.existsAsync();
            }
        });
    }

    @Override
    public Task<Void> deleteAsync() {
        Task<Void> storeTask = store.deleteAsync();
        return Task.whenAll(Arrays.asList(legacy.deleteAsync(), storeTask)).continueWithTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task1) throws Exception {
                return storeTask;
            }
        });
    }
}
