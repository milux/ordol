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

import de.milux.ordol.algo.MethodMatching;
import de.milux.ordol.helpers.Utils;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javaslang.Tuple2;

public class MethodMapping {

  public final MethodData mLib;
  public final MethodData mApp;
  public final double similarity;

  public MethodMapping(MethodData mLib, MethodData mApp, double similarity) {
    this.mLib = mLib;
    this.mApp = mApp;
    this.similarity = similarity;
  }

  private void processUnitsConditional(
      BiConsumer<UnitData, UnitData> processor, Predicate<UnitData> condition) {
    if (mLib.blocks.isEmpty() || mApp.blocks.isEmpty()) {
      return;
    }
    Map<Integer, UnitKGram> lks = mLib.getUniqueKGramsWithCondition(condition);
    Map<Integer, UnitKGram> aks = mApp.getUniqueKGramsWithCondition(condition);
    lks.keySet()
        .forEach(
            hash -> {
              if (aks.containsKey(hash)) {
                UnitKGram lk = lks.get(hash);
                UnitKGram ak = aks.get(hash);
                // safety measure to catch possible hash collisions
                if (UnitKGram.kGramsInstrEqual(lk, ak)) {
                  for (int i = 0, l = lk.size(); i < l; i++) {
                    UnitData lu = lk.get(i);
                    UnitData au = ak.get(i);
                    if (condition.test(lu)) {
                      processor.accept(lu, au);
                    }
                  }
                }
              }
            });
  }

  public void processMethodCorrelations(
      Map<String, MethodMatching> matchMap, double classSimilarity) {
    processUnitsConditional(
        (lu, au) -> {
          Tuple2<String, Integer> lm = lu.refMethod;
          Tuple2<String, Integer> am = au.refMethod;
          // skip external classes ("Phantom References" in Soot during library analysis/mapping)
          if (lm == null || am == null) {
            return;
          }
          MethodMatching mm = matchMap.get(lm._1);
          // the application class in the matching must be equivalent to the expected app class
          if (mm != null && mm.appClass.name.equals(am._1)) {
            double score = mLib.weight * similarity * classSimilarity;
            if (score > 0.) {
              mm.corPoll.update(lm._2, am._2, score, mLib.weight);
            }
          }
        },
        ud -> ud.hasInvocation);
  }

  public void addAssumedTypeMappings(
      PollMap<String> map, String appClassName, double classSimilarity) {
    processUnitsConditional(
        (lu, au) -> {
          for (int i = 0, l = lu.refTypes.length; i < l; i++) {
            String lc = lu.refTypes[i];
            String ac = au.refTypes[i];
            // skip external classes ("Phantom References" in Soot during library analysis/mapping)
            // do not allow constructors to add a mapping to their own class
            if (lc == null || ac == null || (mApp.isConstructor && ac.equals(appClassName))) {
              continue;
            }
            double score = mLib.weight * similarity * classSimilarity;
            if (score > 0.) {
              map.update(lc, ac, score, mLib.weight);
            }
          }
        },
        UnitData::hasRefs);
  }

  @Override
  public String toString() {
    StringBuilder sb = Utils.getBuilder();
    sb.append(mLib.getFullName());
    sb.append(" >>> ");
    sb.append(mApp.getFullName());
    sb.append(" (");
    sb.append(mLib.weight);
    sb.append(", ");
    sb.append(Utils.toPercent(similarity));
    sb.append(")");
    return Utils.freeBuilder(sb);
  }
}
