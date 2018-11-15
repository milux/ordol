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
package de.milux.ordol.helpers;

import java.util.AbstractList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

public class IndexedList<T> extends AbstractList<T> {

  public static <T1> IndexedList<T1> of(List<T1> list) {
    return new IndexedList<>(list);
  }

  private final List<T> innerList;

  public IndexedList(List<T> list) {
    innerList = list;
  }

  public void forEach(BiConsumer<Integer, T> biConsumer) {
    IntStream.range(0, this.size()).forEach(i -> biConsumer.accept(i, this.get(i)));
  }

  public void forEachParallel(BiConsumer<Integer, T> biConsumer) {
    IntStream.range(0, this.size()).parallel().forEach(i -> biConsumer.accept(i, this.get(i)));
  }

  @Override
  public int size() {
    return innerList.size();
  }

  @Override
  public T get(int index) {
    return innerList.get(index);
  }
}
