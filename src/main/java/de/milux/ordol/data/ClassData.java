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
import de.milux.ordol.helpers.Utils;
import java.util.*;
import java.util.stream.Collectors;
import soot.SootClass;

public class ClassData extends AbstractList<MethodData> implements Comparable<ClassData> {
  /** The hash value of a default <init> method */
  public static final int STD_CONSTRUCTOR_HASH = 610540073;

  public final String name;
  public final String libSuperClass;
  public final String appSuperClass;
  public final Set<String> interfaces;
  public final MethodData[] methodDataArray;
  public final transient long weight;
  private transient BitSet[] bitSets;
  private transient int hashBitCount = -1;
  private transient int hashPrimeIdx = -1;

  public ClassData(SootClass c) {
    List<MethodData> methods = new ArrayList<>();
    IndexedList.of(c.getMethods())
        .forEach(
            (i, m) -> {
              MethodData md = new MethodData(m, i);
              // ignore default constructors
              if (!md.isConstructor || md.hashCode() != STD_CONSTRUCTOR_HASH) {
                methods.add(new MethodData(m, i));
              }
            });
    this.methodDataArray = methods.toArray(new MethodData[0]);
    this.name = c.getName().intern();
    // get the nearest super class in hierarchy which is a library class, or null if
    // java.lang.Object
    if (c.hasSuperclass()) {
      SootClass superCl = c.getSuperclass();
      this.appSuperClass = superCl.isApplicationClass() ? superCl.getName().intern() : null;
      while (superCl.hasSuperclass() && !superCl.isLibraryClass()) {
        superCl = superCl.getSuperclass();
      }
      this.libSuperClass = superCl.isLibraryClass() ? superCl.getName().intern() : null;
    } else {
      this.libSuperClass = null;
      this.appSuperClass = null;
    }
    this.interfaces =
        Utils.emptyOrROSet(
            c.getInterfaces()
                .stream()
                .filter(SootClass::isApplicationClass)
                .map(SootClass::getName)
                .map(String::intern)
                .collect(Collectors.toSet()));
    this.weight = this.stream().mapToLong(m -> m.weight).sum();
  }

  public ClassData(
      String name,
      List<MethodData> methods,
      String libSuperClass,
      String appSuperClass,
      Set<String> interfaces) {
    this.methodDataArray = methods.toArray(new MethodData[0]);
    this.name = name;
    this.libSuperClass = libSuperClass;
    this.appSuperClass = appSuperClass;
    this.interfaces = Utils.emptyOrROSet(interfaces);
    this.weight = this.stream().mapToLong(m -> m.weight).sum();
  }

  private void ensureBitSets() {
    if (this.hashPrimeIdx == -1) {
      synchronized (this) {
        // hashPrimeIdx is the last value set
        if (this.hashPrimeIdx >= 0) {
          return;
        }
        // make bit hashes over all methods' k-grams in this class
        Set<Integer> hashes = new HashSet<>();
        this.forEach(m -> hashes.addAll(m.getKGramHashes()));
        this.bitSets = Utils.makeBitSets(hashes);
        this.hashBitCount = hashes.size();
        this.hashPrimeIdx = Utils.getBitSetPrimeIdx(hashes.size());
      }
    }
  }

  public BitSet[] getBitSets() {
    ensureBitSets();
    return bitSets;
  }

  public int getHashBitCount() {
    ensureBitSets();
    return hashBitCount;
  }

  public int getHashPrimeIdx() {
    ensureBitSets();
    return hashPrimeIdx;
  }

  @Override
  public MethodData get(int index) {
    return this.methodDataArray[index];
  }

  @Override
  public int size() {
    return this.methodDataArray.length;
  }

  @Override
  public int compareTo(ClassData o) {
    return this.name.compareTo(o.name);
  }

  /** Hash value is cached here */
  private int hashValue = 0;

  @Override
  public int hashCode() {
    if (hashValue == 0) {
      this.hashValue = this.name.hashCode() ^ super.hashCode();
    }
    return hashValue;
  }

  @Override
  public boolean equals(Object o) {
    return (this == o)
        || (o instanceof ClassData
            && this.name.equals(((ClassData) o).name)
            && this.weight == ((ClassData) o).weight
            && Objects.equals(this.libSuperClass, ((ClassData) o).libSuperClass)
            && Objects.equals(this.appSuperClass, ((ClassData) o).appSuperClass)
            && this.interfaces.equals(((ClassData) o).interfaces)
            && Arrays.equals(this.methodDataArray, ((ClassData) o).methodDataArray));
  }
}
