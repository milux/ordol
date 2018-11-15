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
import de.milux.ordol.helpers.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import javaslang.Tuple;
import javaslang.Tuple2;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.internal.AbstractStmt;

import javax.annotation.Nonnull;

public class UnitData {

  // factory for per-thread ArrayList to buffer referenced types when invoking Parser.parseUnit()
  private static final ThreadLocal<ArrayList<String>> thRefList =
      ThreadLocal.withInitial(() -> new ArrayList<>(3));
  public static final String[] EMPTY_STR_ARRAY = new String[0];

  public static boolean instrEqual(UnitData ud1, UnitData ud2) {
    return Objects.equals(ud1.instr, ud2.instr);
  }

  public static boolean fullyEqual(UnitData ud1, UnitData ud2) {
    return Objects.equals(ud1.instr, ud2.instr) && Arrays.equals(ud1.refTypes, ud2.refTypes);
  }

  public final String instr;
  public final String[] refTypes;
  public final transient boolean hasInvocation;
  public final Tuple2<String, Integer> refMethod;

  public UnitData(Unit u) {
    ArrayList<String> localList = thRefList.get();
    this.instr = Parser.parseUnit(u, localList).intern();
    if (localList.isEmpty()) {
      this.refTypes = EMPTY_STR_ARRAY;
    } else {
      this.refTypes = localList.stream().map(Utils.INTERN_STRING).toArray(String[]::new);
      localList.clear();
    }
    Tuple2<String, Integer> refMethod = null;
    boolean hasInvocation = false;
    if (u instanceof AbstractStmt) {
      AbstractStmt stmt = (AbstractStmt) u;
      if (stmt.containsInvokeExpr()) {
        InvokeExpr ie = stmt.getInvokeExpr();
        SootMethod im = ie.getMethod();
        SootClass ic = im.getDeclaringClass();
        if (!ic.isLibraryClass()) {
          hasInvocation = true;
          if (ic.isApplicationClass()) {
            refMethod = Tuple.of(ic.getName().intern(), ic.getMethods().indexOf(im));
          }
        }
      }
    }
    this.hasInvocation = hasInvocation;
    this.refMethod = refMethod;
  }

  public UnitData(String s) {
    instr = s;
    refTypes = EMPTY_STR_ARRAY;
    hasInvocation = false;
    refMethod = null;
  }

  public UnitData(@Nonnull String instr, @Nonnull String[] refTypes, boolean hasInvocation) {
    this.instr = instr;
    this.refTypes = refTypes.length > 0 ? refTypes : EMPTY_STR_ARRAY;
    this.hasInvocation = hasInvocation;
    this.refMethod = null;
  }

  public UnitData(
      @Nonnull String instr,
      @Nonnull String[] refTypes,
      @Nonnull Tuple2<String, Integer> refMethod) {
    this.instr = instr;
    this.refTypes = refTypes.length > 0 ? refTypes : EMPTY_STR_ARRAY;
    this.hasInvocation = true;
    this.refMethod = refMethod;
  }

  public boolean hasRefs() {
    return this.refTypes != EMPTY_STR_ARRAY;
  }

  @Override
  public int hashCode() {
    return this.instr.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    return (this == o) || (o instanceof UnitData && this.instr.equals(((UnitData) o).instr));
  }

  @Override
  public String toString() {
    StringBuilder sb = Utils.getBuilder();
    sb.append(this.instr);
    if (refTypes.length > 0) {
      for (String refType : refTypes) {
        sb.append(", ");
        sb.append(refType);
      }
    }
    return Utils.freeBuilder(sb);
  }
}
