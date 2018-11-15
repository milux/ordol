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

import java.util.function.BiFunction;

public class UnitKGram extends KGram<UnitData> {
  private static boolean kGramsEqual(
      UnitKGram k1, UnitKGram k2, BiFunction<UnitData, UnitData, Boolean> op) {
    if (k1.size() != k2.size()) {
      return false;
    }
    for (int i = 0, s = k1.size(); i < s; i++) {
      if (!op.apply(k1.get(i), k2.get(i))) {
        return false;
      }
    }
    return true;
  }

  public static boolean kGramsInstrEqual(UnitKGram k1, UnitKGram k2) {
    return kGramsEqual(k1, k2, UnitData::instrEqual);
  }

  public static boolean kGramsFullyEqual(UnitKGram k1, UnitKGram k2) {
    return kGramsEqual(k1, k2, UnitData::fullyEqual);
  }

  public UnitKGram(UnitData[] array) {
    super(array);
  }
}
