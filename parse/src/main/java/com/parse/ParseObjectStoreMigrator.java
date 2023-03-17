package com.parse;

import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;

import java.util.Arrays;

public class ParseObjectStoreMigrator<T extends ParseObject> implements ParseObjectStore<T> {

    private ParseObjectStore<T> store;
    private ParseObjectStore<T> legacy;

    public ParseObjectStoreMigrator(ParseObjectStore<T> store, ParseObjectStore<T> legacy) {
        this.store = store;
        this.legacy = legacy;
    }

    private static <T extends ParseObject> Task<T> migrate(
        final ParseObjectStore<T> from, final ParseObjectStore<T> to) {
        return from.getAsync()
            .onSuccessTask(
                task -> {
                    final T object = task.getResult();
                    if (object == null) {
                        return task;
                    }

                    return Task.whenAll(
                            Arrays.asList(from.deleteAsync(), to.setAsync(object)))
                        .continueWith(task1 -> object);
                });
    }

    @Override
    public Task<T> getAsync() {
        return store.getAsync().continueWithTask(new Continuation<T, Task<T>>() {
            @Override
            public Task<T> then(Task<T> task) throws Exception {
                if (task.getResult() != null) return task;
                return migrate(legacy, store);
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
