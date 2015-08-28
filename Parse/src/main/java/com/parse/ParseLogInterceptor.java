/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;

import bolts.Task;

// TODO(mengyan): Add java doc and make it public before we launch it
/** package */ class ParseLogInterceptor implements ParseNetworkInterceptor {

  private final static String TAG = "ParseLogNetworkInterceptor";
  private final static String LOG_PARAGRAPH_BREAKER = "--------------";
  private final static String KEY_TYPE = "Type";
  private final static String KEY_HEADERS = "Headers";
  private final static String KEY_URL = "Url";
  private final static String KEY_METHOD = "Method";
  private final static String KEY_REQUEST_ID = "Request-Id";
  private final static String KEY_CONTENT_LENGTH = "Content-Length";
  private final static String KEY_CONTENT_TYPE = "Content-Type";
  private final static String KEY_BODY = "Body";
  private final static String KEY_STATUS_CODE = "Status-Code";
  private final static String KEY_REASON_PHASE = "Reason-Phase";
  private final static String KEY_ERROR = "Error";
  private final static String TYPE_REQUEST = "Request";
  private final static String TYPE_RESPONSE = "Response";
  private final static String TYPE_ERROR = "Error";

  private final static String IGNORED_BODY_INFO = "Ignored";

  private static final String GZIP_ENCODING = "gzip";

  /* package for tests */ static abstract class Logger {
    public static String NEW_LINE = "\n";

    // The reason we need a lock here is because since multiple network threads may write to the
    // Logcat concurrently, the message of different threads may intertwined. We need this lock to
    // keep the message printed by different threads are separated.
    private ReentrantLock lock;

    public Logger() {
      lock = new ReentrantLock();
    }

    public abstract void write(String str);

    public void lock() {
      lock.lock();
    }

    public void unlock() {
      lock.unlock();
    }

    public void write(String name, String value) {
      write(name + " : " + value);
    }

    public void writeLine(String str) {
      write(str);
      write(NEW_LINE);
    }

    public void writeLine(String name, String value) {
      writeLine(name + " : " + value);
    }
  }

  private static class LogcatLogger extends Logger {

    private static int MAX_MESSAGE_LENGTH = 4000;

    @Override
    public void write(String str) {
      int start = 0;
      while (start < str.length()) {
        int end = Math.min(start + MAX_MESSAGE_LENGTH, str.length());
        Log.i(TAG, str.substring(start, end));
        start = end;
      }
    }

    @Override
    public void writeLine(String str) {
      // Log.i() actually write in a new line every time, so we need to rewrite it.
      write(str);
    }
  }

  private static class ProxyInputStream extends InputStream {
    // Helper stream to proxy the original input stream to other input stream
    private final InputStream originalInput;
    private final PipedInputStream proxyInput;
    private final PipedOutputStream proxyOutput;

    public ProxyInputStream(
        InputStream originalInput, final InterceptCallback callback) throws IOException {
      this.originalInput = originalInput;
      PipedInputStream tempProxyInput = new PipedInputStream();
      PipedOutputStream tempProxyOutput = null;
      try {
        tempProxyOutput = new PipedOutputStream(tempProxyInput);
      } catch (IOException e) {
        callback.done(null, e);
        ParseIOUtils.closeQuietly(tempProxyOutput);
        ParseIOUtils.closeQuietly(tempProxyInput);
        throw e;
      }
      proxyInput = tempProxyInput;
      proxyOutput = tempProxyOutput;

      // We need to make sure we read and write proxyInput/Output in separate thread, otherwise
      // there will be deadlock.
      Task.call(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          callback.done(proxyInput, null);
          return null;
        }
      }, ParseExecutors.io());
    }

    @Override
    public int read() throws IOException {
      try {
        int n = originalInput.read();
        if (n == -1) {
          ParseIOUtils.closeQuietly(proxyOutput);
        } else {
          proxyOutput.write(n);
        }
        return n;
      } catch (IOException e) {
        // If we have problems in read from original inputStream or write to the proxyOutputStream,
        // we simply close the proxy stream and throw the exception.
        ParseIOUtils.closeQuietly(proxyOutput);
        throw e;
      }
    }
  }

  private static boolean isContentTypePrintable(String contentType) {
    return (contentType != null) && (contentType.contains("json") || contentType.contains("text"));
  }

  private static boolean isGzipEncoding(ParseHttpResponse response) {
    return GZIP_ENCODING.equals(response.getHeader("Content-Encoding"));
  }

  private static String formatBytes(byte[] bytes, String contentType) {
    // We handle json separately since it is the most common body and json class provide method
    // to format it.
    if (contentType.contains("json")) {
      try {
        return new JSONObject(new String(bytes)).toString(4);
      } catch (JSONException e) {
        return new String(bytes).trim();
      }
    } else if (contentType.contains("text")) {
      return new String(bytes).trim();
    } else {
      throw new IllegalStateException("We can not print this " + contentType);
    }
  }

  private interface InterceptCallback extends ParseCallback2<InputStream, IOException> {

    @Override
    void done(InputStream proxyInputStream, IOException e);
  }


  private Logger logger;

  /* package for tests */ void setLogger(Logger logger) {
    if (this.logger == null) {
      this.logger = logger;
    } else {
      throw new IllegalStateException(
          "Another logger was already registered: " + this.logger);
    }
  }

  private Logger getLogger() {
    if (logger == null) {
      logger = new LogcatLogger();
    }
    return logger;
  }

  // Request Id generator
  private final AtomicInteger nextRequestId = new AtomicInteger(0);

  @Override
  public ParseHttpResponse intercept(Chain chain) throws IOException {
    // Intercept request
    final String requestId = String.valueOf(nextRequestId.getAndIncrement());
    ParseHttpRequest request = chain.getRequest();

    logRequestInfo(getLogger(), requestId, request);

    // Developers need to manually call this
    ParseHttpResponse tempResponse = null;
    try {
      tempResponse = chain.proceed(request);
    } catch (IOException e) {
      // Log error when we can not get response from server
      logError(getLogger(), requestId, e.getMessage());
      throw e;
    }

    final ParseHttpResponse response = tempResponse;
    InputStream newResponseBodyStream = response.getContent();
    // For response content, if developers care time of the response(latency, sending and receiving
    // time etc) or need the original networkStream to do something, they have to proxy the
    // response.
    if (isContentTypePrintable(response.getContentType())) {
      newResponseBodyStream = new ProxyInputStream(response.getContent(), new InterceptCallback() {
        @Override
        public void done(InputStream proxyInput, IOException e) {
          if (e != null) {
            return;
          }

          try {
            // This inputStream will be blocked until we write to the proxyOutputStream
            // in ProxyInputStream
            InputStream decompressedInput = isGzipEncoding(response) ?
                new GZIPInputStream(proxyInput) : proxyInput;
            ByteArrayOutputStream decompressedOutput = new ByteArrayOutputStream();
            ParseIOUtils.copy(decompressedInput, decompressedOutput);
            byte[] bodyBytes = decompressedOutput.toByteArray();
            String responseBodyInfo = formatBytes(bodyBytes, response.getContentType());
            logResponseInfo(getLogger(), requestId, response, responseBodyInfo);

            ParseIOUtils.closeQuietly(decompressedInput);
            ParseIOUtils.closeQuietly(proxyInput);
            // No need to close the byteArrayStream
          } catch (IOException e1) {
            // Log error when we can not read body stream
            logError(getLogger(), requestId, e1.getMessage());
            ParseIOUtils.closeQuietly(proxyInput);
          }
        }
      });
    } else {
      logResponseInfo(getLogger(), requestId, response, IGNORED_BODY_INFO);
    }

    //TODO(mengyan) Add builder constructor with state parameter
    return new ParseHttpResponse.Builder()
        .setContent(newResponseBodyStream)
        .setContentType(response.getContentType())
        .setHeaders(response.getAllHeaders())
        .setReasonPhase(response.getReasonPhrase())
        .setStatusCode(response.getStatusCode())
        .setTotalSize(response.getTotalSize())
        .build();
  }

  private void logRequestInfo(
      Logger logger, String requestId, ParseHttpRequest request) throws IOException {
    logger.lock();
    logger.writeLine(KEY_TYPE, TYPE_REQUEST);
    logger.writeLine(KEY_REQUEST_ID, requestId);
    logger.writeLine(KEY_URL, request.getUrl());
    logger.writeLine(KEY_METHOD, request.getMethod().toString());
    // Add missing headers
    Map<String, String> headers = new HashMap<>(request.getAllHeaders());
    if (request.getBody() != null) {
      headers.put(KEY_CONTENT_LENGTH, String.valueOf(request.getBody().getContentLength()));
      headers.put(KEY_CONTENT_TYPE, request.getBody().getContentType());
    }
    logger.writeLine(KEY_HEADERS, headers.toString());


    // Body
    if (request.getBody() != null) {
      String requestBodyInfo;
      String contentType = request.getBody().getContentType();
      if (isContentTypePrintable(contentType)) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        request.getBody().writeTo(output);
        requestBodyInfo =  formatBytes(output.toByteArray(), contentType);
      } else {
        requestBodyInfo = IGNORED_BODY_INFO;
      }
      logger.writeLine(KEY_BODY, requestBodyInfo);
    }

    logger.writeLine(LOG_PARAGRAPH_BREAKER);
    logger.unlock();
  }

  // Since we can not read the content of the response directly, we need an additional parameter
  // to pass the responseBody after we get it asynchronously.
  private void logResponseInfo(
      Logger logger, String requestId, ParseHttpResponse response, String responseBodyInfo) {
    logger.lock();
    logger.writeLine(KEY_TYPE, TYPE_RESPONSE);
    logger.writeLine(KEY_REQUEST_ID, requestId);
    logger.writeLine(KEY_STATUS_CODE, String.valueOf(response.getStatusCode()));
    logger.writeLine(KEY_REASON_PHASE, response.getReasonPhrase());
    logger.writeLine(KEY_HEADERS, response.getAllHeaders().toString());

    // Body
    if (responseBodyInfo != null) {
      logger.writeLine(KEY_BODY, responseBodyInfo);
    }

    logger.writeLine(LOG_PARAGRAPH_BREAKER);
    logger.unlock();
  }

  // Since we can not read the content of the response directly, we need an additional parameter
  // to pass the responseBody after we get it asynchronously.
  private void logError(Logger logger, String requestId, String message) {
    logger.lock();
    logger.writeLine(KEY_TYPE, TYPE_ERROR);
    logger.writeLine(KEY_REQUEST_ID, requestId);
    logger.writeLine(KEY_ERROR, message);
    logger.writeLine(LOG_PARAGRAPH_BREAKER);
    logger.unlock();
  }
}
