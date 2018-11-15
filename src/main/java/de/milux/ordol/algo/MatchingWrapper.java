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

import de.milux.ordol.helpers.CustomCollectors;
import de.milux.ordol.helpers.IndexedList;
import de.milux.ordol.helpers.Utils;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MatchingWrapper<R, C> {
  private List<R> rowList;
  private List<C> colList;
  private MWBMatchingAlgorithm mwbm;
  private List<Integer> matching;
  private double[][] weights;

  public MatchingWrapper(List<R> rowList, List<C> colList, BiFunction<R, C, Double> simFunc) {
    this(
        rowList,
        colList,
        simFunc,
        sim -> {
          if (sim < 0. || sim > 1.) {
            throw new IllegalArgumentException(
                "Similarity Function must return values in range [0.0; 1.0], encountered " + sim);
          }
          return sim == 0. ? Double.NEGATIVE_INFINITY : 1. + sim;
        });
  }

  public MatchingWrapper(
      List<R> rowList,
      List<C> colList,
      BiFunction<R, C, Double> simFunc,
      Function<Double, Double> algoMapper) {
    this.rowList = rowList;
    this.colList = colList;
    mwbm = new MWBMatchingAlgorithm(rowList.size(), colList.size());
    weights = new double[rowList.size()][colList.size()];
    IndexedList.of(rowList)
        .forEach(
            (i, re) -> {
              double[] row = new double[colList.size()];
              IndexedList.of(colList)
                  .forEach(
                      (j, ce) -> {
                        final double sim = simFunc.apply(re, ce);
                        row[j] = sim;
                        mwbm.setWeight(i, j, algoMapper.apply(sim));
                      });
              weights[i] = row;
            });
  }

  public MatchingWrapper(List<R> rowList, List<C> colList, double[][] weights) {
    this.rowList = rowList;
    this.colList = colList;
    mwbm = new MWBMatchingAlgorithm(weights);
    this.weights = weights;
  }

  public double[][] getWeights() {
    return weights;
  }

  public List<R> getUnmatched() {
    ArrayList<R> unmatched = new ArrayList<>();
    IndexedList.of(getMatching())
        .forEach(
            (i, j) -> {
              if (j < 0) {
                unmatched.add(rowList.get(i));
              }
            });
    return Utils.getTrimmed(unmatched);
  }

  public List<Integer> getMatching() {
    if (matching == null) {
      matching =
          Arrays.stream(mwbm.getMatching()).boxed().collect(CustomCollectors.toCompactROList());
    }
    return matching;
  }

  public double getWeight(int i, int j) {
    return weights[i][j];
  }

  public Set<R> getMatchedRows() {
    Set<R> matchedRows = new HashSet<>();
    IndexedList.of(getMatching())
        .forEach(
            (i, j) -> {
              if (j >= 0) {
                matchedRows.add(rowList.get(i));
              }
            });
    return matchedRows;
  }

  public Set<C> getMatchedColumns() {
    Set<C> matchedColumns = new HashSet<>();
    getMatching()
        .forEach(
            j -> {
              if (j >= 0) {
                matchedColumns.add(colList.get(j));
              }
            });
    return matchedColumns;
  }

  public void forEach(BiConsumer<R, C> con) {
    IndexedList.of(getMatching())
        .forEach(
            (i, j) -> {
              if (j >= 0) {
                con.accept(rowList.get(i), colList.get(j));
              }
            });
  }

  public void forEach(Consumer3<R, C, Double> con) {
    IndexedList.of(getMatching())
        .forEach(
            (i, j) -> {
              if (j >= 0) {
                con.accept(rowList.get(i), colList.get(j), weights[i][j]);
              }
            });
  }
}
