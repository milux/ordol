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

import de.milux.ordol.helpers.Utils;
import io.vavr.Tuple;
import io.vavr.Tuple2;

import java.util.*;

public class PollItem<T> {

  public final T type1;
  private HashMap<T, Double> map = new HashMap<>();
  private long weightSum = 0L;
  private boolean canUpdate = true;

  public PollItem(T type1) {
    this.type1 = type1;
  }

  public void update(T type2, double similarity, long weight) {
    if (!this.canUpdate) {
      throw new RuntimeException("Update after getMap() call not possible!");
    }
    weightSum += weight;
    map.put(type2, map.getOrDefault(type2, 0.) + similarity);
  }

  public Tuple2<T, Double> getBestFit() {
    //        double sum = map.values().stream().reduce(0., (v1, v2) -> v1 + v2);
    Set<Map.Entry<T, Double>> es = map.entrySet();
    Map.Entry<T, Double> max =
        Collections.max(
            es,
            (entry1, entry2) -> {
              if (Objects.equals(entry1.getValue(), entry2.getValue())) {
                return 0;
              } else if (entry1.getValue() > entry2.getValue()) {
                return 1;
              } else {
                return -1;
              }
            });
    return Tuple.of(max.getKey(), max.getValue() / weightSum);
  }

  public Map<T, Double> getMap() {
    if (this.canUpdate) {
      this.canUpdate = false;
      //            double sum = map.values().stream().reduce(0., (v1, v2) -> v1 + v2);
      map.keySet()
          .forEach(
              k -> map.put(k, weightSum == 0L ? 0. : map.get(k) / weightSum));
    }
    return map;
  }

  public long getWeightSum() {
    return weightSum;
  }

  @Override
  public String toString() {
    Tuple2<T, Double> best = getBestFit();
    StringBuilder sb = Utils.getBuilder();
    sb.append(type1);
    sb.append(" >>> ");
    sb.append(best._1);
    sb.append(" (");
    sb.append(Utils.toPercent(best._2));
    sb.append(", ");
    sb.append(weightSum);
    sb.append(" weightSum)");
    return Utils.freeBuilder(sb);
  }
}
