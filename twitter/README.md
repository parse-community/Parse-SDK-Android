# Parse Twitter Utils for Android
A utility library to authenticate `ParseUser`s with Twitter.

## Dependency

After including JitPack:
```gradle
dependencies {
    implementation "com.github.parse-community.Parse-SDK-Android:twitter:latest.version.here"
}
```

## Usage
Extensive docs can be found in the [guide](https://docs.parseplatform.org/android/guide/#twitter-users). The basic steps are:
```java
// in Application.onCreate(); or somewhere similar
ParseTwitterUtils.initialize("YOUR CONSUMER KEY", "YOUR CONSUMER SECRET");
```
Then later, when your user taps the login button:
```java
ParseTwitterUtils.logIn(this, new LogInCallback() {
  @Override
  public void done(ParseUser user, ParseException err) {
    if (user == null) {
      Log.d("MyApp", "Uh oh. The user cancelled the Twitter login.");
    } else if (user.isNew()) {
      Log.d("MyApp", "User signed up and logged in through Twitter!");
    } else {
      Log.d("MyApp", "User logged in through Twitter!");
    }
  }
});
```

## License
    Copyright (c) 2015-present, Parse, LLC.
    All rights reserved.

    This source code is licensed under the BSD-style license found in the
    LICENSE file in the root directory of this source tree. An additional grant
    of patent rights can be found in the PATENTS file in the same directory.
