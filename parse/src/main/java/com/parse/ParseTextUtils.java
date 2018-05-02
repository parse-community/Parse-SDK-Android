/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.parse;

/* package */ class ParseTextUtils {

  /**
   * Returns a string containing the tokens joined by delimiters.
   * @param tokens an array objects to be joined. Strings will be formed from
   *     the objects by calling object.toString().
   */
  /* package */ static String join(CharSequence delimiter, Iterable tokens) {
    StringBuilder sb = new StringBuilder();
    boolean firstTime = true;
    for (Object item: tokens) {
      if (firstTime) {
        firstTime = false;
      } else {
        sb.append(delimiter);
      }
      sb.append(item);
    }
    return sb.toString();
  }

  /**
   * Returns true if the string is null or 0-length.
   * @param text the string to be examined
   * @return true if str is null or zero length
   */
  public static boolean isEmpty(CharSequence text) {
    return text == null || text.length() == 0;
  }

  /**
   * Returns true if a and b are equal, including if they are both null.
   * <p><i>Note: In platform versions 1.1 and earlier, this method only worked well if
   * both the arguments were instances of String.</i></p>
   * @param a first CharSequence to check
   * @param b second CharSequence to check
   * @return true if a and b are equal
   */
  public static boolean equals(CharSequence a, CharSequence b) {
    return (a == b) || (a != null && a.equals(b));
  }

  private ParseTextUtils() {
    /* cannot be instantiated */
  }
}
