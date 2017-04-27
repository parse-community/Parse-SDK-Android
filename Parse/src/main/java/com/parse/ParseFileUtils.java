/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.parse;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

/**
 * General file manipulation utilities.
 */
/** package */ class ParseFileUtils {

  /**
   * The number of bytes in a kilobyte.
   */
  public static final long ONE_KB = 1024;

  /**
   * The number of bytes in a megabyte.
   */
  public static final long ONE_MB = ONE_KB * ONE_KB;

  /**
   * The file copy buffer size (30 MB)
   */
  private static final long FILE_COPY_BUFFER_SIZE = ONE_MB * 30;

  /**
   * Reads the contents of a file into a byte array.
   * The file is always closed.
   *
   * @param file  the file to read, must not be <code>null</code>
   * @return the file contents, never <code>null</code>
   * @throws IOException in case of an I/O error
   * @since Commons IO 1.1
   */
  public static byte[] readFileToByteArray(File file) throws IOException {
    InputStream in = null;
    try {
      in = openInputStream(file);
      return ParseIOUtils.toByteArray(in);
    } finally {
      ParseIOUtils.closeQuietly(in);
    }
  }

  //-----------------------------------------------------------------------
  /**
   * Opens a {@link FileInputStream} for the specified file, providing better
   * error messages than simply calling <code>new FileInputStream(file)</code>.
   * <p>
   * At the end of the method either the stream will be successfully opened,
   * or an exception will have been thrown.
   * <p>
   * An exception is thrown if the file does not exist.
   * An exception is thrown if the file object exists but is a directory.
   * An exception is thrown if the file exists but cannot be read.
   *
   * @param file  the file to open for input, must not be <code>null</code>
   * @return a new {@link FileInputStream} for the specified file
   * @throws FileNotFoundException if the file does not exist
   * @throws IOException if the file object is a directory
   * @throws IOException if the file cannot be read
   * @since Commons IO 1.3
   */
  public static FileInputStream openInputStream(File file) throws IOException {
    if (file.exists()) {
      if (file.isDirectory()) {
        throw new IOException("File '" + file + "' exists but is a directory");
      }
      if (!file.canRead()) {
        throw new IOException("File '" + file + "' cannot be read");
      }
    } else {
      throw new FileNotFoundException("File '" + file + "' does not exist");
    }
    return new FileInputStream(file);
  }

  /**
   * Writes a byte array to a file creating the file if it does not exist.
   * <p>
   * NOTE: As from v1.3, the parent directories of the file will be created
   * if they do not exist.
   *
   * @param file  the file to write to
   * @param data  the content to write to the file
   * @throws IOException in case of an I/O error
   * @since Commons IO 1.1
   */
  public static void writeByteArrayToFile(File file, byte[] data) throws IOException {
    OutputStream out = null;
    try {
      out = openOutputStream(file);
      out.write(data);
    } finally {
      ParseIOUtils.closeQuietly(out);
    }
  }

  //-----------------------------------------------------------------------
  /**
   * Opens a {@link FileOutputStream} for the specified file, checking and
   * creating the parent directory if it does not exist.
   * <p>
   * At the end of the method either the stream will be successfully opened,
   * or an exception will have been thrown.
   * <p>
   * The parent directory will be created if it does not exist.
   * The file will be created if it does not exist.
   * An exception is thrown if the file object exists but is a directory.
   * An exception is thrown if the file exists but cannot be written to.
   * An exception is thrown if the parent directory cannot be created.
   *
   * @param file  the file to open for output, must not be <code>null</code>
   * @return a new {@link FileOutputStream} for the specified file
   * @throws IOException if the file object is a directory
   * @throws IOException if the file cannot be written to
   * @throws IOException if a parent directory needs creating but that fails
   * @since Commons IO 1.3
   */
  public static FileOutputStream openOutputStream(File file) throws IOException {
    if (file.exists()) {
      if (file.isDirectory()) {
        throw new IOException("File '" + file + "' exists but is a directory");
      }
      if (!file.canWrite()) {
        throw new IOException("File '" + file + "' cannot be written to");
      }
    } else {
      File parent = file.getParentFile();
      if (parent != null && !parent.exists()) {
        if (!parent.mkdirs()) {
          throw new IOException("File '" + file + "' could not be created");
        }
      }
    }
    return new FileOutputStream(file);
  }

  /**
   * Moves a file.
   * <p>
   * When the destination file is on another file system, do a "copy and delete".
   *
   * @param srcFile the file to be moved
   * @param destFile the destination file
   * @throws NullPointerException if source or destination is {@code null}
   * @throws IOException if source or destination is invalid
   * @throws IOException if an IO error occurs moving the file
   * @since 1.4
   */
  public static void moveFile(final File srcFile, final File destFile) throws IOException {
    if (srcFile == null) {
      throw new NullPointerException("Source must not be null");
    }
    if (destFile == null) {
      throw new NullPointerException("Destination must not be null");
    }
    if (!srcFile.exists()) {
      throw new FileNotFoundException("Source '" + srcFile + "' does not exist");
    }
    if (srcFile.isDirectory()) {
      throw new IOException("Source '" + srcFile + "' is a directory");
    }
    if (destFile.exists()) {
      throw new IOException("Destination '" + destFile + "' already exists");
    }
    if (destFile.isDirectory()) {
      throw new IOException("Destination '" + destFile + "' is a directory");
    }
    final boolean rename = srcFile.renameTo(destFile);
    if (!rename) {
      copyFile( srcFile, destFile );
      if (!srcFile.delete()) {
        ParseFileUtils.deleteQuietly(destFile);
        throw new IOException("Failed to delete original file '" + srcFile +
                "' after copy to '" + destFile + "'");
      }
    }
  }

  /**
   * Copies a file to a new location preserving the file date.
   * <p>
   * This method copies the contents of the specified source file to the
   * specified destination file. The directory holding the destination file is
   * created if it does not exist. If the destination file exists, then this
   * method will overwrite it.
   * <p>
   * <strong>Note:</strong> This method tries to preserve the file's last
   * modified date/times using {@link File#setLastModified(long)}, however
   * it is not guaranteed that the operation will succeed.
   * If the modification operation fails, no indication is provided.
   *
   * @param srcFile  an existing file to copy, must not be {@code null}
   * @param destFile  the new file, must not be {@code null}
   *
   * @throws NullPointerException if source or destination is {@code null}
   * @throws IOException if source or destination is invalid
   * @throws IOException if an IO error occurs during copying
   * @throws IOException if the output file length is not the same as the input file length after the copy completes
   * @see #copyFile(File, File, boolean)
   */
  public static void copyFile(final File srcFile, final File destFile) throws IOException {
    copyFile(srcFile, destFile, true);
  }

  /**
   * Copies a file to a new location.
   * <p>
   * This method copies the contents of the specified source file
   * to the specified destination file.
   * The directory holding the destination file is created if it does not exist.
   * If the destination file exists, then this method will overwrite it.
   * <p>
   * <strong>Note:</strong> Setting <code>preserveFileDate</code> to
   * {@code true} tries to preserve the file's last modified
   * date/times using {@link File#setLastModified(long)}, however it is
   * not guaranteed that the operation will succeed.
   * If the modification operation fails, no indication is provided.
   *
   * @param srcFile  an existing file to copy, must not be {@code null}
   * @param destFile  the new file, must not be {@code null}
   * @param preserveFileDate  true if the file date of the copy
   *  should be the same as the original
   *
   * @throws NullPointerException if source or destination is {@code null}
   * @throws IOException if source or destination is invalid
   * @throws IOException if an IO error occurs during copying
   * @throws IOException if the output file length is not the same as the input file length after the copy completes
   * @see #doCopyFile(File, File, boolean)
   */
  public static void copyFile(final File srcFile, final File destFile,
                              final boolean preserveFileDate) throws IOException {
    if (srcFile == null) {
      throw new NullPointerException("Source must not be null");
    }
    if (destFile == null) {
      throw new NullPointerException("Destination must not be null");
    }
    if (!srcFile.exists()) {
      throw new FileNotFoundException("Source '" + srcFile + "' does not exist");
    }
    if (srcFile.isDirectory()) {
      throw new IOException("Source '" + srcFile + "' exists but is a directory");
    }
    if (srcFile.getCanonicalPath().equals(destFile.getCanonicalPath())) {
      throw new IOException("Source '" + srcFile + "' and destination '" + destFile + "' are the same");
    }
    final File parentFile = destFile.getParentFile();
    if (parentFile != null) {
      if (!parentFile.mkdirs() && !parentFile.isDirectory()) {
        throw new IOException("Destination '" + parentFile + "' directory cannot be created");
      }
    }
    if (destFile.exists() && !destFile.canWrite()) {
      throw new IOException("Destination '" + destFile + "' exists but is read-only");
    }
    doCopyFile(srcFile, destFile, preserveFileDate);
  }

  /**
   * Internal copy file method.
   * This caches the original file length, and throws an IOException
   * if the output file length is different from the current input file length.
   * So it may fail if the file changes size.
   * It may also fail with "IllegalArgumentException: Negative size" if the input file is truncated part way
   * through copying the data and the new file size is less than the current position.
   *
   * @param srcFile  the validated source file, must not be {@code null}
   * @param destFile  the validated destination file, must not be {@code null}
   * @param preserveFileDate  whether to preserve the file date
   * @throws IOException if an error occurs
   * @throws IOException if the output file length is not the same as the input file length after the copy completes
   * @throws IllegalArgumentException "Negative size" if the file is truncated so that the size is less than the position
   */
  private static void doCopyFile(final File srcFile, final File destFile, final boolean preserveFileDate) throws IOException {
    if (destFile.exists() && destFile.isDirectory()) {
      throw new IOException("Destination '" + destFile + "' exists but is a directory");
    }

    FileInputStream fis = null;
    FileOutputStream fos = null;
    FileChannel input = null;
    FileChannel output = null;
    try {
      fis = new FileInputStream(srcFile);
      fos = new FileOutputStream(destFile);
      input  = fis.getChannel();
      output = fos.getChannel();
      final long size = input.size(); // TODO See IO-386
      long pos = 0;
      long count = 0;
      while (pos < size) {
        final long remain = size - pos;
        count = remain > FILE_COPY_BUFFER_SIZE ? FILE_COPY_BUFFER_SIZE : remain;
        final long bytesCopied = output.transferFrom(input, pos, count);
        if (bytesCopied == 0) { // IO-385 - can happen if file is truncated after caching the size
          break; // ensure we don't loop forever
        }
        pos += bytesCopied;
      }
    } finally {
      ParseIOUtils.closeQuietly(output);
      ParseIOUtils.closeQuietly(fos);
      ParseIOUtils.closeQuietly(input);
      ParseIOUtils.closeQuietly(fis);
    }

    final long srcLen = srcFile.length(); // TODO See IO-386
    final long dstLen = destFile.length(); // TODO See IO-386
    if (srcLen != dstLen) {
      throw new IOException("Failed to copy full contents from '" +
              srcFile + "' to '" + destFile + "' Expected length: " + srcLen +" Actual: " + dstLen);
    }
    if (preserveFileDate) {
      destFile.setLastModified(srcFile.lastModified());
    }
  }

  //-----------------------------------------------------------------------
  /**
   * Deletes a directory recursively.
   *
   * @param directory  directory to delete
   * @throws IOException in case deletion is unsuccessful
   */
  public static void deleteDirectory(final File directory) throws IOException {
    if (!directory.exists()) {
      return;
    }

    if (!isSymlink(directory)) {
      cleanDirectory(directory);
    }

    if (!directory.delete()) {
      final String message =
              "Unable to delete directory " + directory + ".";
      throw new IOException(message);
    }
  }

  /**
   * Deletes a file, never throwing an exception. If file is a directory, delete it and all sub-directories.
   * <p>
   * The difference between File.delete() and this method are:
   * <ul>
   * <li>A directory to be deleted does not have to be empty.</li>
   * <li>No exceptions are thrown when a file or directory cannot be deleted.</li>
   * </ul>
   *
   * @param file  file or directory to delete, can be {@code null}
   * @return {@code true} if the file or directory was deleted, otherwise
   * {@code false}
   *
   * @since 1.4
   */
  public static boolean deleteQuietly(final File file) {
    if (file == null) {
      return false;
    }
    try {
      if (file.isDirectory()) {
        cleanDirectory(file);
      }
    } catch (final Exception ignored) {
    }

    try {
      return file.delete();
    } catch (final Exception ignored) {
      return false;
    }
  }

  /**
   * Cleans a directory without deleting it.
   *
   * @param directory directory to clean
   * @throws IOException in case cleaning is unsuccessful
   */
  public static void cleanDirectory(final File directory) throws IOException {
    if (!directory.exists()) {
      final String message = directory + " does not exist";
      throw new IllegalArgumentException(message);
    }

    if (!directory.isDirectory()) {
      final String message = directory + " is not a directory";
      throw new IllegalArgumentException(message);
    }

    final File[] files = directory.listFiles();
    if (files == null) {  // null if security restricted
      throw new IOException("Failed to list contents of " + directory);
    }

    IOException exception = null;
    for (final File file : files) {
      try {
        forceDelete(file);
      } catch (final IOException ioe) {
        exception = ioe;
      }
    }

    if (null != exception) {
      throw exception;
    }
  }

  //-----------------------------------------------------------------------
  /**
   * Deletes a file. If file is a directory, delete it and all sub-directories.
   * <p>
   * The difference between File.delete() and this method are:
   * <ul>
   * <li>A directory to be deleted does not have to be empty.</li>
   * <li>You get exceptions when a file or directory cannot be deleted.
   *      (java.io.File methods returns a boolean)</li>
   * </ul>
   *
   * @param file  file or directory to delete, must not be {@code null}
   * @throws NullPointerException if the directory is {@code null}
   * @throws FileNotFoundException if the file was not found
   * @throws IOException in case deletion is unsuccessful
   */
  public static void forceDelete(final File file) throws IOException {
    if (file.isDirectory()) {
      deleteDirectory(file);
    } else {
      final boolean filePresent = file.exists();
      if (!file.delete()) {
        if (!filePresent){
          throw new FileNotFoundException("File does not exist: " + file);
        }
        final String message =
                "Unable to delete file: " + file;
        throw new IOException(message);
      }
    }
  }

  /**
   * Determines whether the specified file is a Symbolic Link rather than an actual file.
   * <p>
   * Will not return true if there is a Symbolic Link anywhere in the path,
   * only if the specific file is.
   * <p>
   * For code that runs on Java 1.7 or later, use the following method instead:
   * <br>
   * {@code boolean java.nio.file.Files.isSymbolicLink(Path path)}
   * @param file the file to check
   * @return true if the file is a Symbolic Link
   * @throws IOException if an IO error occurs while checking the file
   * @since 2.0
   */
  public static boolean isSymlink(final File file) throws IOException {
    if (file == null) {
      throw new NullPointerException("File must not be null");
    }
//    if (FilenameUtils.isSystemWindows()) {
//      return false;
//    }
    File fileInCanonicalDir = null;
    if (file.getParent() == null) {
      fileInCanonicalDir = file;
    } else {
      final File canonicalDir = file.getParentFile().getCanonicalFile();
      fileInCanonicalDir = new File(canonicalDir, file.getName());
    }

    if (fileInCanonicalDir.getCanonicalFile().equals(fileInCanonicalDir.getAbsoluteFile())) {
      return false;
    } else {
      return true;
    }
  }

  //region String

  public static String readFileToString(File file, Charset encoding) throws IOException {
    return new String(readFileToByteArray(file), encoding);
  }

  public static String readFileToString(File file, String encoding) throws IOException {
    return readFileToString(file, Charset.forName(encoding));
  }

  public static void writeStringToFile(File file, String string, Charset encoding)
      throws IOException {
    writeByteArrayToFile(file, string.getBytes(encoding));
  }

  public static void writeStringToFile(File file, String string, String encoding)
      throws IOException {
    writeStringToFile(file, string, Charset.forName(encoding));
  }

  //endregion

  //region JSONObject

  /**
   * Reads the contents of a file into a {@link JSONObject}. The file is always closed.
   */
  public static JSONObject readFileToJSONObject(File file) throws IOException, JSONException {
    String content = readFileToString(file, "UTF-8");
    return new JSONObject(content);
  }

  /**
   * Writes a {@link JSONObject} to a file creating the file if it does not exist.
   */
  public static void writeJSONObjectToFile(File file, JSONObject json) throws IOException {
    ParseFileUtils.writeByteArrayToFile(file, json.toString().getBytes(Charset.forName("UTF-8")));
  }

  //endregion
}
