/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.internal.matchers.TypeSafeMatcher;

import static org.hamcrest.CoreMatchers.equalTo;

public class ParseMatchers {
    public static Matcher<Throwable> hasParseErrorCode(int code) {
        return hasParseErrorCode(equalTo(code));
    }

    public static Matcher<Throwable> hasParseErrorCode(final Matcher<Integer> matcher) {
        return new TypeSafeMatcher<Throwable>() {
            @Override
            public boolean matchesSafely(Throwable item) {
                return item instanceof ParseException
                        && matcher.matches(((ParseException) item).getCode());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("exception with message ");
                description.appendDescriptionOf(matcher);
            }
        };
    }
}
