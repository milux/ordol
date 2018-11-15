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

import de.milux.ordol.Constants;
import de.milux.ordol.data.*;
import de.milux.ordol.helpers.DoubleHolder;
import java.util.*;
import org.apache.commons.collections4.Equator;
import org.apache.commons.collections4.ListUtils;

public class MethodMatching {

  private List<MethodMapping> maxMapping = null;
  private double classSimilarity = 0.;
    public final ClassData libClass;
    public final ClassData appClass;
  public final PollMap<Integer> corPoll = new PollMap<>();

  public MethodMatching(ClassData libClass, ClassData appClass) {
    this(libClass, appClass, false);
  }

  public MethodMatching(ClassData lc, ClassData ac, boolean simOnly) {
    this.libClass = lc;
    this.appClass = ac;
    if (!simOnly) {
      maxMapping = Collections.emptyList();
    }
    // if expected superclass is android library class, and the types or names do not match, this
    // can't be a match
    if (lc.libSuperClass != null
        && (ac.libSuperClass == null || !lc.libSuperClass.equals(ac.libSuperClass))) {
      // construction finished here, nothing to compare/generate on method level
      return;
    }
    // if either class contains no methods, it is impossible to match
    if (ac.isEmpty() || lc.isEmpty()) {
      // construction finished here, nothing to compare/generate on method level
      return;
    }
    this.classSimilarity = calcClassSimilarity(lc, ac, simOnly);
  }

  public double calcClassSimilarity(ClassData lc, ClassData ac, boolean simOnly) {
    if (lc.weight == 0 || ac.weight == 0) {
      return 0.;
    }
    DoubleHolder simHolder = new DoubleHolder(0.);
    MatchingWrapper<MethodData, MethodData> mw =
        new MatchingWrapper<>(lc, ac, this::getMethodSimilarity);
    if (simOnly) {
      mw.forEach((mLib, mApp, sim) -> simHolder.add(sim * mLib.weight));
    } else {
      maxMapping = new ArrayList<>(this.appClass.size());
      mw.forEach(
          (mLib, mApp, sim) -> {
            maxMapping.add(new MethodMapping(mLib, mApp, sim));
            simHolder.add(sim * mLib.weight);
          });
    }
    // divide by total weight of the library class
    double classSim = simHolder.get() / lc.weight;
    // set to 0 below cutoff value
    return classSim < Constants.SIMILARITY_CUTOFF_THRESHOLD ? 0. : classSim;
  }

  public void rematchWithInvocationStats() {
    this.maxMapping = new ArrayList<>(this.appClass.size());
    MatchingWrapper<MethodData, MethodData> mw =
        new MatchingWrapper<>(
            libClass,
            appClass,
            (mLib, mApp) -> {
              double mSim = getMethodSimilarity(mLib, mApp) * (1. - Constants.CALLREF_INFLUENCE);
              double corScore =
                  corPoll.get(mLib.idxInClass, mApp.idxInClass) * Constants.CALLREF_INFLUENCE;
              return corScore + mSim;
            });
    mw.forEach((mLib, mApp, sim) -> this.maxMapping.add(new MethodMapping(mLib, mApp, sim)));
  }

  public static List<UnitData> getCommonUnits(List<UnitData> lApp, List<UnitData> lLib) {
    return ListUtils.longestCommonSubsequence(
        lApp,
        lLib,
        new Equator<UnitData>() {
          @Override
          public boolean equate(UnitData ud1, UnitData ud2) {
            return UnitData.instrEqual(ud1, ud2);
          }

          @Override
          public int hash(UnitData ud) {
            return ud.hashCode();
          }
        });
  }

  public double getMethodSimilarity(MethodData mLib, MethodData mApp) {
    // (class) constructors can only match (class) constructors, no exception!
    if ((mLib.isConstructor || mApp.isConstructor) && !mApp.name.equals(mLib.name)) {
      return 0.;
    }

    // Measure similarity of methods based on k-grams
    Set<Integer> hLib = mLib.getKGramHashes();
      Set<Integer> hApp = mApp.getKGramHashes();
    if (!hLib.isEmpty() && !hApp.isEmpty()) {
      Set<Integer> isec = new HashSet<>(hLib);
      isec.retainAll(hApp);
      return 2. * isec.size() / (hLib.size() + hApp.size());
    } else {
      // if both methods are empty, it's a perfect match, otherwise it's the opposite
      if (hLib.size() == hApp.size()) {
        return 1.;
      } else {
        return 0.;
      }
    }

//      // Measure similarity of methods based on longest common sequences (and used types
//      // except for initial cycle)
//      List<UnitData> lLib = mLib.fuzzyInstructions, lApp = mApp.fuzzyInstructions;
//      if (lLib.size() > 0 && lApp.size() > 0) {
//          double common = (double) LCS.lcsLength(lApp, lLib, UnitData::hashCode,
//                  UnitData::instrEqual);
//          return 2. * common / (double) (lLib.size() + lApp.size());
//      } else {
//          // if both methods are empty, it's a perfect match, otherwise it's the opposite
//          if (lLib.size() == lApp.size()) {
//              return 1.;
//          } else {
//              return 0.;
//          }
//      }
//
//      // Measure similarity of methods based on SSDeep fuzzy hash comparison
//      if (mApp.ctph != null && mLib.ctph != null) {
//          double ssSim = 0.;
//          if (mApp.ctph.equals(mLib.ctph)) {
//              return 1.;
//          } else {
//              return SSDeep.fuzzySimilarity(mApp.ctph, mLib.ctph);
//          }
//      } else {
//          if (mApp.ctph == mLib.ctph) {
//              return 1.;
//          } else {
//              return 0.;
//          }
//      }
//
//      // Measure similarity of methods based on references to known external methods
//      // (Android library)
//      List<String> lApp = mApp.refs, lLib = mLib.refs;
//      if (lApp.size() > 0 && lLib.size() > 0) {
//          List<String> lcs = ListUtils.longestCommonSubsequence(lApp, lLib);
//          return (double) lcs.size() / (double) lApp.size();
//      } else {
//          return lApp.size() == lLib.size() ? 1. : 0.;
//      }
  }

  public void processMethodCorrelations(Map<String, MethodMatching> matchMap) {
    this.getMaxMapping()
        .forEach(
            mm -> {
              if (this.classSimilarity * mm.similarity >= Constants.MAPPING_THRESHOLD) {
                mm.processMethodCorrelations(matchMap, this.classSimilarity);
              }
            });
  }

  public void addAssumedTypeMappings(PollMap<String> map, String appClassName) {
    this.getMaxMapping()
        .forEach(
            mm -> {
              if (this.classSimilarity * mm.similarity >= Constants.MAPPING_THRESHOLD) {
                mm.addAssumedTypeMappings(map, appClassName, this.classSimilarity);
              }
            });
  }

  public double getClassSimilarity() {
    return this.classSimilarity;
  }

  public List<MethodMapping> getMaxMapping() {
    return maxMapping;
  }
}
