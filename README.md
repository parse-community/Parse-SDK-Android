# Parse SDK for Android

[![Maven Central][maven-svg]][maven-link]
[![Dependencies][dependencies-svg]][dependencies-link]
[![References][references-svg]][references-link]
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
  dependencies {
    compile 'com.parse:parse-android:1.16.3'
  }
  ```

  Snapshots of the development version are available in [jFrog's `snapshots` repository][snap].

- **Option 2:** Compiling for yourself into AAR file

  If you want to manually compile the SDK, begin by cloning the repository locally or retrieving the source code for a particular [release][releases]. Open the project in Android Studio and run the following commands in the Terminal of Android Studio:
  
  ```
  ./gradlew clean jarRelease
  ```
  Output file can be found in `Parse/build/outputs/` with extension .aar
  
  You can link to your project to your AAR file as you please.
    
 ### Setup
- **Option 1:** Setup in the Manifest

  You may define `com.parse.SERVER_URL` and `com.parse.APPLICATION_ID` meta-data in your `AndroidManifest.xml`:

  ```xml
  <application ...>
    <meta-data
      android:name="com.parse.SERVER_URL"
      android:value="@string/parse_server_url" />
    <meta-data
      android:name="com.parse.APPLICATION_ID"
      android:value="@string/parse_app_id" />
    ...
  </application>
  ```
  
- **Option 2:** Setup in the Application
  
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
        .server("http://localhost:1337/parse/")
        .build()
      );
    }
  }
  ```
  
 For either option, the custom `Application` class must be registered in `AndroidManifest.xml`:
 
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
 
 [maven-svg]: https://maven-badges.herokuapp.com/maven-central/com.parse/parse-android/badge.svg?style=flat
 [maven-link]: https://maven-badges.herokuapp.com/maven-central/com.parse/parse-android
 
 [dependencies-svg]: https://www.versioneye.com/java/com.parse:parse-android/badge.svg?style=flat-square
 [dependencies-link]: https://www.versioneye.com/java/com.parse:parse-android
 
 [references-svg]: https://www.versioneye.com/java/com.parse:parse-android/reference_badge.svg?style=flat-square
 [references-link]: https://www.versioneye.com/java/com.parse:parse-android/references
 
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
