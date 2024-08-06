# [4.3.0](https://github.com/parse-community/Parse-SDK-Android/compare/4.2.1...4.3.0) (2024-02-18)


### Features

* Add support for uploading a `ParseFile` from a URI ([#1207](https://github.com/parse-community/Parse-SDK-Android/issues/1207)) ([83aec68](https://github.com/parse-community/Parse-SDK-Android/commit/83aec68cb7f95e0116b3878b8cda099fd3a2e200))

## [4.2.1](https://github.com/parse-community/Parse-SDK-Android/compare/4.2.0...4.2.1) (2023-08-25)


### Bug Fixes

* Missing Proguard rules for R8 in full mode ([#1196](https://github.com/parse-community/Parse-SDK-Android/issues/1196)) ([7db0965](https://github.com/parse-community/Parse-SDK-Android/commit/7db09650447db2e0f82247240ae51687189cd03f))

# [4.2.0](https://github.com/parse-community/Parse-SDK-Android/compare/4.1.0...4.2.0) (2023-02-22)


### Features

* Add support for Facebook SDK 15.x ([#1188](https://github.com/parse-community/Parse-SDK-Android/issues/1188)) ([5ebd443](https://github.com/parse-community/Parse-SDK-Android/commit/5ebd4437ff9554cf981b9437e593ed7e9deb3675))

# [4.1.0](https://github.com/parse-community/Parse-SDK-Android/compare/4.0.0...4.1.0) (2022-08-26)


### Bug Fixes

* exception on concurrent download of `ParseFile` from multiple threads ([#1180](https://github.com/parse-community/Parse-SDK-Android/issues/1180)) ([44b1914](https://github.com/parse-community/Parse-SDK-Android/commit/44b191497a29429bca0ab6bf44fb63c69515b3c9))

### Features

* upgrade various dependencies ([#1181](https://github.com/parse-community/Parse-SDK-Android/issues/1181)) ([c455f4a](https://github.com/parse-community/Parse-SDK-Android/commit/c455f4a4c183c7c11f57c9662542829e82b207ad))

# [4.0.0](https://github.com/parse-community/Parse-SDK-Android/compare/3.0.1...4.0.0) (2022-06-10)


### Features

* update various dependencies ([#1172](https://github.com/parse-community/Parse-SDK-Android/issues/1172)) ([779dc0b](https://github.com/parse-community/Parse-SDK-Android/commit/779dc0b9d635a2b14f142e7eb2788f938ccc6134))


### BREAKING CHANGES

* The Facebook Login SDK is upgraded to 13.x; this is a transitive dependency, so if you are directly calling the Facebook Login SDK in your app then this may be a braking change; this release of the Parse Android SDK adds a version range to the Facebook Login SDK dependency for correct gradle dependency resolving ([779dc0b](779dc0b))

## [3.0.1](https://github.com/parse-community/Parse-SDK-Android/compare/3.0.0...3.0.1) (2022-05-26)


### Bug Fixes

* users logged out after SDK upgrade due to different cache path; this fixes the bug that was introduced with release 3.0.0 which ignores SDK-internal data that is stored locally on the client side ([#1168](https://github.com/parse-community/Parse-SDK-Android/issues/1168)) ([ec7bd03](https://github.com/parse-community/Parse-SDK-Android/commit/ec7bd03eb98310dd63d15398be344b0f5e5fd54f))

# [3.0.0](https://github.com/parse-community/Parse-SDK-Android/compare/2.1.0...3.0.0) (2021-11-25)

### ⚠️ Warning

This version contains a bug that ignores SDK-internal data that is already stored locally on the client side. This includes for example the Parse SDK session token, so an already logged-in user will be required to log in again. If you are not starting with a new app but are considering upgrading an existing app you may want to skip this version and wait for a fix in a future version. (#1158)

### Features

* update project dependencies and code refactoring ([#1147](https://github.com/parse-community/Parse-SDK-Android/issues/1147)) ([7d0faa3](https://github.com/parse-community/Parse-SDK-Android/commit/7d0faa38bf3ccc85b715dc61965d5b82103c19a4))


### BREAKING CHANGES

* The required minimum API level changes from 16 to 21. The following deprecated methods are removed: `Parse.getParseDir()` (use `getParseCacheDir(String)` or `getParseFilesDir(String)` instead), `ParseTwitterUtils.link(ParseUser, Context)` (use `linkInBackground(Context, ParseUser)` instead), `ParseTwitterUtils.link(ParseUser, String, String, String, String)` (use `linkInBackground(ParseUser, String, String, String, String)` instead). ([7d0faa3](7d0faa3))

# [2.1.0](https://github.com/parse-community/Parse-SDK-Android/compare/2.0.6...2.1.0) (2021-11-21)


### Features

* add support for custom objectId ([#1088](https://github.com/parse-community/Parse-SDK-Android/issues/1088)) ([d420371](https://github.com/parse-community/Parse-SDK-Android/commit/d420371e761b07fac02f8ad747de0191817db5fa))

## [2.0.6](https://github.com/parse-community/Parse-SDK-Android/compare/2.0.5...2.0.6) (2021-11-16)


### Bug Fixes

* missing pending intents mutability for Android 12+ ([#1139](https://github.com/parse-community/Parse-SDK-Android/issues/1139)) ([fbe8d87](https://github.com/parse-community/Parse-SDK-Android/commit/fbe8d87cc0604c4c34866815307c07e99a125f45))

## [2.0.5](https://github.com/parse-community/Parse-SDK-Android/compare/2.0.4...2.0.5) (2021-11-10)


### Bug Fixes

* failed signup attempt with anonymous ParseUser leaves it in inconsistent state ([#1136](https://github.com/parse-community/Parse-SDK-Android/issues/1136)) ([ac6d9e0](https://github.com/parse-community/Parse-SDK-Android/commit/ac6d9e0734d1ea9c0bc26ddd7bfa8dea21a8c693))

## [2.0.4](https://github.com/parse-community/Parse-SDK-Android/compare/2.0.3...2.0.4) (2021-11-03)


### Performance Improvements

* remove unnecessary extra child traversal in collectDirtyChildren ([#1058](https://github.com/parse-community/Parse-SDK-Android/issues/1058)) ([1844b3e](https://github.com/parse-community/Parse-SDK-Android/commit/1844b3ee9557a9f12983a573dc6d02ef0ec79910))

## [2.0.3](https://github.com/parse-community/Parse-SDK-Android/compare/2.0.2...2.0.3) (2021-10-18)


### Bug Fixes

* java package version always 1.0.0, current parse version not pushed to server ([#1130](https://github.com/parse-community/Parse-SDK-Android/issues/1130)) ([3c6496a](https://github.com/parse-community/Parse-SDK-Android/commit/3c6496a2bc8be989f3d1d7f808edc829dea2579e))

## [2.0.2](https://github.com/parse-community/Parse-SDK-Android/compare/2.0.1...2.0.2) (2021-10-17)


### Bug Fixes

* race condition when keys are added or removed while the object is being traversed ([#1062](https://github.com/parse-community/Parse-SDK-Android/issues/1062)) ([d28e64d](https://github.com/parse-community/Parse-SDK-Android/commit/d28e64dfc6ade48a032504c54a9bfd2c08a800e2))

## [2.0.1](https://github.com/parse-community/Parse-SDK-Android/compare/2.0.0...2.0.1) (2021-10-14)


### Bug Fixes

* add maven publications to configure the Jitpack releases ([#1128](https://github.com/parse-community/Parse-SDK-Android/issues/1128)) ([67c4fb6](https://github.com/parse-community/Parse-SDK-Android/commit/67c4fb6209e1a3b3db9663156b356b8888ef0d87))
* Parse Android SDK 2.0.0 not building on jitpack ([#1129](https://github.com/parse-community/Parse-SDK-Android/issues/1129)) ([5d40917](https://github.com/parse-community/Parse-SDK-Android/commit/5d409174358ec4f2f04b9e0ea3000743c4a066ff))

# [2.0.0](https://github.com/parse-community/Parse-SDK-Android/compare/1.26.0...2.0.0) (2021-10-10)

### BREAKING CHANGES
- Required minimum SDK version is 16 ([#1095](https://github.com/parse-community/Parse-SDK-Android/pull/1095))
- Support for Google Cloud Messaging (GCM) is removed, use Firebase Cloud Messaging instead; see the [Google developer documentation](https://developers.google.com/cloud-messaging/faq) for more details and migration assistance ([#1105](https://github.com/parse-community/Parse-SDK-Android/pull/1105))

### Feature
- Update all dependencies and modernize the source base (Asen Lekov) [#1095](https://github.com/parse-community/Parse-SDK-Android/pull/1095)
- Upgrade Facebook Login SDK to 8.2.0 (Somye Mahajan) [#1105](https://github.com/parse-community/Parse-SDK-Android/pull/1105)

### Fix
- Remove `gcm` module since GCM is no longer supported by Google (John Carlson) [#1091](https://github.com/parse-community/Parse-SDK-Android/pull/1091)

### Internal Changes
  - SDK targets the latest Android version API 30
  - Update the codebase to advantage of Java 8 syntax
  - Update Kotlin and Coroutines version to `1.5.31`
  - Update project from Android Studio `3.6` to `4.2`
  - Update Gradle version from `5.6.4` to `6.8.3`
  - Update Robolectric from `3.8` to `4.6` and adjust all tests
  - Update Play services
    - Google Play services auth from `18.0.0` to `19.2.0`
    - Google Cloud Messaging from `12.0.1` to `17.0.0`
    - Firebase Messaging from `20.1.5` to `22.0.0`
  - Update jacoco and fixed reporting of test coverage
  - Migrate deprecated dependency repository from `jcenter()` to `mavenCentral()`

### CI
- Migrate from Travis CI to GitHub Actions (Asen Lekov) [#1095](https://github.com/parse-community/Parse-SDK-Android/pull/1095)

## 2.0.0-alpha.1

### BREAKING CHANGES
- Required minimum SDK version is 16

### Feature
- Update all dependencies and modernize the source base (Asen Lekov) [#1095](https://github.com/parse-community/Parse-SDK-Android/pull/1095)
- Upgrade Facebook Login SDK to 8.2.0 (Somye Mahajan) [#1105](https://github.com/parse-community/Parse-SDK-Android/pull/1105)

### Internal Changes
  - SDK targets the latest Android version API 30
  - Update the codebase to advantage of Java 8 syntax
  - Update Kotlin and Coroutines version to `1.5.31`
  - Update project from Android Studio `3.6` to `4.2`
  - Update Gradle version from `5.6.4` to `6.8.3`
  - Update Robolectric from `3.8` to `4.6` and adjust all tests
  - Update Play services
    - Google Play services auth from `18.0.0` to `19.2.0`
    - Google Cloud Messaging from `12.0.1` to `17.0.0`
    - Firebase Messaging from `20.1.5` to `22.0.0`
  - Update jacoco and fixed reporting of test coverage
  - Migrate deprecated dependency repository from `jcenter()` to `mavenCentral()`

### CI
- Migrate from Travis CI to GitHub Actions (Asen Lekov) [#1095](https://github.com/parse-community/Parse-SDK-Android/pull/1095)

## 1.26.0
- fix TypeCastException when unlinking google account [#1076](https://github.com/parse-community/Parse-SDK-Android/pull/1076)
- feature: KTX property delegation custom labels [#1066](https://github.com/parse-community/Parse-SDK-Android/pull/1066)
- feature: Coroutine Task Wrapper [#1064](https://github.com/parse-community/Parse-SDK-Android/pull/1064)
- Rename functions that cause shadow members [#1054](https://github.com/parse-community/Parse-SDK-Android/pull/1054)

## 1.25.0
> __BREAKING CHANGES__
>
> - FIX: Corrected the `Installation` property `appVersion` to be the build version instead of the version name. This aligns the property with its equivalent in the Parse iOS SDK. See [#902](https://github.com/parse-community/Parse-SDK-Android/issues/902) for details. Thanks to [Manuel Trezza](https://github.com/mtrezza).
- Added RxJava module to transform `Task`s into RxJava types.

## 1.24.2
- FIX: Fixed naming collission bug due to integration of bolts-tasks module. See [#1028](https://github.com/parse-community/Parse-SDK-Android/issues/1028) for details. Thanks to [Manuel Trezza](https://github.com/mtrezza)

## 1.24.1
> __WARNING__
>
> Avoid using this release as it contains a [naming collission bug](https://github.com/parse-community/Parse-SDK-Android/issues/1028) that has been introduced in release `1.24.0` and fixed in release `1.24.2`. The bug causes the project compliation to fail due to duplicate class names for the `bolts-tasks` module when using the Facebook Android SDK or the Parse Android SDK Facebook module.

- Resolves issue around missing bolts-tasks dependency thanks to @rogerhu (#1025)

## 1.24.0
> __WARNING__
>
> Avoid using this release as it contains a [naming collission bug](https://github.com/parse-community/Parse-SDK-Android/issues/1028) that has been introduced in release `1.24.0` and fixed in release `1.24.2`. The bug causes the project compliation to fail due to duplicate class names for the `bolts-tasks` module when using the Facebook Android SDK or the Parse Android SDK Facebook module.

- Add bolts-task instead of depending on FB's outdated lib (#1018) thanks to @rogerhu
- Add nullability to `ParseCloud` (#1008) thanks to @Jawnnypoo
- Set to unknown name if version name is null (#1014) thanks to @Jawnnypoo
- Fix signup method name (#1017) thanks to @Jawnnypoo

## 1.23.1
- Correction to OkHttp version thanks to @mtrezza

## 1.23.0
- Add Google login/signup support
- Move Facebook and Twitter libraries to be modules within this library
- Update Facebook login to use AndroidX
- Add ability to update the server without having to reinitialize the client thanks to @mtrezza

## 1.22.1
Re-releasing since Jitpack failed. Same as 1.22.0

## 1.22.0
- Expose client destroy
- Enhancement to ParseQuery kt operations

## 1.21.0
- Add coroutines support module
- Fix bug in save user in batch

## 1.20.0
- Fix fetchAllIfNeeded and fetchAllIfNeededInBackground limit #939
- Expose useful constants #930
- ParseQuery extensions #929
- Change to non-deprecated methods for FCM #927. If you are using FCM and updating to 1.20.0, be sure to take a look at the FCM README for the updated steps on usage.

## 1.19.0
- SDK now uses AndroidX and API 28
- Kotlin Delegates
- Fix StackOverflowError when merging ParseObject from JSON #896

## 1.18.5
- Fix for issue #886

## 1.18.4
- Fix issue with returning { "result": null } in cloud function (deserialized as JSONObject instead of null)
- Remove deprecated methods in ParseAnalytics and ParsePush
- Add findAll() method to ParseQuery which iterates and finds all ParseObjects for a query (no limit)

## 1.18.3
- Add ktx module and dependency, which adds some Kotlin extensions for easier Parse SDK usage.

## 1.18.2
- More things made public for LiveQuery support

## 1.18.1
- Make things public for LiveQuery support

# 1.18.0
- Annotate ParseObject with nullability thanks to @kurtisnelson and @Jawnnypoo
- Remove deprecated refresh() method from ParseObject
- Partial string match thanks to @rogerhu
- Fix delete, save eventually, and findAllPinned issues thanks to @dangtz
