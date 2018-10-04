# Parse SDK Android KTX
Kotlin extension support for Parse Android

## Setup

### Installation
After including JitPack:

```groovy
dependencies {
    implementation "com.github.parse-community.Parse-SDK-Android:ktx:latest.version.here"
}
```
Then, you will be able to use the extension functions. For example:
```kotlin
/**
 * A cat, ParseObject subclass. Register with ParseObject.registerSubclass(Cat::class.java)
 */
@ParseClassName(Cat.NAME)
class Cat : ParseObject() {

    companion object {
        const val NAME = "Cat"

        const val KEY_NAME = "name"
        const val KEY_AGE = "age"
    }

    var name: String?
        get() = getString(KEY_NAME)
        set(value) = putOrIgnore(KEY_NAME, value)

    var age: Int?
        get() = getInt(KEY_AGE)
        set(value) = putOrRemove(KEY_AGE, value)
}

```

## Property Delegation

> "There are certain common kinds of properties, that, though we can implement them manually every time we need them, would be very nice to implement once and for all, and put into a library."

The text quoted above is the best explanation anyone could pass us, we use property delegation for the properties of our ParseObject, this prevents us having to write very much boilerplate.

### Without property delegation:

```kotlin
@ParseClassName("Cat")
class Cat : ParseObject() {

    companion object {
        const val KEY_NAME = "name"
    }

    var name: String
        get = getString(KEY_NAME)
        set(value) = putString(KEY_NAME, value)

}
```

### With property delegation:

```kotlin
@ParseClassName("Cat")
class Cat : ParseObject() {

    var name: String by stringAttribute() // That's it

}
```

The `stringAttribute` is a property delegate, and we have many other specialized types and also for generic types.

This causes us to not have to write get/set and besides, it removed the get/put boilerplate which is a must to map our classes with the Parse collections.

## ParseQuery extensions

Using Property Delegates will allow you to use a more secure way of creating queries.

If is needed to rename some property of a ParseObject, it is only necessary to use the IDE refactoring tool, that your queries will be automatically updated, which is not the case if hard coded strings are used.

```kotlin
@ParseClassName("Cat")
class Cat : ParseObject() {

    var age by intAttribute()

}

val query = ParseQuery.getQuery(Cat::class.java)
// Use this syntax
query.whereEqualTo(Cat::name, 1)
// instead of
query.whereEqualTo("age", 1)
// or
query.whereEqualTo(Cat::age.name, 1)
```

## Contributing
When contributing to the `ktx` module, please first consider if the extension function you are wanting to add would potentially be better suited in the main `parse` module. If it is something specific to Kotlin users or only useful in a Kotlin project, feel free to make a PR adding it to this module. Otherwise, consider adding the addition to the `parse` module itself, so that it is still usable in Java.

## License
    Copyright (c) 2015-present, Parse, LLC.
    All rights reserved.

    This source code is licensed under the BSD-style license found in the
    LICENSE file in the root directory of this source tree. An additional grant
    of patent rights can be found in the PATENTS file in the same directory.
