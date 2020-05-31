# Parse SDK Android FCM
FCM support for Parse Android apps

## Dependency

After including JitPack:
```gradle
dependencies {
    implementation "com.github.parse-community.Parse-SDK-Android:fcm:latest.version.here"
}
```
Then, follow Google's docs for [setting up an Firebase app](https://firebase.google.com/docs/android/setup). Although the steps are different for setting up FCM with Parse, it is also a good idea to read over the [Firebase FCM Setup](https://firebase.google.com/docs/cloud-messaging/android/client).

You will then need to register the messaging service in your manifest, specifically:
```xml
<service
    android:name="com.parse.fcm.ParseFirebaseMessagingService">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT"/>
    </intent-filter>
</service>
```

After these services are registered in the Manifest, you then need to register your push broadcast receiver:
```xml
<receiver
    android:name="com.parse.ParsePushBroadcastReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.parse.push.intent.RECEIVE" />
        <action android:name="com.parse.push.intent.DELETE" />
        <action android:name="com.parse.push.intent.OPEN" />
    </intent-filter>
</receiver>
```

After this, you are all set. You should see `ParseFCM: FCM registration success` messages, assuming you have enabled logging via:
```java
// be sure to disable this in the release apk
Parse.setLogLevel(Parse.LOG_LEVEL_DEBUG);
```

## Custom Notifications
If you need to customize the notification that is sent out from a push, you can do so easily by extending `ParsePushBroadcastReceiver` with your own class and registering it instead in the Manifest.

## Messaging Service
If you need to store the FCM token elsewhere outside of Parse, you can create your own implementation of the `FirebaseMessagingService`, just make sure you are either extending `ParseFirebaseMessagingService` or are calling `ParseFCM.register(token);` in the `onNewToken` method.

## License
    Copyright (c) 2015-present, Parse, LLC.
    All rights reserved.

    This source code is licensed under the BSD-style license found in the
    LICENSE file in the root directory of this source tree. An additional grant
    of patent rights can be found in the PATENTS file in the same directory.
