/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import bolts.Continuation;
import bolts.Task;
import bolts.TaskCompletionSource;

/**
 * {@code ParseFile} is a local representation of a file that is saved to the Parse cloud.
 * <p/>
 * The workflow is to construct a {@code ParseFile} with data and optionally a filename. Then save
 * it and set it as a field on a {@link ParseObject}.
 * <p/>
 * Example:
 * <pre>
 * ParseFile file = new ParseFile("hello".getBytes());
 * file.save();
 *
 * ParseObject object = new ParseObject("TestObject");
 * object.put("file", file);
 * object.save();
 * </pre>
 */
public class ParseFile implements Parcelable {

  /* package for tests */ static ParseFileController getFileController() {
    return ParseCorePlugins.getInstance().getFileController();
  }

  private static ProgressCallback progressCallbackOnMainThread(
      final ProgressCallback progressCallback) {
    if (progressCallback == null) {
      return null;
    }

    return new ProgressCallback() {
      @Override
      public void done(final Integer percentDone) {
        Task.call(new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            progressCallback.done(percentDone);
            return null;
          }
        }, ParseExecutors.main());
      }
    };
  }

  /* package */ static class State {

    /* package */ static class Builder {

      private String name;
      private String mimeType;
      private String url;

      public Builder() {
        // do nothing
      }

      public Builder(State state) {
        name = state.name();
        mimeType = state.mimeType();
        url = state.url();
      }

      public Builder name(String name) {
        this.name = name;
        return this;
      }

      public Builder mimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
      }

      public Builder url(String url) {
        this.url = url;
        return this;
      }

      public State build() {
        return new State(this);
      }
    }

    private final String name;
    private final String contentType;
    private final String url;

    private State(Builder builder) {
      name = builder.name != null ? builder.name : "file";
      contentType = builder.mimeType;
      url = builder.url;
    }

    public String name() {
      return name;
    }

    public String mimeType() {
      return contentType;
    }

    public String url() {
      return url;
    }
  }

  private State state;

  /**
   * Staging of {@code ParseFile}'s data is stored in memory until the {@code ParseFile} has been
   * successfully synced with the server.
   */
  /* package for tests */ byte[] data;
  /* package for tests */ File file;

  /* package for tests */ final TaskQueue taskQueue = new TaskQueue();
  private Set<TaskCompletionSource<?>> currentTasks = Collections.synchronizedSet(
      new HashSet<TaskCompletionSource<?>>());

  /**
   * Creates a new file from a file pointer.
   *
   * @param file
   *          The file.
   */
  public ParseFile(File file) {
    this(file, null);
  }

  /**
   * Creates a new file from a file pointer, and content type. Content type will be used instead of
   * auto-detection by file extension.
   *
   * @param file
   *          The file.
   * @param contentType
   *          The file's content type.
   */
  public ParseFile(File file, String contentType) {
    this(new State.Builder().name(file.getName()).mimeType(contentType).build());
    this.file = file;
  }

  /**
   * Creates a new file from a byte array, file name, and content type. Content type will be used
   * instead of auto-detection by file extension.
   *
   * @param name
   *          The file's name, ideally with extension. The file name must begin with an alphanumeric
   *          character, and consist of alphanumeric characters, periods, spaces, underscores, or
   *          dashes.
   * @param data
   *          The file's data.
   * @param contentType
   *          The file's content type.
   */
  public ParseFile(String name, byte[] data, String contentType) {
    this(new State.Builder().name(name).mimeType(contentType).build());
    this.data = data;
  }

  /**
   * Creates a new file from a byte array.
   *
   * @param data
   *          The file's data.
   */
  public ParseFile(byte[] data) {
    this(null, data, null);
  }

  /**
   * Creates a new file from a byte array and a name. Giving a name with a proper file extension
   * (e.g. ".png") is ideal because it allows Parse to deduce the content type of the file and set
   * appropriate HTTP headers when it is fetched.
   *
   * @param name
   *          The file's name, ideally with extension. The file name must begin with an alphanumeric
   *          character, and consist of alphanumeric characters, periods, spaces, underscores, or
   *          dashes.
   * @param data
   *          The file's data.
   */
  public ParseFile(String name, byte[] data) {
    this(name, data, null);
  }

  /**
   * Creates a new file from a byte array, and content type. Content type will be used instead of
   * auto-detection by file extension.
   *
   * @param data
   *          The file's data.
   * @param contentType
   *          The file's content type.
   */
  public ParseFile(byte[] data, String contentType) {
    this(null, data, contentType);
  }

  /**
   * Creates a new file instance from a {@link Parcel} source. This is used when unparceling
   * a non-dirty ParseFile. Subclasses that need Parcelable behavior should provide their own
   * {@link android.os.Parcelable.Creator} and override this constructor.
   *
   * @param source
   *          the source Parcel
   */
  protected ParseFile(Parcel source) {
    this(source, ParseParcelDecoder.get());
  }

  /**
   * Creates a new file instance from a {@link Parcel} using the given {@link ParseParcelDecoder}.
   * The decoder is currently unused, but it might be in the future, plus this is the pattern we
   * are using in parcelable classes.
   *
   * @param source the parcel
   * @param decoder the decoder
   */
  ParseFile(Parcel source, ParseParcelDecoder decoder) {
    this(new State.Builder()
          .url(source.readString())
          .name(source.readString())
          .mimeType(source.readByte() == 1 ? source.readString() : null)
          .build());
  }

  /* package for tests */ ParseFile(State state) {
    this.state = state;
  }

  /* package for tests */ State getState() {
    return state;
  }

  /**
   * The filename. Before save is called, this is just the filename given by the user (if any).
   * After save is called, that name gets prefixed with a unique identifier.
   *
   * @return The file's name.
   */
  public String getName() {
    return state.name();
  }

  /**
   * Whether the file still needs to be saved.
   *
   * @return Whether the file needs to be saved.
   */
  public boolean isDirty() {
    return state.url() == null;
  }

  /**
   * Whether the file has available data.
   */
  public boolean isDataAvailable() {
    return data != null || getFileController().isDataAvailable(state);
  }

  /**
   * This returns the url of the file. It's only available after you save or after you get the file
   * from a ParseObject.
   *
   * @return The url of the file.
   */
  public String getUrl() {
    return state.url();
  }

  /**
   * Saves the file to the Parse cloud synchronously.
   */
  public void save() throws ParseException {
    ParseTaskUtils.wait(saveInBackground());
  }

  private Task<Void> saveAsync(final String sessionToken,
      final ProgressCallback uploadProgressCallback,
      Task<Void> toAwait, final Task<Void> cancellationToken) {
    // If the file isn't dirty, just return immediately.
    if (!isDirty()) {
      return Task.forResult(null);
    }
    if (cancellationToken != null && cancellationToken.isCancelled()) {
      return Task.cancelled();
    }

    // Wait for our turn in the queue, then check state to decide whether to no-op.
    return toAwait.continueWithTask(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> task) throws Exception {
        if (!isDirty()) {
          return Task.forResult(null);
        }
        if (cancellationToken != null && cancellationToken.isCancelled()) {
          return Task.cancelled();
        }

        Task<ParseFile.State> saveTask;
        if (data != null) {
          saveTask = getFileController().saveAsync(
              state,
              data,
              sessionToken,
              progressCallbackOnMainThread(uploadProgressCallback),
              cancellationToken);
        } else {
          saveTask = getFileController().saveAsync(
              state,
              file,
              sessionToken,
              progressCallbackOnMainThread(uploadProgressCallback),
              cancellationToken);
        }

        return saveTask.onSuccessTask(new Continuation<State, Task<Void>>() {
          @Override
          public Task<Void> then(Task<State> task) throws Exception {
            state = task.getResult();
            // Since we have successfully uploaded the file, we do not need to hold the file pointer
            // anymore.
            data = null;
            file = null;
            return task.makeVoid();
          }
        });
      }
    });
  }

  /**
   * Saves the file to the Parse cloud in a background thread.
   * `progressCallback` is guaranteed to be called with 100 before saveCallback is called.
   *
   * @param uploadProgressCallback
   *          A ProgressCallback that is called periodically with progress updates.
   * @return A Task that will be resolved when the save completes.
   */
  public Task<Void> saveInBackground(final ProgressCallback uploadProgressCallback) {
    final TaskCompletionSource<Void> cts = new TaskCompletionSource<>();
    currentTasks.add(cts);

    return ParseUser.getCurrentSessionTokenAsync().onSuccessTask(new Continuation<String, Task<Void>>() {
      @Override
      public Task<Void> then(Task<String> task) throws Exception {
        final String sessionToken = task.getResult();
        return saveAsync(sessionToken, uploadProgressCallback, cts.getTask());
      }
    }).continueWithTask(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> task) throws Exception {
        cts.trySetResult(null); // release
        currentTasks.remove(cts);
        return task;
      }
    });
  }

  /* package */ Task<Void> saveAsync(final String sessionToken,
      final ProgressCallback uploadProgressCallback, final Task<Void> cancellationToken) {
    return taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> toAwait) throws Exception {
        return saveAsync(sessionToken, uploadProgressCallback, toAwait, cancellationToken);
      }
    });
  }

  /**
   * Saves the file to the Parse cloud in a background thread.
   *
   * @return A Task that will be resolved when the save completes.
   */
  public Task<Void> saveInBackground() {
    return saveInBackground((ProgressCallback) null);
  }

  /**
   * Saves the file to the Parse cloud in a background thread.
   * `progressCallback` is guaranteed to be called with 100 before saveCallback is called.
   *
   * @param saveCallback
   *          A SaveCallback that gets called when the save completes.
   * @param progressCallback
   *          A ProgressCallback that is called periodically with progress updates.
   */
  public void saveInBackground(final SaveCallback saveCallback,
      ProgressCallback progressCallback) {
    ParseTaskUtils.callbackOnMainThreadAsync(saveInBackground(progressCallback), saveCallback);
  }

  /**
   * Saves the file to the Parse cloud in a background thread.
   *
   * @param callback
   *          A SaveCallback that gets called when the save completes.
   */
  public void saveInBackground(SaveCallback callback) {
    ParseTaskUtils.callbackOnMainThreadAsync(saveInBackground(), callback);
  }

  /**
   * Synchronously gets the data from cache if available or fetches its content from the network.
   * You probably want to use {@link #getDataInBackground()} instead unless you're already in a
   * background thread.
   */
  public byte[] getData() throws ParseException {
    return ParseTaskUtils.wait(getDataInBackground());
  }

  /**
   * Asynchronously gets the data from cache if available or fetches its content from the network.
   * A {@code ProgressCallback} will be called periodically with progress updates.
   *
   * @param progressCallback
   *          A {@code ProgressCallback} that is called periodically with progress updates.
   * @return A Task that is resolved when the data has been fetched.
   */
  public Task<byte[]> getDataInBackground(final ProgressCallback progressCallback) {
    final TaskCompletionSource<Void> cts = new TaskCompletionSource<>();
    currentTasks.add(cts);

    return taskQueue.enqueue(new Continuation<Void, Task<byte[]>>() {
      @Override
      public Task<byte[]> then(Task<Void> toAwait) throws Exception {
        return fetchInBackground(progressCallback, toAwait, cts.getTask()).onSuccess(new Continuation<File, byte[]>() {
          @Override
          public byte[] then(Task<File> task) throws Exception {
            File file = task.getResult();
            try {
              return ParseFileUtils.readFileToByteArray(file);
            } catch (IOException e) {
              // do nothing
            }
            return null;
          }
        });
      }
    }).continueWithTask(new Continuation<byte[], Task<byte[]>>() {
      @Override
      public Task<byte[]> then(Task<byte[]> task) throws Exception {
        cts.trySetResult(null); // release
        currentTasks.remove(cts);
        return task;
      }
    });
  }

  /**
   * Asynchronously gets the data from cache if available or fetches its content from the network.
   *
   * @return A Task that is resolved when the data has been fetched.
   */
  public Task<byte[]> getDataInBackground() {
    return getDataInBackground((ProgressCallback) null);
  }

  /**
   * Asynchronously gets the data from cache if available or fetches its content from the network.
   * A {@code ProgressCallback} will be called periodically with progress updates.
   * A {@code GetDataCallback} will be called when the get completes.
   *
   * @param dataCallback
   *          A {@code GetDataCallback} that is called when the get completes.
   * @param progressCallback
   *          A {@code ProgressCallback} that is called periodically with progress updates.
   */
  public void getDataInBackground(GetDataCallback dataCallback,
      final ProgressCallback progressCallback) {
    ParseTaskUtils.callbackOnMainThreadAsync(getDataInBackground(progressCallback), dataCallback);
  }

  /**
   * Asynchronously gets the data from cache if available or fetches its content from the network.
   * A {@code GetDataCallback} will be called when the get completes.
   *
   * @param dataCallback
   *          A {@code GetDataCallback} that is called when the get completes.
   */
  public void getDataInBackground(GetDataCallback dataCallback) {
    ParseTaskUtils.callbackOnMainThreadAsync(getDataInBackground(), dataCallback);
  }

  /**
   * Synchronously gets the file pointer from cache if available or fetches its content from the
   * network. You probably want to use {@link #getFileInBackground()} instead unless you're already
   * in a background thread.
   * <strong>Note: </strong> The {@link File} location may change without notice and should not be
   * stored to be accessed later.
   */
  public File getFile() throws ParseException {
    return ParseTaskUtils.wait(getFileInBackground());
  }

  /**
   * Asynchronously gets the file pointer from cache if available or fetches its content from the
   * network. The {@code ProgressCallback} will be called periodically with progress updates.
   * <strong>Note: </strong> The {@link File} location may change without notice and should not be
   * stored to be accessed later.
   *
   * @param progressCallback
   *          A {@code ProgressCallback} that is called periodically with progress updates.
   * @return A Task that is resolved when the file pointer of this object has been fetched.
   */
  public Task<File> getFileInBackground(final ProgressCallback progressCallback) {
    final TaskCompletionSource<Void> cts = new TaskCompletionSource<>();
    currentTasks.add(cts);

    return taskQueue.enqueue(new Continuation<Void, Task<File>>() {
      @Override
      public Task<File> then(Task<Void> toAwait) throws Exception {
        return fetchInBackground(progressCallback, toAwait, cts.getTask());
      }
    }).continueWithTask(new Continuation<File, Task<File>>() {
      @Override
      public Task<File> then(Task<File> task) throws Exception {
        cts.trySetResult(null); // release
        currentTasks.remove(cts);
        return task;
      }
    });
  }

  /**
   * Asynchronously gets the file pointer from cache if available or fetches its content from the
   * network.
   * <strong>Note: </strong> The {@link File} location may change without notice and should not be
   * stored to be accessed later.
   *
   * @return A Task that is resolved when the data has been fetched.
   */
  public Task<File> getFileInBackground() {
    return getFileInBackground((ProgressCallback) null);
  }

  /**
   * Asynchronously gets the file pointer from cache if available or fetches its content from the
   * network. The {@code GetFileCallback} will be called when the get completes.
   * The {@code ProgressCallback} will be called periodically with progress updates.
   * The {@code ProgressCallback} is guaranteed to be called with 100 before the
   * {@code GetFileCallback} is called.
   * <strong>Note: </strong> The {@link File} location may change without notice and should not be
   * stored to be accessed later.
   *
   * @param fileCallback
   *          A {@code GetFileCallback} that is called when the get completes.
   * @param progressCallback
   *          A {@code ProgressCallback} that is called periodically with progress updates.
   */
  public void getFileInBackground(GetFileCallback fileCallback,
      final ProgressCallback progressCallback) {
    ParseTaskUtils.callbackOnMainThreadAsync(getFileInBackground(progressCallback), fileCallback);
  }

  /**
   * Asynchronously gets the file pointer from cache if available or fetches its content from the
   * network. The {@code GetFileCallback} will be called when the get completes.
   * <strong>Note: </strong> The {@link File} location may change without notice and should not be
   * stored to be accessed later.
   *
   * @param fileCallback
   *          A {@code GetFileCallback} that is called when the get completes.
   */
  public void getFileInBackground(GetFileCallback fileCallback) {
    ParseTaskUtils.callbackOnMainThreadAsync(getFileInBackground(), fileCallback);
  }

  /**
   * Synchronously gets the data stream from cached file if available or fetches its content from
   * the network, saves the content as cached file and returns the data stream of the cached file.
   * You probably want to use {@link #getDataStreamInBackground} instead unless you're already in a
   * background thread.
   */
  public InputStream getDataStream() throws ParseException {
    return ParseTaskUtils.wait(getDataStreamInBackground());
  }

  /**
   * Asynchronously gets the data stream from cached file if available or fetches its content from
   * the network, saves the content as cached file and returns the data stream of the cached file.
   * The {@code ProgressCallback} will be called periodically with progress updates.
   *
   * @param progressCallback
   *          A {@code ProgressCallback} that is called periodically with progress updates.
   * @return A Task that is resolved when the data stream of this object has been fetched.
   */
  public Task<InputStream> getDataStreamInBackground(final ProgressCallback progressCallback) {
    final TaskCompletionSource<Void> cts = new TaskCompletionSource<>();
    currentTasks.add(cts);

    return taskQueue.enqueue(new Continuation<Void, Task<InputStream>>() {
      @Override
      public Task<InputStream> then(Task<Void> toAwait) throws Exception {
        return fetchInBackground(progressCallback, toAwait, cts.getTask()).onSuccess(new Continuation<File, InputStream>() {
          @Override
          public InputStream then(Task<File> task) throws Exception {
            return new FileInputStream(task.getResult());
          }
        });
      }
    }).continueWithTask(new Continuation<InputStream, Task<InputStream>>() {
      @Override
      public Task<InputStream> then(Task<InputStream> task) throws Exception {
        cts.trySetResult(null); // release
        currentTasks.remove(cts);
        return task;
      }
    });
  }

  /**
   * Asynchronously gets the data stream from cached file if available or fetches its content from
   * the network, saves the content as cached file and returns the data stream of the cached file.
   *
   * @return A Task that is resolved when the data stream has been fetched.
   */
  public Task<InputStream> getDataStreamInBackground() {
    return getDataStreamInBackground((ProgressCallback) null);
  }

  /**
   * Asynchronously gets the data stream from cached file if available or fetches its content from
   * the network, saves the content as cached file and returns the data stream of the cached file.
   * The {@code GetDataStreamCallback} will be called when the get completes. The
   * {@code ProgressCallback} will be called periodically with progress updates. The
   * {@code ProgressCallback} is guaranteed to be called with 100 before
   * {@code GetDataStreamCallback} is called.
   *
   * @param dataStreamCallback
   *          A {@code GetDataStreamCallback} that is called when the get completes.
   * @param progressCallback
   *          A {@code ProgressCallback} that is called periodically with progress updates.
   */
  public void getDataStreamInBackground(GetDataStreamCallback dataStreamCallback,
      final ProgressCallback progressCallback) {
    ParseTaskUtils.callbackOnMainThreadAsync(
        getDataStreamInBackground(progressCallback), dataStreamCallback);
  }

  /**
   * Asynchronously gets the data stream from cached file if available or fetches its content from
   * the network, saves the content as cached file and returns the data stream of the cached file.
   * The {@code GetDataStreamCallback} will be called when the get completes.
   *
   * @param dataStreamCallback
   *          A {@code GetDataStreamCallback} that is called when the get completes.
   */
  public void getDataStreamInBackground(GetDataStreamCallback dataStreamCallback) {
    ParseTaskUtils.callbackOnMainThreadAsync(getDataStreamInBackground(), dataStreamCallback);
  }

  private Task<File> fetchInBackground(
      final ProgressCallback progressCallback,
      Task<Void> toAwait,
      final Task<Void> cancellationToken) {
    if (cancellationToken != null && cancellationToken.isCancelled()) {
      return Task.cancelled();
    }

    return toAwait.continueWithTask(new Continuation<Void, Task<File>>() {
      @Override
      public Task<File> then(Task<Void> task) throws Exception {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
          return Task.cancelled();
        }
        return getFileController().fetchAsync(
            state,
            null,
            progressCallbackOnMainThread(progressCallback),
            cancellationToken);
      }
    });
  }

  /**
   * Cancels the operations for this {@code ParseFile} if they are still in the task queue. However,
   * if a network request has already been started for an operation, the network request will not
   * be canceled.
   */
  //TODO (grantland): Deprecate and replace with CancellationToken
  public void cancel() {
    Set<TaskCompletionSource<?>> tasks = new HashSet<>(currentTasks);
    for (TaskCompletionSource<?> tcs : tasks) {
      tcs.trySetCancelled();
    }
    currentTasks.removeAll(tasks);
  }

  /*
   * Encode/Decode
   */
  @SuppressWarnings("unused")
  /* package */ ParseFile(JSONObject json, ParseDecoder decoder) {
    this(new State.Builder().name(json.optString("name")).url(json.optString("url")).build());
  }

  /* package */ JSONObject encode() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("__type", "File");
    json.put("name", getName());

    String url = getUrl();
    if (url == null) {
      throw new IllegalStateException("Unable to encode an unsaved ParseFile.");
    }
    json.put("url", getUrl());

    return json;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    writeToParcel(dest, ParseParcelEncoder.get());
  }

  void writeToParcel(Parcel dest, ParseParcelEncoder encoder) {
    if (isDirty()) {
      throw new RuntimeException("Unable to parcel an unsaved ParseFile.");
    }
    dest.writeString(getUrl()); // Not null
    dest.writeString(getName()); // Not null
    String type = state.mimeType(); // Nullable
    dest.writeByte(type != null ? (byte) 1 : 0);
    if (type != null) {
      dest.writeString(type);
    }
  }

  public final static Creator<ParseFile> CREATOR = new Creator<ParseFile>() {
    @Override
    public ParseFile createFromParcel(Parcel source) {
      return new ParseFile(source);
    }

    @Override
    public ParseFile[] newArray(int size) {
      return new ParseFile[size];
    }
  };
}
