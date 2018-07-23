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

## Contributing
When contributing to the `ktx` module, please first consider if the extension function you are wanting to add would potentially be better suited in the main `parse` module. If it is something specific to Kotlin users or only useful in a Kotlin project, feel free to make a PR adding it to this module. Otherwise, consider adding the addition to the `parse` module itself, so that it is still usable in Java.

## License
    Copyright (c) 2015-present, Parse, LLC.
    All rights reserved.

    This source code is licensed under the BSD-style license found in the
    LICENSE file in the root directory of this source tree. An additional grant
    of patent rights can be found in the PATENTS file in the same directory.