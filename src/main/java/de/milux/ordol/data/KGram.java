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
package de.milux.ordol.data;

import de.milux.ordol.helpers.IndexedList;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;

public class KGram<T> extends AbstractList<T> {
  public final T[] kGramArray;

  public KGram(T[] array) {
    this.kGramArray = array;
  }

  @Override
  public T get(int index) {
    return kGramArray[index];
  }

  @Override
  public int size() {
    return kGramArray.length;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    return o instanceof KGram && Arrays.equals(kGramArray, ((KGram) o).kGramArray);
  }

  private static <T> void makeKGrams(
      List<List<T>> blocks,
      Map<Integer, Set<Integer>> blockSuccs,
      Consumer<T[]> kGramConsumer,
      T[] kGram,
      int k,
      int bi,
      int ibi,
      int ki) {
    List<T> b = blocks.get(bi);
    while (ibi < b.size() && ki < k) {
      kGram[ki++] = b.get(ibi++);
    }
    // need more instructions to complete k-gram, iterate successors
    if (ki < k) {
      Set<Integer> suc = blockSuccs.get(bi);
      if (suc != null) {
        final int nki = ki;
        // iterate over successors via recursion
        suc.forEach(si -> makeKGrams(blocks, blockSuccs, kGramConsumer, kGram, k, si, 0, nki));
      }
    } else {
      kGramConsumer.accept(Arrays.copyOf(kGram, k));
    }
  }

  public static <T> void getKGrams(
      List<List<T>> blocks,
      Map<Integer, Set<Integer>> blockSuccs,
      Consumer<T[]> kGramConsumer,
      Class<T> type,
      int k) {
    @SuppressWarnings("unchecked")
    T[] kGram = (T[]) Array.newInstance(type, k);
    IndexedList.of(blocks)
        .forEach(
            (bi, b) -> {
              for (int i = 0, s = b.size(); i < s; i++) {
                makeKGrams(blocks, blockSuccs, kGramConsumer, kGram, k, bi, i, 0);
              }
            });
  }
}
