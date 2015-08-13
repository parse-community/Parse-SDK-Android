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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

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
  private final static String TYPE_REQUEST = "Request";
  private final static String TYPE_RESPONSE = "Response";

  private final static String IGNORED_BODY_INFO = "Ignored";

  private static abstract class Logger {
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

    @Override
    public void write(String str) {
      Log.i(TAG, str);
    }

    @Override
    public void writeLine(String str) {
      // Log.i() actually write in a new line every time, so we need to rewrite it.
      write(str);
    }
  }

  private static class ProxyInputStream extends InputStream {

    private ParseHttpResponse response;
    private String requestId;
    private boolean hasBeenPrinted;
    private ByteArrayOutputStream bodyOutput;
    private Logger logger;
    private boolean needsToBePrinted;

    public ProxyInputStream(String requestId, ParseHttpResponse response, Logger logger)
        throws FileNotFoundException {
      this.requestId = requestId;
      this.response = response;
      this.logger = logger;
      bodyOutput = new ByteArrayOutputStream();
      needsToBePrinted = isContentTypePrintable(response.getContentType());
    }

    @Override
    public int read() throws IOException {
      int n = response.getContent().read();
      if (n == -1) {
        // We need this flag because it is unsafe to just use -1 to decide whether to print or not.
        // read() always return -1 after it hits the end of the stream.
        if (!hasBeenPrinted) {
          hasBeenPrinted = true;
          if (needsToBePrinted) {
            bodyOutput.write(n);
            bodyOutput.close();

            byte[] bodyBytes = bodyOutput.toByteArray();
            String responseBodyInfo = formatBytes(bodyBytes, response.getContentType());
            logResponseInfo(requestId, response, responseBodyInfo);
          } else {
            logResponseInfo(requestId, response, IGNORED_BODY_INFO);
          }
        }
      } else {
        if (needsToBePrinted) {
          bodyOutput.write(n);
        }
      }
      return n;
    }

    private void logResponseInfo(final String requestId,
        final ParseHttpResponse response, final String responseBodyInfo) {
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

  private static boolean isContentTypePrintable(String contentType) {
    return contentType.contains("json") || contentType.contains("text");
  }

  // Request Id generator
  private final AtomicInteger nextRequestId = new AtomicInteger(0);

  private Logger logger;

  public Logger getLogger() {
    if (logger == null) {
      logger = new LogcatLogger();
    }
    return logger;
  }

  @Override
  public ParseHttpResponse intercept(Chain chain) throws IOException {
    // Intercept request
    String requestId = String.valueOf(nextRequestId.getAndIncrement());
    ParseHttpRequest request = chain.getRequest();

    String requestBodyInfo = getRequestBodyInfo(request);
    logRequestInfo(requestId, request, requestBodyInfo);

    // Developers need to manually call this
    ParseHttpResponse response = chain.proceed(request);

    // For response content, if developers care time of the response(latency, sending and receiving
    // time etc) or need the original networkStream to do something, they have to proxy the
    // response.
    //TODO(mengyan) Add builder constructor with state parameter
    return new ParseHttpResponse.Builder()
        .setContent(new ProxyInputStream(requestId, response, getLogger()))
        .setContentType(response.getContentType())
        .setHeaders(response.getAllHeaders())
        .setReasonPhase(response.getReasonPhrase())
        .setStatusCode(response.getStatusCode())
        .setTotalSize(response.getTotalSize())
        .build();
  }

  private void logRequestInfo(final String requestId,
      final ParseHttpRequest request, final String requestBodyInfo) throws IOException {
    Logger logger = getLogger();
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
    if (requestBodyInfo != null) {
      logger.writeLine(KEY_BODY, requestBodyInfo);
    }

    logger.writeLine(LOG_PARAGRAPH_BREAKER);
    logger.unlock();
  }

  private String getRequestBodyInfo(ParseHttpRequest request)
      throws IOException {
    if (request.getBody() == null) {
      return null;
    }

    String requestContentType = request.getBody().getContentType();
    if (isContentTypePrintable(requestContentType)) {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      request.getBody().writeTo(output);
      return formatBytes(output.toByteArray(), requestContentType);
    } else {
      return IGNORED_BODY_INFO;
    }
  }
}
