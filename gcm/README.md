# Parse SDK Android GCM
GCM support for Parse Android apps

## Deprecated
Please note that GCM is deprecated in favor of FCM. This module exists as a backwards compatible solution for projects already using GCM. New apps should instead use FCM.

## Setup

### Installation
After including JitPack:

```groovy
dependencies {
    implementation "com.github.parse-community.Parse-SDK-Android:parse:latest.version.here"
    implementation "com.github.parse-community.Parse-SDK-Android:gcm:latest.version.here"
}
```

You will then need to register some things in your manifest, firstly, the GCM sender ID:
```xml
<meta-data
    android:name="com.parse.push.gcm_sender_id"
    android:value="id:YOUR_SENDER_ID_HERE" />
```
The sender ID should be all numbers. Make sure you are keeping the `id:` in the front.

Next you will register the GcmReceiver:
```xml
<receiver
    android:name="com.google.android.gms.gcm.GcmReceiver"
    android:exported="true"
    android:permission="com.google.android.c2dm.permission.SEND" >
    <intent-filter>
        <action android:name="com.google.android.c2dm.intent.RECEIVE" />
        <category android:name="${packageName}" />
    </intent-filter>
</receiver>
```
And to listen for the pushes from GCM:
```xml
<service
    android:name="com.parse.gcm.ParseGCMListenerService"
    android:exported="false" >
    <intent-filter>
        <action android:name="com.google.android.c2dm.intent.RECEIVE" />
    </intent-filter>
</service>
```
And finally, to register the device for GCM pushes:
```xml
<service
    android:name="com.parse.gcm.ParseGCMInstanceIDListenerService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.android.gms.iid.InstanceID" />
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

After this, you are all set. Adding the `parse-gcm-android` package will include a [ParseGCMJobService](https://github.com/parse-community/Parse-SDK-Android/blob/master/gcm/src/main/java/com/parse/gcm/ParseGCMJobService.java) in the `AndroidManifest.xml` file that will register for a GCM token when the app starts.  You should see `ParseGCM: GCM registration success` messages assuming you have enabled logging:

```java
Parse.setLogLevel(Parse.LOG_LEVEL_DEBUG);
```

## Custom Notifications
If you need to customize the notification that is sent out from a push, you can do so easily by extending `ParsePushBroadcastReceiver` with your own class and registering it instead in the Manifest.

## License
    Copyright (c) 2015-present, Parse, LLC.
    All rights reserved.

    This source code is licensed under the BSD-style license found in the
    LICENSE file in the root directory of this source tree. An additional grant
    of patent rights can be found in the PATENTS file in the same directory.
