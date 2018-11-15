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

import static de.milux.ordol.Constants.ALLOW_ADDITIONAL_APP_CLASS_METHODS;
import static de.milux.ordol.Constants.SIMILARITY_CUTOFF_THRESHOLD;

import de.milux.ordol.data.ClassData;
import de.milux.ordol.helpers.Utils;

public class ApproxMethodMatching extends MethodMatching {

  public ApproxMethodMatching(ClassData libClass, ClassData appClass) {
    super(libClass, appClass, true);
  }

  @Override
  public double calcClassSimilarity(ClassData lc, ClassData ac, boolean simOnly) {
    if (ac.size() <= lc.size() || ALLOW_ADDITIONAL_APP_CLASS_METHODS) {
      double approxSim =
          Utils.getBitSetSimilarity(
              lc.getHashPrimeIdx(),
              ac.getHashPrimeIdx(),
              lc.getHashBitCount(),
              lc.getBitSets(),
              ac.getBitSets());
      if (approxSim > SIMILARITY_CUTOFF_THRESHOLD) {
        return super.calcClassSimilarity(lc, ac, simOnly);
      }
    }
    return 0.;
  }
}
