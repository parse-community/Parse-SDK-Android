package com.parse.gcm;

import android.os.Bundle;

import com.google.android.gms.gcm.GcmListenerService;
import com.parse.PLog;
import com.parse.PushRouter;

import org.json.JSONException;
import org.json.JSONObject;

public class ParseGCMListenerService extends GcmListenerService {

    @Override
    public void onMessageReceived(String s, Bundle bundle) {
        super.onMessageReceived(s, bundle);
        String pushId = bundle.getString("push_id");
        String timestamp = bundle.getString("time");
        String dataString = bundle.getString("data");
        String channel = bundle.getString("channel");

        JSONObject data = null;
        if (dataString != null) {
            try {
                data = new JSONObject(dataString);
            } catch (JSONException e) {
                PLog.e(ParseGCM.TAG, "Ignoring push because of JSON exception while processing: " + dataString, e);
                return;
            }
        }

        PushRouter.getInstance().handlePush(pushId, timestamp, channel, data);
    }
}
