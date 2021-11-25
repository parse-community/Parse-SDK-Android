![parse-repository-header-sdk-android](https://user-images.githubusercontent.com/5673677/138284986-844b692c-d976-4370-a840-0ada5de8a8bf.png)

---

[![Build Status](https://github.com/parse-community/Parse-SDK-Android/workflows/ci/badge.svg?branch=master)](https://github.com/parse-community/Parse-SDK-Android/actions?query=workflow%3Aci+branch%3Amaster)
[![Snyk Badge](https://snyk.io/test/github/parse-community/Parse-SDK-Android/badge.svg)](https://snyk.io/test/github/parse-community/Parse-SDK-Android)
[![codecov](https://codecov.io/gh/parse-community/Parse-SDK-Android/branch/master/graph/badge.svg)](https://codecov.io/gh/parse-community/Parse-SDK-Android)

[![android min api](https://img.shields.io/badge/Android_API->=21-66c718.svg)](https://github.com/parse-community/parse-dashboard/releases)
[![auto-release](https://img.shields.io/badge/%F0%9F%9A%80-auto--release-9e34eb.svg)](https://github.com/parse-community/parse-dashboard/releases)

[![](https://jitpack.io/v/parse-community/Parse-SDK-Android.svg)](https://jitpack.io/#parse-community/Parse-SDK-Android)
[![](https://jitpack.io/v/parse-community/Parse-SDK-Android/month.svg)](https://jitpack.io/#parse-community/Parse-SDK-Android)

[![Backers on Open Collective](https://opencollective.com/parse-server/backers/badge.svg)][open-collective-link]
[![Sponsors on Open Collective](https://opencollective.com/parse-server/sponsors/badge.svg)][open-collective-link]
[![License](https://img.shields.io/badge/license-BSD-lightgrey.svg)](https://github.com/parse-community/Parse-SDK-Android/blob/master/LICENSE)
[![Forum](https://img.shields.io/discourse/https/community.parseplatform.org/topics.svg)](https://community.parseplatform.org/c/parse-server)
[![Twitter Follow](https://img.shields.io/twitter/follow/ParsePlatform.svg?label=Follow%20us%20on%20Twitter&style=social)](https://twitter.com/intent/follow?screen_name=ParsePlatform)

---

A library that gives you access to the powerful Parse Server backend from your Android app. For more information about Parse and its features, see [the website](https://parseplatform.org/), [getting started][guide], and [blog](https://blog.parseplatform.org/).

---

- [Getting Started](#getting-started)
  - [Compatibility](#compatibility)
  - [Add Dependency](#add-dependency)
  - [Setup](#setup)
- [Contributing](#contributing)
- [More Parse Android Projects](#more-parse-android-projects)

# Getting Started

## Compatibility

The Parse Android SDK has the following Android API and [Gradle Plugin][gradle-plugin] compatibility.

| SDK version | Minimum API level | Targeting API level | Gradle Plugin |
|-------------|-------------------|---------------------|---------------|
| 1.26        | < API 16          | API 29              | 3.6.2         |
| 2.0         | >= API 16         | API 30              | 4.2.2         |
| 2.1         | >= API 21         | API 31              | 7.0.3         |

## Add Dependency

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
ext {
   parseVersion = "latest.version.here"
}
dependencies {
    implementation "com.github.parse-community.Parse-SDK-Android:parse:$parseVersion"
    // for Google login/signup support (optional)
    implementation "com.github.parse-community.Parse-SDK-Android:google:$parseVersion"
    // for Facebook login/signup support (optional)
    implementation "com.github.parse-community.Parse-SDK-Android:facebook:$parseVersion"
    // for Twitter login/signup support (optional)
    implementation "com.github.parse-community.Parse-SDK-Android:twitter:$parseVersion"
    // for FCM Push support (optional)
    implementation "com.github.parse-community.Parse-SDK-Android:fcm:$parseVersion"
    // for Kotlin extensions support (optional)
    implementation "com.github.parse-community.Parse-SDK-Android:ktx:$parseVersion"
    // for Kotlin coroutines support (optional)
    implementation "com.github.parse-community.Parse-SDK-Android:coroutines:$parseVersion"
    // for RxJava support (optional)
    implementation "com.github.parse-community.Parse-SDK-Android:rxjava:$parseVersion"
}
```

replacing `latest.version.here` with the latest released version (see JitPack badge above).

## Setup

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
        .server("https://your-server-address/parse/")
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

Note that if you are testing with a server using `http`, you will need to add `android:usesCleartextTraffic="true"` to your above `<application>` definition, but you should only do this while testing and should use `https` for your final product.

See the [guide][guide] for the rest of the SDK usage.

# Contributing

We want to make contributing to this project as easy and transparent as possible. Please refer to the [Contribution Guidelines](CONTRIBUTING.md).

# More Parse Android Projects

These are other official libraries we made that can help you create your Parse app.

- [ParseGoogleUtils](/google) - Google login/signup.
- [ParseFacebookUtils](/facebook) - Facebook login/signup.
- [ParseTwitterUtils](/twitter) - Twitter login/signup.
- [Parse FCM](/fcm) - [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging) support for sending push notifications.
- [Parse KTX](/ktx) - Kotlin extensions for ease of developer use.
- [Parse Coroutines](/coroutines) - Kotlin Coroutines support for various Parse async operations
- [Parse RxJava](/rxjava) - Transform Parse `Task`s to RxJava `Completable`s and `Single`s
- [ParseLiveQuery](https://github.com/parse-community/ParseLiveQuery-Android) - Realtime query subscription.
- [ParseUI](https://github.com/parse-community/ParseUI-Android) - Prebuilt UI elements.

---

As of April 5, 2017, Parse, LLC has transferred this code to the parse-community organization, and will no longer be contributing to or distributing this code.

[guide]: http://docs.parseplatform.org/android/guide/
[open-collective-link]: https://opencollective.com/parse-server
[gradle-plugin]: https://developer.android.com/studio/releases/gradle-plugin
