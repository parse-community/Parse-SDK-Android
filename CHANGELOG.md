## Changelog

### master
> __BREAKING CHANGES__
> 
> - Required MIN SDK Version now is 16 required by latest Firebase and Facebook SDKs

> __NOTE__
> 
> This release mostly internal code base restructure and improvements. No noticeable features 
> or bugfixes are introduced. @L3K0V

- INTERNAL: Update all the codebase to advantage of Java 8 goodies
    - Replace all anonymous classes with lambdas where possible. 
    - Make all fields final where possible
    - Internal classes as static where possible
    - Replace test assertions with context ones
    - Use diamond <> where possible
    - Use method references where possible
    - Imports re-arrange
- INTERNAL: Updated Kotlin  and Coroutines version to `1.5.31`
- INTERNAL: Update project from Android Studio `3.6` to `4.2`
- INTERNAL: Update Gradle version from `5.6.4` to `6.8.3`
- INTERNAL: Update Robolectric from `3.8` to `4.6` and adjust all tests
- Now SDK is targeting latest Android version API 30
- Update Play services up to their respective versions
    - Google Play services auth from `18.0.0` to `19.0.0`
    - Google Cloud Messaging from `12.0.1` to `17.0.0` (GCM module will be removed in flavor of Firebase)
    - Firebase Messaging from `20.1.5` to `22.0.0`
- INTERNAL: Update jacoco and fixed reporting of test coverage
- INTERNAL: Migrated from Travis CI to GitHub Actions
- INTERNAL: Migrate depricated dependency repository from `jcenter()` to `mavenCentral()`

### 1.25.0
> __BREAKING CHANGES__
>
> - FIX: Corrected the `Installation` property `appVersion` to be the build version instead of the version name. This aligns the property with its equivalent in the Parse iOS SDK. See [#902](https://github.com/parse-community/Parse-SDK-Android/issues/902) for details. Thanks to [Manuel Trezza](https://github.com/mtrezza).
- Added RxJava module to transform `Task`s into RxJava types.

### 1.24.2
- FIX: Fixed naming collission bug due to integration of bolts-tasks module. See [#1028](https://github.com/parse-community/Parse-SDK-Android/issues/1028) for details. Thanks to [Manuel Trezza](https://github.com/mtrezza)

### 1.24.1
> __WARNING__
>
> Avoid using this release as it contains a [naming collission bug](https://github.com/parse-community/Parse-SDK-Android/issues/1028) that has been introduced in release `1.24.0` and fixed in release `1.24.2`. The bug causes the project compliation to fail due to duplicate class names for the `bolts-tasks` module when using the Facebook Android SDK or the Parse Android SDK Facebook module.

- Resolves issue around missing bolts-tasks dependency thanks to @rogerhu (#1025)

### 1.24.0
> __WARNING__
>
> Avoid using this release as it contains a [naming collission bug](https://github.com/parse-community/Parse-SDK-Android/issues/1028) that has been introduced in release `1.24.0` and fixed in release `1.24.2`. The bug causes the project compliation to fail due to duplicate class names for the `bolts-tasks` module when using the Facebook Android SDK or the Parse Android SDK Facebook module.

- Add bolts-task instead of depending on FB's outdated lib (#1018) thanks to @rogerhu
- Add nullability to `ParseCloud` (#1008) thanks to @Jawnnypoo
- Set to unknown name if version name is null (#1014) thanks to @Jawnnypoo
- Fix signup method name (#1017) thanks to @Jawnnypoo

### 1.23.1
- Correction to OkHttp version thanks to @mtrezza

### 1.23.0
- Add Google login/signup support
- Move Facebook and Twitter libraries to be modules within this library
- Update Facebook login to use AndroidX
- Add ability to update the server without having to reinitialize the client thanks to @mtrezza

### 1.22.1
Re-releasing since Jitpack failed. Same as 1.22.0

### 1.22.0
- Expose client destroy
- Enhancement to ParseQuery kt operations

### 1.21.0
- Add coroutines support module
- Fix bug in save user in batch

### 1.20.0
- Fix fetchAllIfNeeded and fetchAllIfNeededInBackground limit #939
- Expose useful constants #930
- ParseQuery extensions #929
- Change to non-deprecated methods for FCM #927. If you are using FCM and updating to 1.20.0, be sure to take a look at the FCM README for the updated steps on usage.

### 1.19.0
- SDK now uses AndroidX and API 28
- Kotlin Delegates
- Fix StackOverflowError when merging ParseObject from JSON #896

### 1.18.5
- Fix for issue #886

### 1.18.4
- Fix issue with returning { "result": null } in cloud function (deserialized as JSONObject instead of null)
- Remove deprecated methods in ParseAnalytics and ParsePush
- Add findAll() method to ParseQuery which iterates and finds all ParseObjects for a query (no limit)

### 1.18.3
- Add ktx module and dependency, which adds some Kotlin extensions for easier Parse SDK usage.

### 1.18.2
- More things made public for LiveQuery support

### 1.18.1
- Make things public for LiveQuery support

### 1.18.0
- Annotate ParseObject with nullability thanks to @kurtisnelson and @Jawnnypoo
- Remove deprecated refresh() method from ParseObject
- Partial string match thanks to @rogerhu
- Fix delete, save eventually, and findAllPinned issues thanks to @dangtz
