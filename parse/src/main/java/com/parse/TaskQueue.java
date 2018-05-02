/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import bolts.Continuation;
import bolts.Task;

/**
 * A helper class for enqueueing tasks
 */
/** package */ class TaskQueue {
  /**
   * We only need to keep the tail of the queue. Cancelled tasks will just complete
   * normally/immediately when their turn arrives.
   */
  private Task<Void> tail;
  private final Lock lock = new ReentrantLock();

  /**
   * Gets a task that can be safely awaited and is dependent on the current tail of the queue. This
   * essentially gives us a proxy for the tail end of the queue that can be safely cancelled.
   * 
   * @return A new task that should be awaited by enqueued tasks.
   */
  private Task<Void> getTaskToAwait() {
    lock.lock();
    try {
      Task<Void> toAwait = tail != null ? tail : Task.<Void> forResult(null);
      return toAwait.continueWith(new Continuation<Void, Void>() {
        @Override
        public Void then(Task<Void> task) throws Exception {
          return null;
        }
      });
    } finally {
      lock.unlock();
    }
  }

  /**
   * Enqueues a task created by taskStart.
   * 
   * @param taskStart
   *          A function given a task to await once state is snapshotted (e.g. after capturing
   *          session tokens at the time of the save call). Awaiting this task will wait for the
   *          created task's turn in the queue.
   * @return The task created by the taskStart function.
   */
  <T> Task<T> enqueue(Continuation<Void, Task<T>> taskStart) {
    lock.lock();
    try {
      Task<T> task;
      Task<Void> oldTail = tail != null ? tail : Task.<Void> forResult(null);
      // The task created by taskStart is responsible for waiting for the task passed into it before
      // doing its work (this gives it an opportunity to do startup work or save state before
      // waiting for its turn in the queue)
      try {
        Task<Void> toAwait = getTaskToAwait();
        task = taskStart.then(toAwait);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

      // The tail task should be dependent on the old tail as well as the newly-created task. This
      // prevents cancellation of the new task from causing the queue to run out of order.
      tail = Task.whenAll(Arrays.asList(oldTail, task));
      return task;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Creates a continuation that will wait for the given task to complete before running the next
   * continuations.
   */
  static <T> Continuation<T, Task<T>> waitFor(final Task<Void> toAwait) {
    return new Continuation<T, Task<T>>() {
      @Override
      public Task<T> then(final Task<T> task) throws Exception {
        return toAwait.continueWithTask(new Continuation<Void, Task<T>>() {
          @Override
          public Task<T> then(Task<Void> ignored) throws Exception {
            return task;
          }
        });
      }
    };
  }

  Lock getLock() {
    return lock;
  }

  void waitUntilFinished() throws InterruptedException {
    lock.lock();
    try {
      if (tail == null) {
        return;
      }
      tail.waitForCompletion();
    } finally {
      lock.unlock();
    }
  }
}
