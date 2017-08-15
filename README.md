# Parse SDK for Android
[![Build Status][build-status-svg]][build-status-link]
[![Coverage Status][coverage-status-svg]][coverage-status-link]
[![Maven Central][maven-svg]][maven-link]
[![License][license-svg]][license-link]

[![Join Chat](https://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/ParsePlatform/Chat)


A library that gives you access to the powerful Parse cloud platform from your Android app.
For more information about Parse and its features, see [the website][parseplatform.org] and [getting started][guide].

## Download
Add the dependency in Gradle:

```groovy
dependencies {
  compile 'com.parse:parse-android:1.15.8'
}
```

Snapshots of the development version are available in [jFrog's `snapshots` repository][snap].

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
 [guide]: http://docs.parseplatform.org/android/guide/

 [latest]: https://search.maven.org/remote_content?g=com.parse&a=parse-android&v=LATEST
 [snap]: https://oss.jfrog.org/artifactory/oss-snapshot-local/com/parse/parse-android/

 [build-status-svg]: https://travis-ci.org/parse-community/Parse-SDK-Android.svg?branch=master
 [build-status-link]: https://travis-ci.org/parse-community/Parse-SDK-Android
 [coverage-status-svg]: https://coveralls.io/repos/parse-community/Parse-SDK-Android/badge.svg?branch=master&service=github
 [coverage-status-link]: https://coveralls.io/github/parse-community/Parse-SDK-Android?branch=master
 [maven-svg]: https://maven-badges.herokuapp.com/maven-central/com.parse/parse-android/badge.svg?style=flat
 [maven-link]: https://maven-badges.herokuapp.com/maven-central/com.parse/parse-android

 [parseui-link]: https://github.com/parse-community/ParseUI-Android
 [parselivequery-link]: https://github.com/parse-community/ParseLiveQuery-Android
 [parsefacebookutils-link]: https://github.com/parse-community/ParseFacebookUtils-Android
 [parsetwitterutils-link]: https://github.com/parse-community/ParseTwitterUtils-Android

 [license-svg]: https://img.shields.io/badge/license-BSD-lightgrey.svg
 [license-link]: https://github.com/parse-community/Parse-SDK-Android/blob/master/LICENSE
