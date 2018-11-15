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

import static javaslang.API.*;
import static javaslang.Predicates.instanceOf;

import de.milux.ordol.helpers.Utils;
import java.util.List;
import java.util.stream.Collectors;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.AbstractInstanceInvokeExpr;

public class Parser {

  private Parser() {}

  public static String getInvokeType(InvokeExpr ie) {
    return Match(ie)
        .of(
            Case(instanceOf(VirtualInvokeExpr.class), Jimple.VIRTUALINVOKE),
            Case(instanceOf(SpecialInvokeExpr.class), Jimple.SPECIALINVOKE),
            Case(instanceOf(InterfaceInvokeExpr.class), Jimple.INTERFACEINVOKE),
            Case(instanceOf(StaticInvokeExpr.class), Jimple.STATICINVOKE),
            Case(instanceOf(DynamicInvokeExpr.class), Jimple.DYNAMICINVOKE),
            Case(
                $(),
                i -> {
                  System.err.println("Unknown InvokeExpr subclass: " + i.getClass().getName());
                  return "invoke";
                }));
  }

  public static String parseClass(SootClass c, List<String> refTypes) {
    if (c.isLibraryClass()) {
      return c.getName();
    } else {
      if (refTypes != null) {
        if (c.isApplicationClass()) {
          refTypes.add(c.getName());
        } else if (c.isPhantomClass()) {
          refTypes.add(null);
        }
      }
      return "#";
    }
  }

  public static String parseType(Type t, List<String> refTypes) {
    if (t instanceof RefType) {
      return parseClass(((RefType) t).getSootClass(), refTypes);
    }
    return t.toString();
  }

  public static String parseField(SootField f, List<String> refTypes) {
    StringBuilder sb = Utils.getBuilder();
    SootClass c = f.getDeclaringClass();
    sb.append("<");
    sb.append(parseClass(c, refTypes));
    sb.append(": ");
    sb.append(parseType(f.getType(), refTypes));
    sb.append(" ");
    sb.append(c.isLibraryClass() ? f.getName() : "#");
    sb.append(">");
    return Utils.freeBuilder(sb);
  }

  public static String parseMethod(SootMethod m, List<String> refTypes) {
    StringBuilder sb = Utils.getBuilder();
    SootClass c = m.getDeclaringClass();
    sb.append("<");
    sb.append(parseClass(c, refTypes));
    sb.append(": ");
    sb.append(parseType(m.getReturnType(), refTypes));
    sb.append(" ");
    sb.append(c.isLibraryClass() ? m.getName() : "#");
    sb.append("(");
    sb.append(
        m.getParameterTypes()
            .stream()
            .map(t -> Parser.parseType(t, refTypes))
            .collect(Collectors.joining(",")));
    sb.append(")>");
    return Utils.freeBuilder(sb);
  }

  public static String parseValue(Value v, List<String> refTypes) {
    return Match(v)
        .of(
            Case(
                instanceOf(Local.class),
                l -> {
                  StringBuilder sb = Utils.getBuilder();
                  sb.append("<");
                  sb.append(parseType(l.getType(), refTypes));
                  sb.append(">");
                  return Utils.freeBuilder(sb);
                }),
            Case(instanceOf(Constant.class), Object::toString),
            Case(
                instanceOf(BinopExpr.class),
                e -> {
                  StringBuilder sb = Utils.getBuilder();
                  sb.append(parseValue(e.getOp1(), refTypes));
                  sb.append(e.getSymbol());
                  sb.append(parseValue(e.getOp2(), refTypes));
                  return Utils.freeBuilder(sb);
                }),
            Case(
                instanceOf(FieldRef.class),
                f -> {
                  StringBuilder sb = Utils.getBuilder();
                  if (f instanceof InstanceFieldRef) {
                    sb.append(parseValue(((InstanceFieldRef) f).getBase(), refTypes));
                    sb.append(".");
                  }
                  sb.append(parseField(f.getField(), refTypes));
                  return Utils.freeBuilder(sb);
                }),
            Case(
                instanceOf(InvokeExpr.class),
                i -> {
                  StringBuilder sb = Utils.getBuilder();
                  sb.append(getInvokeType(i));
                  sb.append(" ");
                  if (i instanceof AbstractInstanceInvokeExpr) {
                    sb.append(parseValue(((AbstractInstanceInvokeExpr) i).getBase(), refTypes));
                    sb.append(".");
                  }
                  sb.append(parseMethod(i.getMethod(), refTypes));
                  sb.append("(");
                  sb.append(
                      i.getArgs()
                          .stream()
                          .map(val -> parseValue(val, refTypes))
                          .collect(Collectors.joining(",")));
                  sb.append(")");
                  return Utils.freeBuilder(sb);
                }),
            Case(
                instanceOf(IdentityRef.class),
                r ->
                    Match(r)
                        .of(
                            Case(instanceOf(ThisRef.class), tr -> "@this"),
                            Case(
                                instanceOf(ParameterRef.class),
                                pr -> {
                                  StringBuilder sb = Utils.getBuilder();
                                  sb.append("@parameter");
                                  sb.append(pr.getIndex());
                                  sb.append(": ");
                                  sb.append(parseType(pr.getType(), refTypes));
                                  return Utils.freeBuilder(sb);
                                }),
                            Case(instanceOf(CaughtExceptionRef.class), ce -> "@caughtexception"),
                            Case(
                                $(),
                                x -> {
                                  System.out.println(
                                      "Unknown sublcass of IdentityRef: " + x.getClass().getName());
                                  return x.toString();
                                }))),
            Case(
                instanceOf(CastExpr.class),
                c -> {
                  StringBuilder sb = Utils.getBuilder();
                  sb.append("(");
                  sb.append(parseType(c.getCastType(), refTypes));
                  sb.append(") ");
                  sb.append(parseValue(c.getOp(), refTypes));
                  return Utils.freeBuilder(sb);
                }),
            Case(
                instanceOf(ArrayRef.class),
                a -> {
                  StringBuilder sb = Utils.getBuilder();
                  sb.append(parseValue(a.getBase(), refTypes));
                  sb.append("[");
                  sb.append(parseValue(a.getIndex(), refTypes));
                  sb.append("]");
                  return Utils.freeBuilder(sb);
                }),
            Case(instanceOf(NewExpr.class), n -> "new " + parseType(n.getType(), refTypes)),
            Case(
                instanceOf(NewArrayExpr.class),
                n -> {
                  StringBuilder sb = Utils.getBuilder();
                  sb.append("newarray (");
                  sb.append(parseType(n.getBaseType(), refTypes));
                  sb.append(")[");
                  sb.append(parseValue(n.getSize(), refTypes));
                  sb.append("]");
                  return Utils.freeBuilder(sb);
                }),
            Case(instanceOf(NegExpr.class), n -> "neg " + parseValue(n.getOp(), refTypes)),
            Case(instanceOf(LengthExpr.class), l -> "lengthof " + parseValue(l.getOp(), refTypes)),
            Case(
                instanceOf(InstanceOfExpr.class),
                io -> {
                  StringBuilder sb = Utils.getBuilder();
                  sb.append(parseValue(io.getOp(), refTypes));
                  sb.append(" instanceof ");
                  sb.append(parseType(io.getCheckType(), refTypes));
                  return Utils.freeBuilder(sb);
                }),
            Case(
                $(),
                x -> {
                  System.out.println("Unknown subclass of Value: " + x.getClass().getName());
                  System.out.println(x.toString());
                  return x.toString();
                }));
  }

  public static String parseUnit(Unit u, List<String> refTypes) {
    return Match(u)
        .of(
            Case(
                instanceOf(AssignStmt.class),
                a -> {
                  StringBuilder sb = Utils.getBuilder();
                  sb.append(Parser.parseValue(a.getLeftOp(), refTypes));
                  sb.append(" = ");
                  sb.append(Parser.parseValue(a.getRightOp(), refTypes));
                  return Utils.freeBuilder(sb);
                }),
            Case(
                instanceOf(IdentityStmt.class),
                i -> "identity " + Parser.parseValue(i.getRightOp(), refTypes)),
            Case(instanceOf(GotoStmt.class), g -> "goto"),
            Case(
                instanceOf(IfStmt.class),
                i -> "if " + Parser.parseValue(i.getCondition(), refTypes)),
            Case(instanceOf(InvokeStmt.class), i -> Parser.parseValue(i.getInvokeExpr(), refTypes)),
            Case(instanceOf(ReturnVoidStmt.class), r -> "return"),
            Case(instanceOf(ReturnStmt.class), r -> Parser.parseValue(r.getOp(), refTypes)),
            Case(
                instanceOf(SwitchStmt.class),
                s ->
                    Match(s)
                        .of(
                            Case(
                                instanceOf(TableSwitchStmt.class),
                                ts -> {
                                  StringBuilder sb = Utils.getBuilder();
                                  sb.append("tableswitch (");
                                  sb.append(Parser.parseValue(ts.getKey(), refTypes));
                                  sb.append(") {");
                                  sb.append(ts.getLowIndex());
                                  sb.append(" - ");
                                  sb.append(ts.getHighIndex());
                                  sb.append("}");
                                  return Utils.freeBuilder(sb);
                                }),
                            Case(
                                instanceOf(LookupSwitchStmt.class),
                                ls -> {
                                  StringBuilder sb = Utils.getBuilder();
                                  sb.append("lookupswitch (");
                                  sb.append(Parser.parseValue(ls.getKey(), refTypes));
                                  sb.append(") {");
                                  sb.append(
                                      ls.getLookupValues()
                                          .stream()
                                          .map(IntConstant::toString)
                                          .collect(Collectors.joining(", ")));
                                  sb.append("}");
                                  return Utils.freeBuilder(sb);
                                }),
                            Case(
                                $(),
                                x -> {
                                  System.out.println(
                                      "Unknown sublcass of SwitchStmt: " + x.getClass().getName());
                                  return x.toString();
                                }))),
            Case(
                instanceOf(ThrowStmt.class),
                t -> "throw " + Parser.parseValue(t.getOp(), refTypes)),
            Case(
                instanceOf(MonitorStmt.class),
                mo ->
                    Match(mo)
                        .of(
                            Case(
                                instanceOf(EnterMonitorStmt.class),
                                en -> "entermonitor " + Parser.parseValue(en.getOp(), refTypes)),
                            Case(
                                instanceOf(ExitMonitorStmt.class),
                                en -> "exitmonitor " + Parser.parseValue(en.getOp(), refTypes)),
                            Case(
                                $(),
                                x -> {
                                  System.out.println(
                                      "Unknown sublcass of MonitorStmt: " + x.getClass().getName());
                                  return x.toString();
                                }))),
            Case(instanceOf(NopStmt.class), nop -> "nop"),
            Case(
                $(),
                x -> {
                  System.out.println("Unknown sublcass of Unit: " + x.getClass().getName());
                  System.out.println(x.toString());
                  return x.toString();
                }));
  }
}
