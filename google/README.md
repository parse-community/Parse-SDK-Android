# Parse Google Utils for Android
A utility library to authenticate `ParseUser`s with the Google SDK.

## Dependency

After including JitPack:
```gradle
dependencies {
    implementation "com.github.parse-community.Parse-SDK-Android:google:latest.version.here"
}
```

## Usage
Here we will show the basic steps for logging in/signing up with Google. First:
```java
// in Application.onCreate(); or somewhere similar
ParseGoogleUtils.initialize(getString(R.string.default_web_client_id));
```
If you have already configured [Firebase](https://firebase.google.com/docs/android/setup) in your project, the above will work. Otherwise, you might instead need to replace `getString(R.id.default_web_client_id` with a web configured OAuth 2.0 API client ID.

Within the activity where your user is going to log in with Google, include the following:
```java
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
  super.onActivityResult(requestCode, resultCode, data);
  ParseGoogleUtils.onActivityResult(requestCode, resultCode, data);
}
```
Then elsewhere, when your user taps the login button:
```java
ParseGoogleUtils.logInWithReadPermissionsInBackground(this, permissions, new LogInCallback() {
  @Override
  public void done(ParseUser user, ParseException err) {
    if (user == null) {
      Log.d("MyApp", "Uh oh. The user cancelled the Google login.");
    } else if (user.isNew()) {
      Log.d("MyApp", "User signed up and logged in through Google!");
    } else {
      Log.d("MyApp", "User logged in through Google!");
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
