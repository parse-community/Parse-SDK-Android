# Parse SDK Android RxJava
RxJava 3 support for Parse Android

## Dependency

After including JitPack:
```gradle
dependencies {
    implementation "com.github.parse-community.Parse-SDK-Android:rxjava:latest.version.here"
}
```

## Usage
RxJava support is provided as an extension method on any `Task`. For example:
```kotlin
ParseTwitterUtils.logInInBackground(this)
    .toSingle()
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe({
        Timber.d("Logged in with user ${it.objectId}"
    }, {
        Timber.e(it)
    })
```
Tasks with a Void results, ie. `Task<Void>` can be made into a `Completable`.
For example:
```kotlin
val user = ParseUser.getCurrentUser()
user.put("lastLoggedIn", System.currentTimeMillis())
user.saveInBackground().toCompletable()
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe({
        // yay
        Timber.d("user saved")
    }, {
        Timber.e(it)
    })
```
Note that these examples uses RxAndroid as well, which you need to add yourself as a dependency.

### From Java
If you need to call this from Java:
```java
ParseUser user = ParseUser.getCurrentUser();
Completable completable = ParseRxJavaUtils.toCompletable(user.saveInBackground());
```
You would use similar calls to create a `Single<T>`.

## License
    Copyright (c) 2015-present, Parse, LLC.
    All rights reserved.

    This source code is licensed under the BSD-style license found in the
    LICENSE file in the root directory of this source tree. An additional grant
    of patent rights can be found in the PATENTS file in the same directory.
