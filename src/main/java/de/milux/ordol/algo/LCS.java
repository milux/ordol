/*
 * ordol
 * 
 * Copyright (C) 2018 Michael Lux, Fraunhofer AISEC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.milux.ordol.algo;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class LCS {

  public static <T> int lcsLength(List<T> l1, List<T> l2) {
    return lcsLength(l1, l2, Object::hashCode, Objects::equals);
  }

  public static <T> int lcsLength(List<T> l1, List<T> l2, Function<T, Integer> hash) {
    return lcsLength(l1, l2, hash, Objects::equals);
  }

  public static <T> int lcsLength(List<T> l1, List<T> l2, BiPredicate<T, T> eq) {
    return lcsLength(l1, l2, Object::hashCode, eq);
  }

  public static <T> int lcsLength(
      List<T> l1, List<T> l2, Function<T, Integer> hash, BiPredicate<T, T> eq) {
    if (l1.isEmpty() || l2.isEmpty()) {
      return 0;
    }
    int[] l1h = new int[l1.size()];
    for (int i = 0, s = l1.size(); i < s; i++) {
      l1h[i] = hash.apply(l1.get(i));
    }
    int[] l2h = new int[l2.size()];
    for (int j = 0, s = l2.size(); j < s; j++) {
      l2h[j] = hash.apply(l2.get(j));
    }
    int[] len = new int[l2.size()];
    int[] lastLen = new int[len.length];
    // first row
    int h1 = l1h[0];
    // process i = 0 outside of loop
    for (int j = 0, sj = l2.size(); j < sj; j++) {
      if (h1 == l2h[j] && eq.test(l1.get(0), l2.get(j))) {
        for (int k = j, s = len.length; k < s; k++) {
          len[k] = 1;
          lastLen[k] = 1;
        }
        break;
      }
      len[j] = 0;
    }
    //
    // System.out.println(Arrays.stream(len).mapToObj(Integer::toString).collect(Collectors.joining(" ")));
    for (int i = 1, si = l1.size(); i < si; i++) {
      // in each round, swap the arrays first
      int[] swap = len;
      len = lastLen;
      lastLen = swap;
      // hash for the element in this row
      h1 = l1h[i];
      // process j = 0 before loop
      if (len[0] == 0 && h1 == l2h[0] && eq.test(l1.get(i), l2.get(0))) {
        len[0] = 1;
        lastLen[0] = 1;
      }
      for (int j = 1, sj = l2.size(); j < sj; j++) {
        if (h1 == l2h[j]
            && lastLen[j - 1] == lastLen[j]
            && lastLen[j] == len[j - 1]
            && eq.test(l1.get(i), l2.get(j))) {
          len[j] = lastLen[j] + 1;
        } else {
          len[j] = Math.max(lastLen[j], len[j - 1]);
        }
      }
      //
      // System.out.println(Arrays.stream(len).mapToObj(Integer::toString).collect(Collectors.joining(" ")));
    }
    return len[len.length - 1];
  }
}
