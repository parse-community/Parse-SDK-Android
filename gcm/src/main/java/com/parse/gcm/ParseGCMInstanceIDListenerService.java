package com.parse.gcm;

import com.google.android.gms.iid.InstanceIDListenerService;

/**
 * Listens for GCM token refreshes and kicks off a background job to save the token
 */
public class ParseGCMInstanceIDListenerService extends InstanceIDListenerService {

    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();
        ParseGCM.register(getApplicationContext());
    }
}
