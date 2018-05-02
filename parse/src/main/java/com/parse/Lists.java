/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.parse;

import java.util.AbstractList;
import java.util.List;

/**
 * Static utility methods pertaining to {@link List} instances. Also see this
 * class's counterparts {@link Sets}, {@link Maps} and {@link Queues}.
 *
 * <p>See the Guava User Guide article on <a href=
 * "https://github.com/google/guava/wiki/CollectionUtilitiesExplained#lists">
 * {@code Lists}</a>.
 *
 * @author Kevin Bourrillion
 * @author Mike Bostock
 * @author Louis Wasserman
 * @since 2.0
 */
/** package */ class Lists {

  /**
   * Returns consecutive sublists of a list, each of the same size (the final list may be smaller).
   * For example, partitioning a list containing [a, b, c, d, e] with a partition size of 3 yields
   * [[a, b, c], [d, e]] -- an outer list containing two inner lists of three and two elements, all
   * in the original order.
   *
   * The outer list is unmodifiable, but reflects the latest state of the source list. The inner
   * lists are sublist views of the original list, produced on demand using List.subList(int, int),
   * and are subject to all the usual caveats about modification as explained in that API.
   *
   * @param list the list to return consecutive sublists of
   * @param size the desired size of each sublist (the last may be smaller)
   * @return a list of consecutive sublists
   */
  /* package */ static <T> List<List<T>> partition(List<T> list, int size) {
    return new Partition<>(list, size);
  }

  private static class Partition<T> extends AbstractList<List<T>> {

    private final List<T> list;
    private final int size;

    public Partition(List<T> list, int size) {
      this.list = list;
      this.size = size;
    }

    @Override
    public List<T> get(int location) {
      int start = location * size;
      int end = Math.min(start + size, list.size());
      return list.subList(start, end);
    }

    @Override
    public int size() {
      return (int) Math.ceil((double)list.size() / size);
    }
  }
}
