package com.parse.fcm;

import android.content.Intent;
import android.os.Bundle;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.parse.InternalParseFCMParseAccess;
import com.parse.ParsePushBroadcastReceiver;

public class ParseFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        InternalParseFCMParseAccess.logVerbose("onMessageReceived");
        //example of json data:
        //{"alert":"this is to everyone","sound":"default","title":"Everyone","content-available":1}
        String channel = remoteMessage.getData().get("channel");
        String data = remoteMessage.getData().get("data");
        Bundle extras = new Bundle();
        extras.putString(ParsePushBroadcastReceiver.KEY_PUSH_CHANNEL, channel);
        if (data == null) {
            extras.putString(ParsePushBroadcastReceiver.KEY_PUSH_DATA, "{}");
        } else {
            extras.putString(ParsePushBroadcastReceiver.KEY_PUSH_DATA, data);
        }

        Intent intent = new Intent(ParsePushBroadcastReceiver.ACTION_PUSH_RECEIVE);
        intent.putExtras(extras);

        // Set the package name to keep this intent within the given package.
        intent.setPackage(getApplicationContext().getPackageName());
        getApplicationContext().sendBroadcast(intent);

    }
}
