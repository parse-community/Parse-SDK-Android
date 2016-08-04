# Parse SDK for Android
[![Build Status][build-status-svg]][build-status-link]
[![Coverage Status][coverage-status-svg]][coverage-status-link]
[![Maven Central][maven-svg]][maven-link]
[![License][license-svg]][license-link]


A library that gives you access to the powerful Parse cloud platform from your Android app. 
For more information about Parse and its features, see [the website][parse.com] and [getting started][guide].

## Download
Download [the latest JAR][latest] or define in Gradle:

```groovy
dependencies {
  compile 'com.parse:parse-android:1.13.1'
}
```

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

## Usage
Everything can done through the supplied gradle wrapper:

### Compile a JAR
```
./gradlew clean jarRelease
```
Outputs can be found in `Parse/build/libs/`

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
We want to make contributing to this project as easy and transparent as possible. Please refer to the [Contribution Guidelines](CONTRIBUTING.md).

## Other Parse Projects

 - [ParseUI for Android][parseui-link]
 - [ParseFacebookUtils for Android][parsefacebookutils-link]
 - [ParseTwitterUtils for Android][parsetwitterutils-link]

## License
    Copyright (c) 2015-present, Parse, LLC.
    All rights reserved.

    This source code is licensed under the BSD-style license found in the
    LICENSE file in the root directory of this source tree. An additional grant 
    of patent rights can be found in the PATENTS file in the same directory.

 [parse.com]: https://www.parse.com/products/android
 [guide]: https://www.parse.com/docs/android/guide
 [blog]: https://blog.parse.com/

 [latest]: https://search.maven.org/remote_content?g=com.parse&a=parse-android&v=LATEST
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/

 [build-status-svg]: https://travis-ci.org/ParsePlatform/Parse-SDK-Android.svg?branch=master
 [build-status-link]: https://travis-ci.org/ParsePlatform/Parse-SDK-Android
 [coverage-status-svg]: https://coveralls.io/repos/ParsePlatform/Parse-SDK-Android/badge.svg?branch=master&service=github
 [coverage-status-link]: https://coveralls.io/github/ParsePlatform/Parse-SDK-Android?branch=master
 [maven-svg]: https://maven-badges.herokuapp.com/maven-central/com.parse/parse-android/badge.svg?style=flat
 [maven-link]: https://maven-badges.herokuapp.com/maven-central/com.parse/parse-android
 
 [parseui-link]: https://github.com/ParsePlatform/ParseUI-Android
 [parsefacebookutils-link]: https://github.com/ParsePlatform/ParseFacebookUtils-Android
 [parsetwitterutils-link]: https://github.com/ParsePlatform/ParseTwitterUtils-Android
 
 [license-svg]: https://img.shields.io/badge/license-BSD-lightgrey.svg
 [license-link]: https://github.com/ParsePlatform/Parse-SDK-Android/blob/master/LICENSE
