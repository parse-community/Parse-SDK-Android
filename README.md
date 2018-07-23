# Parse SDK for Android

[![License][license-svg]][license-link] [![Build Status][build-status-svg]][build-status-link] [![Join Chat](https://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/ParsePlatform/Chat) [![](https://jitpack.io/v/parse-community/Parse-SDK-Android.svg)](https://jitpack.io/#parse-community/Parse-SDK-Android)

A library that gives you access to the powerful Parse cloud platform from your Android app.
For more information about Parse and its features, see [the website][parseplatform.org], [blog][blog] and [getting started][guide].

## Dependency

Add this in your root `build.gradle` file (**not** your module `build.gradle` file):

```gradle
allprojects {
	repositories {
		...
		maven { url "https://jitpack.io" }
	}
}
```

Then, add the library to your project `build.gradle`
```gradle
dependencies {
    implementation "com.github.parse-community.Parse-SDK-Android:parse:latest.version.here"
    // for FCM Push support (optional)
    implementation "com.github.parse-community.Parse-SDK-Android:fcm:latest.version.here"
    // for Kotlin extensions support (optional)
    implementation "com.github.parse-community.Parse-SDK-Android:ktx:latest.version.here"
}
```

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

See the [guide](http://docs.parseplatform.org/android/guide/) for the rest of the SDK usage.

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
 [blog]: http://blog.parseplatform.org/
 [guide]: http://docs.parseplatform.org/android/guide/

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
