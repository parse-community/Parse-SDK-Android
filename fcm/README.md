# Parse SDK Android FCM
FCM support for Parse Android apps

## Setup

### Installation

Add dependency to the application level `build.gradle` file.

```groovy
dependencies {
    implementation 'com.parse:parse-android:latest.version.here'
    implementation 'com.parse:parse-android-fcm:latest.version.here'
}
```
Then, follow Google's docs for [setting up an FCM app](https://firebase.google.com/docs/cloud-messaging/android/client).

## Note
If you need access to the FCM token, instead of creating a service that extends `FirebaseInstanceIdService`, extend `ParseFirebaseInstanceIDService` to assure the token still gets saved properly within Parse.

## License
    Copyright (c) 2015-present, Parse, LLC.
    All rights reserved.

    This source code is licensed under the BSD-style license found in the
    LICENSE file in the root directory of this source tree. An additional grant
    of patent rights can be found in the PATENTS file in the same directory.