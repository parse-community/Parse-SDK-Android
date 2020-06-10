## Changelog

### master

> __BREAKING CHANGES__
> - FIX: Corrected the `Installation` property `appVersion` to be the build version instead of the version name. This aligns the property with its equivalent in the Parse iOS SDK. See [#902](https://github.com/parse-community/Parse-SDK-Android/pull/902) for details. Thanks to [Manuel Trezza](https://github.com/mtrezza).

- Update OkHttp version to allow for future Android API 30 compilation
- Compile with Android 29
- Update Facebook Login dependency to 6.1.0
- Add nullability annotations to `ParseCloud`

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