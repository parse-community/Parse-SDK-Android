package com.parse;

import android.content.Context;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

public class GoogleController {

    private static final String KEY_TOKEN_ID = "idToken";
    private final GoogleSdkDelegate googleSdkDelegate;

    GoogleController(GoogleSdkDelegate googleSdkDelegate) {
        this.googleSdkDelegate = googleSdkDelegate;
    }

    public GoogleController() {
        this(new GoogleSdkDelegateImpl());
    }

    public void initialize(Context context, int callbackRequestCodeOffset) {
        this.googleSdkDelegate.initialize(context, callbackRequestCodeOffset);
    }

    public Map<String, String> getAuthData(String idToken) {
        HashMap authData = new HashMap();
        authData.put(KEY_TOKEN_ID, idToken);
        return authData;
    }

    public void setAuthData(Map<String, String> authData) throws ParseException {
        if (authData == null) {
            //what we do in this case?
        } else {
            String idToken = authData.get(KEY_TOKEN_ID);
        }
    }

    private static class GoogleSdkDelegateImpl implements GoogleSdkDelegate {
        private GoogleSdkDelegateImpl() {

        }

        @Override
        public void initialize(Context var1, int var2) {

        }
    }

    interface GoogleSdkDelegate {
        void initialize(Context var1, int var2);
    }
}
