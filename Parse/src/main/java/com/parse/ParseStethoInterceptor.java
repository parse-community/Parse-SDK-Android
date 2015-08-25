package com.parse;

import com.facebook.stetho.inspector.network.DefaultResponseHandler;
import com.facebook.stetho.inspector.network.NetworkEventReporter;
import com.facebook.stetho.inspector.network.NetworkEventReporterImpl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

/** package */ class ParseStethoInterceptor implements ParseNetworkInterceptor {

  private static final String CONTENT_LENGTH_HEADER = "Content-Length";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";

  // Implementation of Stetho request
  private static class ParseInterceptorHttpRequest
      implements NetworkEventReporter.InspectorRequest {
    private final String requestId;
    private final ParseHttpRequest request;
    private byte[] body;
    private boolean hasGeneratedBody;
    // Since stetho use index to get header, we use a list to store them
    private List<String> headers;

    public ParseInterceptorHttpRequest(String requestId, ParseHttpRequest request) {
      this.requestId = requestId;
      this.request = request;

      // Add content-length and content-type header to the interceptor. These headers are added when
      // a real httpclient send the request. Since we still want users to see these headers, we
      // manually add them to Interceptor request if they are not in the header list.
      headers = new ArrayList<>();
      for (Map.Entry<String, String> headerEntry : request.getAllHeaders().entrySet()) {
        headers.add(headerEntry.getKey());
        headers.add(headerEntry.getValue());
      }
      if (request.getBody() != null) {
        if (!headers.contains(CONTENT_LENGTH_HEADER)) {
          headers.add(CONTENT_LENGTH_HEADER);
          headers.add(String.valueOf(request.getBody().getContentLength()));
        }
        // If user does not set contentType when create ParseFile, it may be null
        if (request.getBody().getContentType() != null && !headers.contains(CONTENT_TYPE_HEADER)) {
          headers.add(CONTENT_TYPE_HEADER);
          headers.add(request.getBody().getContentType());
        }
      }
    }

    @Override
    public String id() {
      return requestId;
    }

    @Override
    public String friendlyName() {
      return null;
    }

    @Nullable
    @Override
    public Integer friendlyNameExtra() {
      return null;
    }

    @Override
    public String url() {
      return request.getUrl();
    }

    @Override
    public String method() {
      return request.getMethod().toString();
    }

    @Nullable
    @Override
    public byte[] body() throws IOException {
      if (!hasGeneratedBody) {
        hasGeneratedBody = true;
        body = generateBody();
      }
      return body;
    }

    private byte[] generateBody() throws IOException {
      ParseHttpBody body = request.getBody();
      if (body == null) {
        return null;
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      body.writeTo(out);
      return out.toByteArray();
    }

    @Override
    public int headerCount() {
      return headers.size() / 2;
    }

    @Override
    public String headerName(int index) {
      return headers.get(index * 2);
    }

    @Override
    public String headerValue(int index) {
      return headers.get(index * 2 + 1);
    }

    @Nullable
    @Override
    public String firstHeaderValue(String name) {
      int index = headers.indexOf(name);
      return index >= 0 ? headers.get(index + 1) : null;
    }
  }

  // Implementation of Stetho response
  private static class ParseInspectorHttpResponse
      implements NetworkEventReporter.InspectorResponse {
    private final String requestId;
    private final ParseHttpRequest request;
    private final ParseHttpResponse response;
    // Since stetho use index to get header, we use a list to store them
    private List<String> responseHeaders;

    public ParseInspectorHttpResponse(
        String requestId,
        ParseHttpRequest request,
        ParseHttpResponse response) {
      this.requestId = requestId;
      this.request = request;
      this.response = response;
      responseHeaders = new ArrayList<>();
      for (Map.Entry<String, String> headerEntry : response.getAllHeaders().entrySet()) {
        responseHeaders.add(headerEntry.getKey());
        responseHeaders.add(headerEntry.getValue());
      }
    }

    @Override
    public String requestId() {
      return requestId;
    }

    @Override
    public String url() {
      return request.getUrl();
    }

    @Override
    public int statusCode() {
      return response.getStatusCode();
    }

    @Override
    public String reasonPhrase() {
      return response.getReasonPhrase();
    }

    @Override
    public boolean connectionReused() {
      // Follow stetho URLConnectionInspectorResponse
      return false;
    }

    @Override
    public int connectionId() {
      // Follow stetho URLConnectionInspectorResponse
      return requestId.hashCode();
    }

    @Override
    public boolean fromDiskCache() {
      // Follow stetho URLConnectionInspectorResponse
      return false;
    }

    @Override
    public int headerCount() {
      return response.getAllHeaders().size();
    }

    @Override
    public String headerName(int index) {
      return responseHeaders.get(index * 2);
    }

    @Override
    public String headerValue(int index) {
      return responseHeaders.get(index * 2 + 1);
    }

    @Nullable
    @Override
    public String firstHeaderValue(String name) {
      int index = responseHeaders.indexOf(name);
      return index >= 0 ? responseHeaders.get(index + 1) : null;
    }
  }

  // Stetho reporter
  private final NetworkEventReporter stethoEventReporter = NetworkEventReporterImpl.get();

  // Request Id generator
  private final AtomicInteger nextRequestId = new AtomicInteger(0);

  @Override
  public ParseHttpResponse intercept(Chain chain) throws IOException {
    // Intercept request
    String requestId = String.valueOf(nextRequestId.getAndIncrement());
    ParseHttpRequest request = chain.getRequest();

    // If stetho debugger is available (chrome debugger is open), intercept the request.
    if (stethoEventReporter.isEnabled()) {
      ParseInterceptorHttpRequest inspectorRequest =
          new ParseInterceptorHttpRequest(requestId, chain.getRequest());
      stethoEventReporter.requestWillBeSent(inspectorRequest);
    }


    ParseHttpResponse response;
    try {
      response = chain.proceed(request);
    } catch (IOException e) {
      // If stetho debugger is available (chrome debugger is open), intercept the exception.
      if (stethoEventReporter.isEnabled()) {
        stethoEventReporter.httpExchangeFailed(requestId, e.toString());
      }
      throw e;
    }

    if (stethoEventReporter.isEnabled()) {
      // If stetho debugger is available (chrome debugger is open), intercept the response body.
      if (request.getBody() != null) {
        stethoEventReporter.dataSent(requestId, request.getBody().getContentLength(),
            request.getBody().getContentLength());
      }

      // If stetho debugger is available (chrome debugger is open), intercept the response header
      stethoEventReporter.responseHeadersReceived(
          new ParseInspectorHttpResponse(requestId, request, response));

      InputStream responseStream = null;
      if (response.getContent() != null) {
        responseStream = response.getContent();
      }

      // Create the stetho proxy inputStream, when Parse read this stream, it will proxy the
      // response body to Stetho reporter.
      responseStream = stethoEventReporter.interpretResponseStream(
          requestId,
          response.getContentType(),
          response.getAllHeaders().get("Content-Encoding"),
          responseStream,
          new DefaultResponseHandler(stethoEventReporter, requestId)
      );
      if (responseStream != null) {
        response = new ParseHttpResponse.Builder()
            .setTotalSize(response.getTotalSize())
            .setContentType(response.getContentType())
            .setHeaders(response.getAllHeaders())
            .setReasonPhase(response.getReasonPhrase())
            .setStatusCode(response.getStatusCode())
            .setContent(responseStream)
            .build();
      }
    }
    return response;
  }
}
