/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse.twitter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatDialog;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

/**
 * For internal use.
 */
class OAuth1FlowDialog extends AppCompatDialog {

    private final String callbackUrl;
    private final String requestUrl;
    private final String serviceUrlIdentifier;
    private final FlowResultHandler handler;

    private WebView webView;
    private ProgressBar progress;

    OAuth1FlowDialog(Context context, String requestUrl, String callbackUrl, String serviceUrlIdentifier, FlowResultHandler resultHandler) {
        super(context);
        this.requestUrl = requestUrl;
        this.callbackUrl = callbackUrl;
        this.serviceUrlIdentifier = serviceUrlIdentifier;
        this.handler = resultHandler;
        this.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                handler.onCancel();
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.parse_twitter_dialog_login);
        webView = findViewById(R.id.webView);
        progress = findViewById(R.id.progress);

        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setWebViewClient(new OAuth1WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl(requestUrl);
    }

    public interface FlowResultHandler {
        /**
         * Called when the user cancels the dialog.
         */
        void onCancel();

        /**
         * Called when the dialog's web view receives an error.
         */
        void onError(int errorCode, String description, String failingUrl);

        /**
         * Called when the dialog portion of the flow is complete.
         *
         * @param callbackUrl The final URL called back (including any query string appended
         *                    by the server).
         */
        void onComplete(String callbackUrl);
    }

    private class OAuth1WebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith(callbackUrl)) {
                OAuth1FlowDialog.this.dismiss();
                handler.onComplete(url);
                return true;
            } else if (url.contains(serviceUrlIdentifier)) {
                return false;
            }
            // launch non-service URLs in a full browser
            getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return true;
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            OAuth1FlowDialog.this.dismiss();
            handler.onError(errorCode, description, failingUrl);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            progress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progress.setVisibility(View.GONE);
        }
    }
}
