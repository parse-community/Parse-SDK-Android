# Parse SDK for Android

[![Bintray][bintray-svg]][bintray-link]
[![License][license-svg]][license-link]

[![Build Status][build-status-svg]][build-status-link]
[![Coverage Status][coverage-status-svg]][coverage-status-link]

[![Join Chat](https://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/ParsePlatform/Chat)

A library that gives you access to the powerful Parse cloud platform from your Android app.
For more information about Parse and its features, see [the website][parseplatform.org], [blog][blog] and [getting started][guide].

## Getting Started

### Installation
- **Option 1:** Gradle

  Add dependency to the application level `build.gradle` file.

  ```groovy
  ext {
    parseVersion = "1.17.0"
  }
  ```

  ```groovy
  dependencies {
    implementation "com.parse:parse-android:$parseVersion"

    // Add for push notification support -- add FCM or GCM but not both.
    implementation "com.parse:parse-fcm-android:$parseVersion" // migrate to FCM
    // implementation "com.parse:parse-gcm-android:$parseVersion" // deprecated GCM support

  }
  ```

  #### Migrating to Firebase  

  If you are upgrading from a previous Parse SDK version and rely on push notifications, it is highly recommended you migrate to Firebase Cloud Messaging (FCM) since Google has announced it will be deprecated in April 2019.  To migrate to FCM, you will only need to make changes to the client.  No changes are needed on the Parse Server side.

  Verify you have done the following:

  - [ ] Added app to [Firebase console](https://console.firebase.google.com/u/0/).
  - [ ] Added the `com.google.gms.google-services` Gradle plugin (see [setup guide](https://firebase.google.com/docs/android/setup))

      ```groovy
      buildscript {
          // ...
          dependencies {
              // ...
              classpath 'com.google.gms:google-services:3.2.1' // google-services plugin
          }
      }

      allprojects {
          // ...
          repositories {
              // ...
              maven {
                  google() // Google's Maven repository
              }
          }
      }
      ```

  - [ ] Downloaded and added [google-services.json](https://support.google.com/firebase/answer/7015592) to your `app/` dir from your Firebase app.
  - [ ] Added `ParseFirebaseInstanceIdService` and `ParseFirebaseMessagingService` to your `AndroidManifest.xml` file (see [docs](https://github.com/parse-community/Parse-SDK-Android/blob/master/fcm/README.md))
  - [ ] Removed `GcmBroadcastReceiver`, `PushService`, `com.parse.push.gcm_sender_id` if upgrading from GCM.

  Assuming these major steps are done, adding the `parse-fcm-android` package will automatically instantiate a [ParseFirebaseJobService](https://github.com/parse-community/Parse-SDK-Android/blob/master/fcm/src/main/java/com/parse/fcm/ParseFirebaseJobService.java) that will register for a FCM token when the app starts.   See the setup instructions below to verify that FCM registration works.

- **Option 2:** Compiling for yourself into AAR file

  If you want to manually compile the SDK, begin by cloning the repository locally or retrieving the source code for a particular [release][releases]. Open the project in Android Studio and run the following commands in the Terminal of Android Studio:

  ```
  ./gradlew clean build
  ```
  Output file can be found in `Parse/build/outputs/` with extension .aar

  You can link to your project to your AAR file as you please.


### Setup
Initialize Parse in a custom class that extends `Application`:
```java
  import com.parse.Parse;
  import android.app.Application;

  public class App extends Application {
    @Override
    public void onCreate() {
      super.onCreate();

      // Remove for production, use to verify FCM is working
      // Look for ParseFCM: FCM registration success messages in Logcat to confirm.
      Parse.setLogLevel(Parse.LOG_LEVEL_DEBUG);

      Parse.initialize(new Parse.Configuration.Builder(this)
        .applicationId("YOUR_APP_ID")
        .clientKey("YOUR_CLIENT_KEY")
        .server("http://localhost:1337/parse/")
        .build()
      );
    }
  }
```

The custom `Application` class must be registered in `AndroidManifest.xml`:

```xml
<application
    android:name=".App"
    ...>
    ...
</application>
```

## Usage
Everything can done through the supplied gradle wrapper:

### Run the Tests
```
./gradlew clean testDebug
```
Results can be found in `Parse/build/reports/`

### Get Code Coverage Reports
```
./gradlew clean jacocoTestReport
```
Results can be found in `Parse/build/reports/`

## Snapshots

Snapshots of the development version can be obtained by using [Jitpack][jitpack-snapshot-link]:

Add the Maven link in your root `build.gradle` file:

  ```groovy
  allprojects {
    repositories {
      maven { url 'https://jitpack.io' }
    }
  }					      
  ```

Add the dependency to your `app/build.gradle`:

  ```groovy
  dependencies {
    implementation 'com.github.parse-community:Parse-SDK-Android:master-SNAPSHOT'
  }
  ```

## How Do I Contribute?
We want to make contributing to this project as easy and transparent as possible. Please refer to the [Contribution Guidelines][contributing].

## Other Parse Projects

 - [ParseUI for Android][parseui-link]
 - [ParseLiveQuery for Android][parselivequery-link]
 - [ParseFacebookUtils for Android][parsefacebookutils-link]
 - [ParseTwitterUtils for Android][parsetwitterutils-link]

## License
    Copyright (c) 2015-present, Parse, LLC.
    All rights reserved.

    This source code is licensed under the BSD-style license found in the
    LICENSE file in the root directory of this source tree. An additional grant
    of patent rights can be found in the PATENTS file in the same directory.

-----

As of April 5, 2017, Parse, LLC has transferred this code to the parse-community organization, and will no longer be contributing to or distributing this code.

 [parseplatform.org]: http://parseplatform.org/
 [blog]: http://blog.parse.com/
 [guide]: http://docs.parseplatform.org/android/guide/

 [latest]: https://search.maven.org/remote_content?g=com.parse&a=parse-android&v=LATEST
 [snap]: https://oss.jfrog.org/artifactory/oss-snapshot-local/com/parse/parse-android/

 [bintray-svg]: https://api.bintray.com/packages/parse/maven/com.parse:parse-android/images/download.svg
 [bintray-link]: https://bintray.com/parse/maven/com.parse:parse-android

 [jitpack-snapshot-link]: https://jitpack.io/#parse-community/Parse-SDK-Android/master-SNAPSHOT

 [license-svg]: https://img.shields.io/badge/license-BSD-lightgrey.svg
 [license-link]: https://github.com/parse-community/Parse-SDK-Android/blob/master/LICENSE

 [build-status-svg]: https://travis-ci.org/parse-community/Parse-SDK-Android.svg?branch=master
 [build-status-link]: https://travis-ci.org/parse-community/Parse-SDK-Android

 [coverage-status-svg]: https://img.shields.io/codecov/c/github/parse-community/Parse-SDK-Android/master.svg
 [coverage-status-link]: https://codecov.io/github/parse-community/Parse-SDK-Android?branch=master

 [parseui-link]: https://github.com/parse-community/ParseUI-Android
 [parselivequery-link]: https://github.com/parse-community/ParseLiveQuery-Android

 [parsefacebookutils-link]: https://github.com/parse-community/ParseFacebookUtils-Android
 [parsetwitterutils-link]: https://github.com/parse-community/ParseTwitterUtils-Android

 [releases]: https://github.com/parse-community/Parse-SDK-Android/releases
 [contributing]: CONTRIBUTING.md
