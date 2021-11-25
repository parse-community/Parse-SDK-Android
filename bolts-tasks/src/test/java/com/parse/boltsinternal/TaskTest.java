/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package com.parse.boltsinternal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TaskTest {

    @Rule public final ExpectedException thrown = ExpectedException.none();

    private void runTaskTest(Callable<Task<?>> callable) {
        try {
            Task<?> task = callable.call();
            task.waitForCompletion();
            if (task.isFaulted()) {
                Exception error = task.getError();
                if (error instanceof RuntimeException) {
                    throw (RuntimeException) error;
                }
                throw new RuntimeException(error);
            } else if (task.isCancelled()) {
                throw new RuntimeException(new CancellationException());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testCache() {
        assertSame(Task.forResult(null), Task.forResult(null));
        Task<Boolean> trueTask = Task.forResult(true);
        assertTrue(trueTask.getResult());
        assertSame(trueTask, Task.forResult(true));
        Task<Boolean> falseTask = Task.forResult(false);
        assertFalse(falseTask.getResult());
        assertSame(falseTask, Task.forResult(false));
        assertSame(Task.cancelled(), Task.cancelled());
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    @Test
    public void testPrimitives() {
        Task<Integer> complete = Task.forResult(5);
        Task<Integer> error = Task.forError(new RuntimeException());
        Task<Integer> cancelled = Task.cancelled();

        assertTrue(complete.isCompleted());
        assertEquals(5, complete.getResult().intValue());
        assertFalse(complete.isFaulted());
        assertFalse(complete.isCancelled());

        assertTrue(error.isCompleted());
        assertTrue(error.getError() instanceof RuntimeException);
        assertTrue(error.isFaulted());
        assertFalse(error.isCancelled());

        assertTrue(cancelled.isCompleted());
        assertFalse(cancelled.isFaulted());
        assertTrue(cancelled.isCancelled());
    }

    @Test
    public void testDelay() throws InterruptedException {
        final Task<Void> delayed = Task.delay(200);
        Thread.sleep(50);
        assertFalse(delayed.isCompleted());
        Thread.sleep(200);
        assertTrue(delayed.isCompleted());
        assertFalse(delayed.isFaulted());
        assertFalse(delayed.isCancelled());
    }

    @Test
    public void testDelayWithCancelledToken() throws InterruptedException {
        CancellationTokenSource cts = new CancellationTokenSource();
        cts.cancel();
        final Task<Void> delayed = Task.delay(200, cts.getToken());
        assertTrue(delayed.isCancelled());
    }

    @Test
    public void testDelayWithToken() throws InterruptedException {
        CancellationTokenSource cts = new CancellationTokenSource();
        final Task<Void> delayed = Task.delay(200, cts.getToken());
        assertFalse(delayed.isCancelled());
        cts.cancel();
        assertTrue(delayed.isCancelled());
    }

    @Test
    public void testSynchronousContinuation() {
        final Task<Integer> complete = Task.forResult(5);
        final Task<Integer> error = Task.forError(new RuntimeException());
        final Task<Integer> cancelled = Task.cancelled();

        complete.continueWith(
                (Continuation<Integer, Void>)
                        task -> {
                            assertEquals(complete, task);
                            assertTrue(task.isCompleted());
                            assertEquals(5, task.getResult().intValue());
                            assertFalse(task.isFaulted());
                            assertFalse(task.isCancelled());
                            return null;
                        });

        error.continueWith(
                (Continuation<Integer, Void>)
                        task -> {
                            assertEquals(error, task);
                            assertTrue(task.isCompleted());
                            assertTrue(task.getError() instanceof RuntimeException);
                            assertTrue(task.isFaulted());
                            assertFalse(task.isCancelled());
                            return null;
                        });

        cancelled.continueWith(
                (Continuation<Integer, Void>)
                        task -> {
                            assertEquals(cancelled, task);
                            assertTrue(cancelled.isCompleted());
                            assertFalse(cancelled.isFaulted());
                            assertTrue(cancelled.isCancelled());
                            return null;
                        });
    }

    @Test
    public void testSynchronousChaining() {
        Task<Integer> first = Task.forResult(1);
        Task<Integer> second = first.continueWith(task -> 2);
        Task<Integer> third = second.continueWithTask(task -> Task.forResult(3));
        assertTrue(first.isCompleted());
        assertTrue(second.isCompleted());
        assertTrue(third.isCompleted());
        assertEquals(1, first.getResult().intValue());
        assertEquals(2, second.getResult().intValue());
        assertEquals(3, third.getResult().intValue());
    }

    @Test
    public void testSynchronousCancellation() {
        Task<Integer> first = Task.forResult(1);
        Task<Integer> second =
                first.continueWith(
                        task -> {
                            throw new CancellationException();
                        });
        assertTrue(first.isCompleted());
        assertTrue(second.isCancelled());
    }

    @Test
    public void testSynchronousContinuationTokenAlreadyCancelled() {
        CancellationTokenSource cts = new CancellationTokenSource();
        final Capture<Boolean> continuationRun = new Capture<>(false);
        cts.cancel();
        Task<Integer> first = Task.forResult(1);
        Task<Integer> second =
                first.continueWith(
                        task -> {
                            continuationRun.set(true);
                            return 2;
                        },
                        cts.getToken());
        assertTrue(first.isCompleted());
        assertTrue(second.isCancelled());
        assertFalse(continuationRun.get());
    }

    @Test
    public void testSynchronousTaskCancellation() {
        Task<Integer> first = Task.forResult(1);
        Task<Integer> second =
                first.continueWithTask(
                        task -> {
                            throw new CancellationException();
                        });
        assertTrue(first.isCompleted());
        assertTrue(second.isCancelled());
    }

    @Test
    public void testBackgroundCall() {
        runTaskTest(
                () ->
                        Task.callInBackground(
                                        () -> {
                                            Thread.sleep(100);
                                            return 5;
                                        })
                                .continueWith(
                                        (Continuation<Integer, Void>)
                                                task -> {
                                                    assertEquals(5, task.getResult().intValue());
                                                    return null;
                                                }));
    }

    @Test
    public void testBackgroundCallTokenCancellation() {
        final CancellationTokenSource cts = new CancellationTokenSource();
        final CancellationToken ct = cts.getToken();
        final Capture<Boolean> waitingToBeCancelled = new Capture<>(false);
        final Object cancelLock = new Object();

        Task<Integer> task =
                Task.callInBackground(
                        () -> {
                            synchronized (cancelLock) {
                                waitingToBeCancelled.set(true);
                                cancelLock.wait();
                            }
                            ct.throwIfCancellationRequested();
                            return 5;
                        });

        while (true) {
            synchronized (cancelLock) {
                if (waitingToBeCancelled.get()) {
                    cts.cancel();
                    cancelLock.notify();
                    break;
                }
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            task.waitForCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        assertTrue(task.isCancelled());
    }

    @Test
    public void testBackgroundCallTokenAlreadyCancelled() {
        final CancellationTokenSource cts = new CancellationTokenSource();

        cts.cancel();
        runTaskTest(
                () ->
                        Task.callInBackground(
                                        () -> {
                                            Thread.sleep(100);
                                            return 5;
                                        },
                                        cts.getToken())
                                .continueWith(
                                        (Continuation<Integer, Void>)
                                                task -> {
                                                    assertTrue(task.isCancelled());
                                                    return null;
                                                }));
    }

    @Test
    public void testBackgroundCallWaiting() throws Exception {
        Task<Integer> task =
                Task.callInBackground(
                        () -> {
                            Thread.sleep(100);
                            return 5;
                        });
        task.waitForCompletion();
        assertTrue(task.isCompleted());
        assertEquals(5, task.getResult().intValue());
    }

    @Test
    public void testBackgroundCallWaitingWithTimeouts() throws Exception {
        final Object sync = new Object();

        Task<Integer> task =
                Task.callInBackground(
                        () -> {
                            synchronized (sync) {
                                sync.wait();
                                Thread.sleep(100);
                            }
                            return 5;
                        });
        // wait -> timeout
        assertFalse(task.waitForCompletion(100, TimeUnit.MILLISECONDS));
        synchronized (sync) {
            sync.notify();
        }
        // wait -> completes
        assertTrue(task.waitForCompletion(1000, TimeUnit.MILLISECONDS));
        // wait -> already completed
        assertTrue(task.waitForCompletion(100, TimeUnit.MILLISECONDS));
        assertEquals(5, task.getResult().intValue());
    }

    @Test
    public void testBackgroundCallWaitingOnError() throws Exception {
        Task<Integer> task =
                Task.callInBackground(
                        () -> {
                            Thread.sleep(100);
                            throw new RuntimeException();
                        });
        task.waitForCompletion();
        assertTrue(task.isCompleted());
        assertTrue(task.isFaulted());
    }

    @Test
    public void testBackgroundCallWaitOnCancellation() throws Exception {
        Task<Integer> task =
                Task.callInBackground(
                                () -> {
                                    Thread.sleep(100);
                                    return 5;
                                })
                        .continueWithTask(task1 -> Task.cancelled());
        task.waitForCompletion();
        assertTrue(task.isCompleted());
        assertTrue(task.isCancelled());
    }

    @Test
    public void testBackgroundError() {
        runTaskTest(
                () ->
                        Task.callInBackground(
                                        (Callable<Integer>)
                                                () -> {
                                                    throw new IllegalStateException();
                                                })
                                .continueWith(
                                        (Continuation<Integer, Void>)
                                                task -> {
                                                    assertTrue(task.isFaulted());
                                                    assertTrue(
                                                            task.getError()
                                                                    instanceof
                                                                    IllegalStateException);
                                                    return null;
                                                }));
    }

    @Test
    public void testBackgroundCancellation() {
        runTaskTest(
                () ->
                        Task.callInBackground(
                                        (Callable<Void>)
                                                () -> {
                                                    throw new CancellationException();
                                                })
                                .continueWith(
                                        (Continuation<Void, Void>)
                                                task -> {
                                                    assertTrue(task.isCancelled());
                                                    return null;
                                                }));
    }

    @Test
    public void testUnobservedError() throws InterruptedException {
        try {
            final Object sync = new Object();
            Task.setUnobservedExceptionHandler(
                    (t, e) -> {
                        synchronized (sync) {
                            sync.notify();
                        }
                    });

            synchronized (sync) {
                startFailedTask();
                System.gc();
                sync.wait();
            }

        } finally {
            Task.setUnobservedExceptionHandler(null);
        }
    }

    // runs in a separate method to ensure it is out of scope.
    private void startFailedTask() throws InterruptedException {
        Task.call(
                        () -> {
                            throw new RuntimeException();
                        })
                .waitForCompletion();
    }

    @Test
    public void testWhenAllNoTasks() {
        Task<Void> task = Task.whenAll(new ArrayList<Task<Void>>());

        assertTrue(task.isCompleted());
        assertFalse(task.isCancelled());
        assertFalse(task.isFaulted());
    }

    @Test
    public void testWhenAnyResultFirstSuccess() {
        runTaskTest(
                () -> {
                    final ArrayList<Task<Integer>> tasks = new ArrayList<>();
                    final Task<Integer> firstToCompleteSuccess =
                            Task.callInBackground(
                                    () -> {
                                        Thread.sleep(50);
                                        return 10;
                                    });
                    tasks.addAll(launchTasksWithRandomCompletions(5));
                    tasks.add(firstToCompleteSuccess);
                    tasks.addAll(launchTasksWithRandomCompletions(5));
                    return Task.whenAnyResult(tasks)
                            .continueWith(
                                    (Continuation<Task<Integer>, Void>)
                                            task -> {
                                                assertTrue(task.isCompleted());
                                                assertFalse(task.isFaulted());
                                                assertFalse(task.isCancelled());
                                                assertEquals(
                                                        firstToCompleteSuccess, task.getResult());
                                                assertTrue(task.getResult().isCompleted());
                                                assertFalse(task.getResult().isCancelled());
                                                assertFalse(task.getResult().isFaulted());
                                                assertEquals(
                                                        10, (int) task.getResult().getResult());
                                                return null;
                                            });
                });
    }

    @Test
    public void testWhenAnyFirstSuccess() {
        runTaskTest(
                () -> {
                    final ArrayList<Task<?>> tasks = new ArrayList<>();
                    final Task<String> firstToCompleteSuccess =
                            Task.callInBackground(
                                    () -> {
                                        Thread.sleep(50);
                                        return "SUCCESS";
                                    });
                    tasks.addAll(launchTasksWithRandomCompletions(5));
                    tasks.add(firstToCompleteSuccess);
                    tasks.addAll(launchTasksWithRandomCompletions(5));
                    return Task.whenAny(tasks)
                            .continueWith(
                                    task -> {
                                        assertTrue(task.isCompleted());
                                        assertFalse(task.isFaulted());
                                        assertFalse(task.isCancelled());
                                        assertEquals(firstToCompleteSuccess, task.getResult());
                                        assertTrue(task.getResult().isCompleted());
                                        assertFalse(task.getResult().isCancelled());
                                        assertFalse(task.getResult().isFaulted());
                                        assertEquals("SUCCESS", task.getResult().getResult());
                                        return null;
                                    });
                });
    }

    @Test
    public void testWhenAnyResultFirstError() {
        final Exception error = new RuntimeException("This task failed.");
        runTaskTest(
                () -> {
                    final ArrayList<Task<Integer>> tasks = new ArrayList<>();
                    final Task<Integer> firstToCompleteError =
                            Task.callInBackground(
                                    () -> {
                                        Thread.sleep(50);
                                        throw error;
                                    });
                    tasks.addAll(launchTasksWithRandomCompletions(5));
                    tasks.add(firstToCompleteError);
                    tasks.addAll(launchTasksWithRandomCompletions(5));
                    return Task.whenAnyResult(tasks)
                            .continueWith(
                                    task -> {
                                        assertTrue(task.isCompleted());
                                        assertFalse(task.isFaulted());
                                        assertFalse(task.isCancelled());
                                        assertEquals(firstToCompleteError, task.getResult());
                                        assertTrue(task.getResult().isCompleted());
                                        assertFalse(task.getResult().isCancelled());
                                        assertTrue(task.getResult().isFaulted());
                                        assertEquals(error, task.getResult().getError());
                                        return null;
                                    });
                });
    }

    @Test
    public void testWhenAnyFirstError() {
        final Exception error = new RuntimeException("This task failed.");
        runTaskTest(
                () -> {
                    final ArrayList<Task<?>> tasks = new ArrayList<>();
                    final Task<String> firstToCompleteError =
                            Task.callInBackground(
                                    () -> {
                                        Thread.sleep(50);
                                        throw error;
                                    });
                    tasks.addAll(launchTasksWithRandomCompletions(5));
                    tasks.add(firstToCompleteError);
                    tasks.addAll(launchTasksWithRandomCompletions(5));
                    return Task.whenAny(tasks)
                            .continueWith(
                                    task -> {
                                        assertTrue(task.isCompleted());
                                        assertFalse(task.isFaulted());
                                        assertFalse(task.isCancelled());
                                        assertEquals(firstToCompleteError, task.getResult());
                                        assertTrue(task.getResult().isCompleted());
                                        assertFalse(task.getResult().isCancelled());
                                        assertTrue(task.getResult().isFaulted());
                                        assertEquals(error, task.getResult().getError());
                                        return null;
                                    });
                });
    }

    @Test
    public void testWhenAnyResultFirstCancelled() {
        runTaskTest(
                () -> {
                    final ArrayList<Task<Integer>> tasks = new ArrayList<>();
                    final Task<Integer> firstToCompleteCancelled =
                            Task.callInBackground(
                                    () -> {
                                        Thread.sleep(50);
                                        throw new CancellationException();
                                    });

                    tasks.addAll(launchTasksWithRandomCompletions(5));
                    tasks.add(firstToCompleteCancelled);
                    tasks.addAll(launchTasksWithRandomCompletions(5));
                    return Task.whenAnyResult(tasks)
                            .continueWith(
                                    task -> {
                                        assertTrue(task.isCompleted());
                                        assertFalse(task.isFaulted());
                                        assertFalse(task.isCancelled());
                                        assertEquals(firstToCompleteCancelled, task.getResult());
                                        assertTrue(task.getResult().isCompleted());
                                        assertTrue(task.getResult().isCancelled());
                                        assertFalse(task.getResult().isFaulted());
                                        return null;
                                    });
                });
    }

    @Test
    public void testWhenAnyFirstCancelled() {
        runTaskTest(
                () -> {
                    final ArrayList<Task<?>> tasks = new ArrayList<>();
                    final Task<String> firstToCompleteCancelled =
                            Task.callInBackground(
                                    () -> {
                                        Thread.sleep(50);
                                        throw new CancellationException();
                                    });
                    tasks.addAll(launchTasksWithRandomCompletions(5));
                    tasks.add(firstToCompleteCancelled);
                    tasks.addAll(launchTasksWithRandomCompletions(5));
                    return Task.whenAny(tasks)
                            .continueWith(
                                    task -> {
                                        assertTrue(task.isCompleted());
                                        assertFalse(task.isFaulted());
                                        assertFalse(task.isCancelled());
                                        assertEquals(firstToCompleteCancelled, task.getResult());
                                        assertTrue(task.getResult().isCompleted());
                                        assertTrue(task.getResult().isCancelled());
                                        assertFalse(task.getResult().isFaulted());
                                        return null;
                                    });
                });
    }

    /**
     * Launches a given number of tasks (of Integer) that will complete either in a completed,
     * cancelled or faulted state (random distribution). Each task will reach completion after a
     * somehow random delay (between 500 and 600 ms). Each task reaching a success completion state
     * will have its result set to a random Integer (between 0 to 1000).
     *
     * @param numberOfTasksToLaunch The number of tasks to launch
     * @return A collection containing all the tasks that have been launched
     */
    private Collection<Task<Integer>> launchTasksWithRandomCompletions(int numberOfTasksToLaunch) {
        final ArrayList<Task<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < numberOfTasksToLaunch; i++) {
            Task<Integer> task =
                    Task.callInBackground(
                            () -> {
                                Thread.sleep((long) (500 + (Math.random() * 100)));
                                double rand = Math.random();
                                if (rand >= 0.7) {
                                    throw new RuntimeException("This task failed.");
                                } else if (rand >= 0.4) {
                                    throw new CancellationException();
                                }
                                return (int) (Math.random() * 1000);
                            });
            tasks.add(task);
        }
        return tasks;
    }

    @Test
    public void testWhenAllSuccess() {
        runTaskTest(
                () -> {
                    final ArrayList<Task<Void>> tasks = new ArrayList<>();
                    for (int i = 0; i < 20; i++) {
                        Task<Void> task =
                                Task.callInBackground(
                                        () -> {
                                            Thread.sleep((long) (Math.random() * 100));
                                            return null;
                                        });
                        tasks.add(task);
                    }
                    return Task.whenAll(tasks)
                            .continueWith(
                                    (Continuation<Void, Void>)
                                            task -> {
                                                assertTrue(task.isCompleted());
                                                assertFalse(task.isFaulted());
                                                assertFalse(task.isCancelled());

                                                for (Task<Void> t : tasks) {
                                                    assertTrue(t.isCompleted());
                                                }
                                                return null;
                                            });
                });
    }

    @Test
    public void testWhenAllOneError() {
        final Exception error = new RuntimeException("This task failed.");

        runTaskTest(
                () -> {
                    final ArrayList<Task<Void>> tasks = new ArrayList<>();
                    for (int i = 0; i < 20; i++) {
                        final int number = i;
                        Task<Void> task =
                                Task.callInBackground(
                                        () -> {
                                            Thread.sleep((long) (Math.random() * 100));
                                            if (number == 10) {
                                                throw error;
                                            }
                                            return null;
                                        });
                        tasks.add(task);
                    }
                    return Task.whenAll(tasks)
                            .continueWith(
                                    (Continuation<Void, Void>)
                                            task -> {
                                                assertTrue(task.isCompleted());
                                                assertTrue(task.isFaulted());
                                                assertFalse(task.isCancelled());

                                                assertFalse(
                                                        task.getError()
                                                                instanceof AggregateException);
                                                assertEquals(error, task.getError());

                                                for (Task<Void> t : tasks) {
                                                    assertTrue(t.isCompleted());
                                                }
                                                return null;
                                            });
                });
    }

    @Test
    public void testWhenAllTwoErrors() {
        final Exception error0 = new RuntimeException("This task failed (0).");
        final Exception error1 = new RuntimeException("This task failed (1).");

        runTaskTest(
                () -> {
                    final ArrayList<Task<Void>> tasks = new ArrayList<>();
                    for (int i = 0; i < 20; i++) {
                        final int number = i;
                        Task<Void> task =
                                Task.callInBackground(
                                        () -> {
                                            Thread.sleep((long) (number * 10));
                                            if (number == 10) {
                                                throw error0;
                                            } else if (number == 11) {
                                                throw error1;
                                            }
                                            return null;
                                        });
                        tasks.add(task);
                    }
                    return Task.whenAll(tasks)
                            .continueWith(
                                    (Continuation<Void, Void>)
                                            task -> {
                                                assertTrue(task.isCompleted());
                                                assertTrue(task.isFaulted());
                                                assertFalse(task.isCancelled());

                                                assertTrue(
                                                        task.getError()
                                                                instanceof AggregateException);
                                                assertEquals(
                                                        2,
                                                        ((AggregateException) task.getError())
                                                                .getInnerThrowables()
                                                                .size());
                                                assertEquals(
                                                        error0,
                                                        ((AggregateException) task.getError())
                                                                .getInnerThrowables()
                                                                .get(0));
                                                assertEquals(
                                                        error1,
                                                        ((AggregateException) task.getError())
                                                                .getInnerThrowables()
                                                                .get(1));
                                                assertEquals(error0, task.getError().getCause());

                                                for (Task<Void> t : tasks) {
                                                    assertTrue(t.isCompleted());
                                                }
                                                return null;
                                            });
                });
    }

    @Test
    public void testWhenAllCancel() {
        runTaskTest(
                () -> {
                    final ArrayList<Task<Void>> tasks = new ArrayList<>();
                    for (int i = 0; i < 20; i++) {
                        final TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();

                        final int number = i;
                        Task.callInBackground(
                                (Callable<Void>)
                                        () -> {
                                            Thread.sleep((long) (Math.random() * 100));
                                            if (number == 10) {
                                                tcs.setCancelled();
                                            } else {
                                                tcs.setResult(null);
                                            }
                                            return null;
                                        });

                        tasks.add(tcs.getTask());
                    }
                    return Task.whenAll(tasks)
                            .continueWith(
                                    (Continuation<Void, Void>)
                                            task -> {
                                                assertTrue(task.isCompleted());
                                                assertFalse(task.isFaulted());
                                                assertTrue(task.isCancelled());

                                                for (Task<Void> t : tasks) {
                                                    assertTrue(t.isCompleted());
                                                }
                                                return null;
                                            });
                });
    }

    @Test
    public void testWhenAllResultNoTasks() {
        Task<List<Void>> task = Task.whenAllResult(new ArrayList<>());

        assertTrue(task.isCompleted());
        assertFalse(task.isCancelled());
        assertFalse(task.isFaulted());
        assertTrue(task.getResult().isEmpty());
    }

    @Test
    public void testWhenAllResultSuccess() {
        runTaskTest(
                () -> {
                    final List<Task<Integer>> tasks = new ArrayList<>();
                    for (int i = 0; i < 20; i++) {
                        final int number = (i + 1);
                        Task<Integer> task =
                                Task.callInBackground(
                                        () -> {
                                            Thread.sleep((long) (Math.random() * 100));
                                            return number;
                                        });
                        tasks.add(task);
                    }
                    return Task.whenAllResult(tasks)
                            .continueWith(
                                    (Continuation<List<Integer>, Void>)
                                            task -> {
                                                assertTrue(task.isCompleted());
                                                assertFalse(task.isFaulted());
                                                assertFalse(task.isCancelled());
                                                assertEquals(tasks.size(), task.getResult().size());

                                                for (int i = 0; i < tasks.size(); i++) {
                                                    Task<Integer> t = tasks.get(i);
                                                    assertTrue(t.isCompleted());
                                                    assertEquals(
                                                            t.getResult(), task.getResult().get(i));
                                                }

                                                return null;
                                            });
                });
    }

    @Test
    public void testAsyncChaining() {
        runTaskTest(
                () -> {
                    final ArrayList<Integer> sequence = new ArrayList<>();
                    Task<Void> result = Task.forResult(null);
                    for (int i = 0; i < 20; i++) {
                        final int taskNumber = i;
                        result =
                                result.continueWithTask(
                                        task ->
                                                Task.callInBackground(
                                                        () -> {
                                                            sequence.add(taskNumber);
                                                            return null;
                                                        }));
                    }
                    result =
                            result.continueWith(
                                    task -> {
                                        assertEquals(20, sequence.size());
                                        for (int i = 0; i < 20; i++) {
                                            assertEquals(i, sequence.get(i).intValue());
                                        }
                                        return null;
                                    });
                    return result;
                });
    }

    @Test
    public void testOnSuccess() {
        Continuation<Integer, Integer> continuation = task -> task.getResult() + 1;
        Task<Integer> complete = Task.forResult(5).onSuccess(continuation);
        Task<Integer> error =
                Task.<Integer>forError(new IllegalStateException()).onSuccess(continuation);
        Task<Integer> cancelled = Task.<Integer>cancelled().onSuccess(continuation);

        assertTrue(complete.isCompleted());
        assertEquals(6, complete.getResult().intValue());
        assertFalse(complete.isFaulted());
        assertFalse(complete.isCancelled());

        assertTrue(error.isCompleted());
        assertTrue(error.getError() instanceof RuntimeException);
        assertTrue(error.isFaulted());
        assertFalse(error.isCancelled());

        assertTrue(cancelled.isCompleted());
        assertFalse(cancelled.isFaulted());
        assertTrue(cancelled.isCancelled());
    }

    @Test
    public void testOnSuccessTask() {
        Continuation<Integer, Task<Integer>> continuation =
                task -> Task.forResult(task.getResult() + 1);
        Task<Integer> complete = Task.forResult(5).onSuccessTask(continuation);
        Task<Integer> error =
                Task.<Integer>forError(new IllegalStateException()).onSuccessTask(continuation);
        Task<Integer> cancelled = Task.<Integer>cancelled().onSuccessTask(continuation);

        assertTrue(complete.isCompleted());
        assertEquals(6, complete.getResult().intValue());
        assertFalse(complete.isFaulted());
        assertFalse(complete.isCancelled());

        assertTrue(error.isCompleted());
        assertTrue(error.getError() instanceof RuntimeException);
        assertTrue(error.isFaulted());
        assertFalse(error.isCancelled());

        assertTrue(cancelled.isCompleted());
        assertFalse(cancelled.isFaulted());
        assertTrue(cancelled.isCancelled());
    }

    @Test
    public void testContinueWhile() {
        final AtomicInteger count = new AtomicInteger(0);
        runTaskTest(
                () ->
                        Task.forResult(null)
                                .continueWhile(
                                        () -> count.get() < 10,
                                        task -> {
                                            count.incrementAndGet();
                                            return null;
                                        })
                                .continueWith(
                                        (Continuation<Void, Void>)
                                                task -> {
                                                    assertEquals(10, count.get());
                                                    return null;
                                                }));
    }

    @Test
    public void testContinueWhileAsync() {
        final AtomicInteger count = new AtomicInteger(0);
        runTaskTest(
                () ->
                        Task.forResult(null)
                                .continueWhile(
                                        () -> count.get() < 10,
                                        task -> {
                                            count.incrementAndGet();
                                            return null;
                                        },
                                        Executors.newCachedThreadPool())
                                .continueWith(
                                        (Continuation<Void, Void>)
                                                task -> {
                                                    assertEquals(10, count.get());
                                                    return null;
                                                }));
    }

    @Test
    public void testContinueWhileAsyncCancellation() {
        final AtomicInteger count = new AtomicInteger(0);
        final CancellationTokenSource cts = new CancellationTokenSource();
        runTaskTest(
                () ->
                        Task.forResult(null)
                                .continueWhile(
                                        () -> count.get() < 10,
                                        task -> {
                                            if (count.incrementAndGet() == 5) {
                                                cts.cancel();
                                            }
                                            return null;
                                        },
                                        Executors.newCachedThreadPool(),
                                        cts.getToken())
                                .continueWith(
                                        (Continuation<Void, Void>)
                                                task -> {
                                                    assertTrue(task.isCancelled());
                                                    assertEquals(5, count.get());
                                                    return null;
                                                }));
    }

    @Test
    public void testCallWithBadExecutor() {
        final RuntimeException exception = new RuntimeException("BAD EXECUTORS");

        Task.call(
                        () -> 1,
                        command -> {
                            throw exception;
                        })
                .continueWith(
                        task -> {
                            assertTrue(task.isFaulted());
                            assertTrue(task.getError() instanceof ExecutorException);
                            assertEquals(exception, task.getError().getCause());

                            return null;
                        });
    }

    @Test
    public void testContinueWithBadExecutor() {
        final RuntimeException exception = new RuntimeException("BAD EXECUTORS");

        Task.call(() -> 1)
                .continueWith(
                        Task::getResult,
                        command -> {
                            throw exception;
                        })
                .continueWith(
                        task -> {
                            assertTrue(task.isFaulted());
                            assertTrue(task.getError() instanceof ExecutorException);
                            assertEquals(exception, task.getError().getCause());

                            return null;
                        });
    }

    @Test
    public void testContinueWithTaskAndBadExecutor() {
        final RuntimeException exception = new RuntimeException("BAD EXECUTORS");

        Task.call(() -> 1)
                .continueWithTask(
                        task -> task,
                        command -> {
                            throw exception;
                        })
                .continueWith(
                        task -> {
                            assertTrue(task.isFaulted());
                            assertTrue(task.getError() instanceof ExecutorException);
                            assertEquals(exception, task.getError().getCause());

                            return null;
                        });
    }

    // region TaskCompletionSource

    @Test
    public void testTrySetResult() {
        TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
        Task<String> task = tcs.getTask();
        assertFalse(task.isCompleted());

        boolean success = tcs.trySetResult("SHOW ME WHAT YOU GOT");
        assertTrue(success);
        assertTrue(task.isCompleted());
        assertEquals("SHOW ME WHAT YOU GOT", task.getResult());
    }

    @Test
    public void testTrySetError() {
        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
        Task<Void> task = tcs.getTask();
        assertFalse(task.isCompleted());

        Exception exception = new RuntimeException("DISQUALIFIED");
        boolean success = tcs.trySetError(exception);
        assertTrue(success);
        assertTrue(task.isCompleted());
        assertEquals(exception, task.getError());
    }

    @Test
    public void testTrySetCanceled() {
        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
        Task<Void> task = tcs.getTask();
        assertFalse(task.isCompleted());

        boolean success = tcs.trySetCancelled();
        assertTrue(success);
        assertTrue(task.isCompleted());
        assertTrue(task.isCancelled());
    }

    @Test
    public void testTrySetOnCompletedTask() {
        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
        tcs.setResult(null);

        assertFalse(tcs.trySetResult(null));
        assertFalse(tcs.trySetError(new RuntimeException()));
        assertFalse(tcs.trySetCancelled());
    }

    @Test
    public void testSetResultOnCompletedTask() {
        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
        tcs.setResult(null);

        thrown.expect(IllegalStateException.class);
        tcs.setResult(null);
    }

    @Test
    public void testSetErrorOnCompletedTask() {
        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
        tcs.setResult(null);

        thrown.expect(IllegalStateException.class);
        tcs.setError(new RuntimeException());
    }

    @Test
    public void testSetCancelledOnCompletedTask() {
        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
        tcs.setResult(null);

        thrown.expect(IllegalStateException.class);
        tcs.setCancelled();
    }

    // endregion
}
