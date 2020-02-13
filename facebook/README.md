# Parse Facebook Utils for Android
A utility library to authenticate `ParseUser`s with the Facebook SDK.

## Dependency

After including JitPack:
```gradle
dependencies {
    implementation "com.github.parse-community.Parse-SDK-Android:facebook:latest.version.here"
}
```

## Usage
Extensive docs can be found in the [guide](https://docs.parseplatform.org/android/guide/#facebook-users). The basic steps are:
```java
// in Application.onCreate(); or somewhere similar
ParseFacebookUtils.initialize(context);
```
Within the activity where your user is going to log in with Facebook, include the following:
```java
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
  super.onActivityResult(requestCode, resultCode, data);
  ParseFacebookUtils.onActivityResult(requestCode, resultCode, data);
}
```
Then elsewhere, when your user taps the login button:
```java
ParseFacebookUtils.logInWithReadPermissionsInBackground(this, permissions, new LogInCallback() {
  @Override
  public void done(ParseUser user, ParseException err) {
    if (user == null) {
      Log.d("MyApp", "Uh oh. The user cancelled the Facebook login.");
    } else if (user.isNew()) {
      Log.d("MyApp", "User signed up and logged in through Facebook!");
    } else {
      Log.d("MyApp", "User logged in through Facebook!");
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
