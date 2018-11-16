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

import de.milux.ordol.algo.Parser;
import de.milux.ordol.helpers.CustomCollectors;
import de.milux.ordol.helpers.IndexedList;
import de.milux.ordol.helpers.Utils;
import io.vavr.Lazy;
import io.vavr.control.Try;
import soot.Body;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.internal.AbstractStmt;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.ExceptionalBlockGraph;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static de.milux.ordol.Constants.K;
import static de.milux.ordol.helpers.Utils.emptyOrROList;
import static de.milux.ordol.helpers.Utils.emptyOrROMap;

public class MethodData {
  public final String name;
  public final List<String> fixTypes;
  public final List<List<UnitData>> blocks;
  public final Map<Integer, Set<Integer>> blockSuccs;
  public final String ctph;
  public final List<String> refs;
  public final int idxInClass;
  public final transient int weight;
  public final transient boolean isConstructor;
  private Set<Integer> kGramHashes;
  public final transient List<UnitData> instr;

  public MethodData(SootMethod m, int idxInClass) {
    this.name = m.getName();
    this.isConstructor = "<init>".equals(this.name) || "<clinit>".equals(this.name);
    List<String> paramTypes =
        m.getParameterTypes()
            .stream()
            .map(t -> Parser.parseType(t, null).intern())
            .collect(CustomCollectors.toCompactROList());
    this.fixTypes = emptyOrROList(paramTypes);
    if (m.isConcrete()) {
      Body body = m.retrieveActiveBody();
      // collect blocks of the ExceptionalUnitGraph
      ExceptionalBlockGraph blockGraph = new ExceptionalBlockGraph(body);
      Map<Block, Integer> blockEnum = new HashMap<>();
      List<List<UnitData>> blocks = new ArrayList<>(blockGraph.size());
      IndexedList<Block> idxBlockList = IndexedList.of(blockGraph.getBlocks());
      idxBlockList.forEach(
          (i, b) -> {
            blocks.add(
                StreamSupport.stream(b.spliterator(), false)
                    .map(UnitData::new)
                    .collect(CustomCollectors.toCompactROList()));
            blockEnum.put(b, i);
          });
      this.blocks = emptyOrROList(blocks);
      // collect edges of the ExceptionalUnitGraph
      Map<Integer, Set<Integer>> blockSuccs = new HashMap<>();
      idxBlockList.forEach(
          (i, b) -> {
            Set<Integer> succs =
                b.getSuccs().stream().map(blockEnum::get).collect(Collectors.toSet());
            if (!succs.isEmpty()) {
              blockSuccs.put(i, Collections.unmodifiableSet(succs));
            }
          });
      this.blockSuccs = emptyOrROMap(blockSuccs);
      List<String> refs =
          body.getUnits()
              .stream()
              .map(
                  u ->
                      Try.of(
                              () -> {
                                if (u instanceof AbstractStmt) {
                                  AbstractStmt stmt = (AbstractStmt) u;
                                  if (stmt.containsInvokeExpr()) {
                                    InvokeExpr ie = stmt.getInvokeExpr();
                                    SootMethod im = ie.getMethod();
                                    if (im.getDeclaringClass().isLibraryClass()) {
                                      return im.toString().intern();
                                    }
                                  } else if (stmt.containsFieldRef()) {
                                    FieldRef fr = stmt.getFieldRef();
                                    SootField f = fr.getField();
                                    if (f.getDeclaringClass().isLibraryClass()) {
                                      return f.toString().intern();
                                    }
                                  }
                                }
                                return null;
                              })
                          .onFailure(Throwable::printStackTrace)
                          .getOrElse(() -> null))
              .filter(Objects::nonNull)
              .collect(CustomCollectors.toCompactROList());

      //      String instr =
      //          body.getUnits().stream().map(u -> new
      // UnitData(u).instr).collect(Collectors.joining(";"));
      //      this.ctph = SSDeep.hashString(instr);
      this.ctph = null;
      this.refs = emptyOrROList(refs);
      this.weight = this.blocks.stream().mapToInt(List::size).sum();
      this.instr =
          Lazy.val(
              () ->
                  emptyOrROList(
                      this.blocks
                          .stream()
                          .reduce(
                              new ArrayList<>(),
                              (a, b) -> {
                                a.addAll(b);
                                return a;
                              })),
              List.class);
    } else {
      this.blocks = Collections.emptyList();
      this.blockSuccs = Collections.emptyMap();
      this.ctph = null;
      this.refs = Collections.emptyList();
      this.weight = 0;
      this.instr = Collections.emptyList();
    }
    this.idxInClass = idxInClass;
  }

  public MethodData(
      String name,
      List<String> fixTypes,
      List<List<UnitData>> blocks,
      Map<Integer, Set<Integer>> blockSuccs,
      String ctph,
      List<String> refs,
      int idxInClass) {
    boolean isConcrete = !blocks.isEmpty();
    this.name = name;
    this.fixTypes = emptyOrROList(fixTypes);
    this.blocks = emptyOrROList(blocks);
    this.blockSuccs = emptyOrROMap(blockSuccs);
    this.ctph = ctph;
    this.refs = emptyOrROList(refs);
    this.idxInClass = idxInClass;
    this.isConstructor = "<init>".equals(this.name) || "<clinit>".equals(this.name);
    this.weight = isConcrete ? this.blocks.stream().mapToInt(List::size).sum() : 0;
    this.instr =
        isConcrete
            ? Lazy.val(
                () ->
                    Collections.unmodifiableList(
                        this.blocks
                            .stream()
                            .reduce(
                                new ArrayList<>(),
                                (a, b) -> {
                                  a.addAll(b);
                                  return a;
                                })),
                List.class)
            : Collections.emptyList();
  }

  public void getUnitKGrams(Consumer<UnitKGram> kGramConsumer) {
    KGram.getKGrams(
        blocks, blockSuccs, ka -> kGramConsumer.accept(new UnitKGram(ka)), UnitData.class, K);
  }

  /**
   * This method creates a Map of all k-grams (and their corresponding hashes) of this method having
   * >= 1 instruction that fulfills the given condition, except those that appear with identical
   * instructions but different methods, as those are ambiguous for matching.
   *
   * @return Map of all distinct k-grams with their hash values as keys
   */
  public Map<Integer, UnitKGram> getUniqueKGramsWithCondition(Predicate<UnitData> condition) {
    Set<Integer> blackList = new HashSet<>();
    Map<Integer, UnitKGram> result = new HashMap<>();
    getUnitKGrams(
        kGram -> {
          if (kGram.stream().anyMatch(condition)) {
            int hash = kGram.hashCode();
            if (blackList.contains(hash)) {
              return;
            }
            if (result.containsKey(hash)) {
              if (!UnitKGram.kGramsFullyEqual(kGram, result.get(hash))) {
                result.remove(hash);
                blackList.add(hash);
              }
            } else {
              result.put(hash, kGram);
            }
          }
        });
    return result;
  }

  public synchronized Set<Integer> getKGramHashes() {
    // check reference without locking
    if (this.kGramHashes == null) {
      synchronized (this) {
        // check if the previous thread already did the work
        if (this.kGramHashes != null) {
          return this.kGramHashes;
        }
        // hashes of all k-grams over all basic blocks
        Set<Integer> hashes = new HashSet<>();
        getUnitKGrams(kGram -> hashes.add(kGram.hashCode()));
        this.kGramHashes = Collections.unmodifiableSet(hashes);
      }
    }
    return this.kGramHashes;
  }

  public String getFullName() {
    StringBuilder sb = Utils.getBuilder();
    sb.append(this.name);
    sb.append("(");
    sb.append(String.join(", ", this.fixTypes));
    sb.append(")");
    return Utils.freeBuilder(sb);
  }

  /** Hash value is cached here */
  private int hashValue = 0;

  @Override
  public int hashCode() {
    if (hashValue == 0) {
      this.hashValue = this.name.hashCode() ^ blocks.hashCode() ^ blockSuccs.hashCode();
    }
    return hashValue;
  }

  @Override
  public boolean equals(Object o) {
    return (this == o)
        || (o instanceof MethodData
            && this.name.equals(((MethodData) o).name)
            && this.weight == ((MethodData) o).weight
            && this.fixTypes.equals(((MethodData) o).fixTypes)
            && this.blocks.equals(((MethodData) o).blocks)
            && this.blockSuccs.equals(((MethodData) o).blockSuccs));
  }
}
