<p align="center">
    <img alt="Parse Platform" src="Assets/logo large.png" width="200">
  </a>
</p>

<h2 align="center">Parse SDK for Android</h2>

<p align="center">
    A library that gives you access to the powerful Parse Server backend from your Android app.
</p>

<p align="center">
    <a href="https://twitter.com/intent/follow?screen_name=parseplatform"><img alt="Follow on Twitter" src="https://img.shields.io/twitter/follow/parseplatform?style=social&label=Follow"></a>
    <a href="https://community.parseplatform.org/"><img alt="Join the conversation" src="https://img.shields.io/discourse/https/community.parseplatform.org/topics.svg"></a>
    <a href="https://github.com/parse-community/Parse-SDK-Android/blob/master/LICENSE"><img alt="License" src="https://img.shields.io/badge/license-BSD-lightgrey.svg"></a>
    <a href=" https://travis-ci.org/parse-community/Parse-SDK-Android"><img alt="Build status" src="https://travis-ci.org/parse-community/Parse-SDK-Android.svg?branch=master"></a>
</p>

<p align="center">
   <a href="https://jitpack.io/#parse-community/Parse-SDK-Android"><img alt="Jitpack" src="https://jitpack.io/v/parse-community/Parse-SDK-Android.svg"></a>
   <a href=" https://travis-ci.org/parse-community/Parse-SDK-Android"><img alt="Build status" src="https://travis-ci.org/parse-community/Parse-SDK-Android.svg?branch=master"></a>
   <a href="#backers"><img alt="Backers on Open Collective" src="https://opencollective.com/parse-server/backers/badge.svg" /></a>
   <a href="#sponsors"><img alt="Sponsors on Open Collective" src="https://opencollective.com/parse-server/sponsors/badge.svg" /></a>
</p>
<br>

For more information about Parse and its features, see [the website](https://parseplatform.org/), [getting started][guide], and [blog](https://blog.parseplatform.org/).

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
replacing `latest.version.here` with the latest released version (see JitPack badge above)

### Setup
Initialize Parse in a custom class that extends `Application`:
```java
import com.parse.Parse;
import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
      super.onCreate();

      Parse.initialize(new Parse.Configuration.Builder(this)
        .applicationId("YOUR_APP_ID")
        // if desired
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

See the [guide][guide] for the rest of the SDK usage.

## How Do I Contribute?
We want to make contributing to this project as easy and transparent as possible. Please refer to the [Contribution Guidelines](CONTRIBUTING.md).

## Other Parse Projects

 - [Parse FCM](/fcm)
 - [Parse KTX](/ktx)
 - [ParseUI](https://github.com/parse-community/ParseUI-Android)
 - [ParseLiveQuery](https://github.com/parse-community/ParseLiveQuery-Android)
 - [ParseFacebookUtils](https://github.com/parse-community/ParseFacebookUtils-Android)
 - [ParseTwitterUtils](https://github.com/parse-community/ParseTwitterUtils-Android)

## License
    Copyright (c) 2015-present, Parse, LLC.
    All rights reserved.

    This source code is licensed under the BSD-style license found in the
    LICENSE file in the root directory of this source tree. An additional grant
    of patent rights can be found in the PATENTS file in the same directory.

-----

As of April 5, 2017, Parse, LLC has transferred this code to the parse-community organization, and will no longer be contributing to or distributing this code.

 [guide]: http://docs.parseplatform.org/android/guide/
 [open-collective-link]: https://opencollective.com/parse-server
