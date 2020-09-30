# Parse SDK Android Coroutines
Kotlin coroutines support for Parse Android

## Dependency

After including JitPack:
```gradle
dependencies {
    implementation "com.github.parse-community.Parse-SDK-Android:coroutines:latest.version.here"
}
```

## Use Parse Coroutines

### ParseQuery 

Now we can call a parse query using a synchronous style, this is possible when we use coroutines. We need to use a regular coroutine builder: 

```kotlin
launch { // Coroutine builder
    val cat = ParseQuery.getQuery(...).coroutineFind()
    // get cats without callback
}
```
We use a coroutine builder because `coroutineFind()` is a suspend function.

We can also, use a function like a coroutine builder, it will be provider us a flexibility call our query without any extensions function.

````kotlin
launchQuery(query) {
	// doing operations like find, get, first and count
}
````

It uses a a regular coroutine builder `launch` and pass as receiver a `ParseQueryOperation`` 

### ParseCloud

We can call cloud function inline:

 ```kotlin
launch { // Coroutine builder
    val catThumb = callCloudFunction("getThumbnail", mapOf("url" to "https://cat.jpg"))
    // get cats without callback
}
```

### ParseUser

SignUp:

```kotlin
launch { // Coroutine builder
    user = ParseUser().apply {
        setUsername("my name")
        setPassword("my pass")
        setEmail("email@example.com")
    }.also {
        coroutineSignUp()
    }
}
```
Login: 

```kotlin
launch { // Coroutine builder
    val user = parseLogIn("username", "password")
}
```

### Parse Object

We can save, pinning and fetch parse objects use coroutines as well.

## Task Wrapper
Coroutine support can be provided as an extension method on any `Task`. For example:
```kotlin
suspend fun anonymousLogin() {
    try {
        val user = ParseAnonymousUtils.logInInBackground().suspendGet()
        Timber.d("Logged in with user ${user.objectId}")
    } catch (e: ParseException) {
        Timber.e(e)
    }
}
```
Tasks with a Void results, ie. `Task<Void>` can be made into a `Completable`.
For example:
```kotlin
suspend fun updateUserLastLogIn() {
    val user = ParseUser.getCurrentUser()
    user.put("lastLoggedIn", System.currentTimeMillis())
    try {
        user.saveInBackground().suspendRun()
        Timber.d("user saved")
    } catch (e: ParseException) {
        Timber.e(it)
    }
}
```

## Contributing
When contributing to the `coroutines` module, please first consider if the extension function you are wanting to add would potentially be better suited in the main `parse` module. If it is something specific to Kotlin users or only useful in a Kotlin project, feel free to make a PR adding it to this module. Otherwise, consider adding the addition to the `parse` module itself, so that it is still usable in Java.

## License
    Copyright (c) 2015-present, Parse, LLC.
    All rights reserved.

    This source code is licensed under the BSD-style license found in the
    LICENSE file in the root directory of this source tree. An additional grant
    of patent rights can be found in the PATENTS file in the same directory.
