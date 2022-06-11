# Contributing to Parse SDK for Android
We want to make contributing to this project as easy and transparent as possible.

## Pull Requests
We actively welcome your pull requests. When we get one, we'll run some Parse-specific integration tests on it first. From here, we'll need to get a core member to sign off on the changes and then merge the pull request. For API changes we may need to fix internal uses, which could cause some delay. We'll do our best to provide updates and feedback throughout the process.

1. Fork the repo and create your branch from `master`.
4. Add unit tests for any new code you add.
3. If you've changed APIs, update the documentation.
4. Ensure the test suite passes.
5. Make sure your code lints by running `./gradlew spotlessApply`.

## Android API compatibility

Consider the following to ensure a consistent Android API compatibility for the Parse Android SDK:
- Target API level: Target the latest available stable API version.
- Minimum API level: Cover at least ~90% of devices. The increment will be done only if dependencies requires it, if the statistics points that the API level covers at least ~90% of devices, or if a currently supported Android API level does not receive security updates anymore. Minimum API level changes must be marked in the release notes as breaking changes.

## Reporting an Issue
Not all issues are SDK issues. If you're unsure whether your bug is with the SDK or backend, you can test to see if it reproduces with our [REST API][rest-api] and [Parse API Console][parse-api-console]. If it does, you can report backend bugs [here][bug-reports].

To view the REST API network requests issued by the Parse SDK and responses from the Parse backend, please check out [OkHttp Interceptors][network-debugging-tool].  With this tool, you can either log network requests/responses to Android logcat, or log them to Chrome Debugger via Stetho.

Details are key. The more information you provide us the easier it'll be for us to debug and the faster you'll receive a fix. Some examples of useful tidbits:

* A description. What did you expect to happen and what actually happened? Why do you think that was wrong?
* A simple unit test that fails. Refer [here][tests-dir] for examples of existing unit tests. See our [README](README.md#usage) for how to run unit tests. You can submit a pull request with your failing unit test so that our CI verifies that the test fails.
* What version does this reproduce on? What version did it last work on?
* [Stacktrace or GTFO][stacktrace-or-gtfo]. In all honesty, full stacktraces with line numbers make a happy developer.
* Anything else you find relevant.

## Code of Conduct
This project adheres to the [Contributor Covenant Code of Conduct](https://github.com/parse-community/parse-server/blob/master/CODE_OF_CONDUCT.md). By participating, you are expected to honor this code.

## License
By contributing to Parse Android SDK, you agree that your contributions will be licensed under its license.

[stack-overflow]: http://stackoverflow.com/tags/parse.com
[bug-reports]: https://github.com/parse-community/parse-server
[rest-api]: http://docs.parseplatform.org/rest/guide/
[network-debugging-tool]: https://github.com/square/okhttp/wiki/Interceptors
[parse-api-console]: http://blog.parseplatform.org/announcements/introducing-the-parse-api-console/
[stacktrace-or-gtfo]: http://i.imgur.com/jacoj.jpg
[tests-dir]: /parse/src/test/java/com/parse
