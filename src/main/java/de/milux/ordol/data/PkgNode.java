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
import java.util.HashMap;
import java.util.Map;

public class PkgNode {
  private long weight;
  private HashMap<String, PkgNode> children = new HashMap<>();

  /**
   * The entry point for the default package object
   *
   * @param cd Object describing the class that will be represented in this structure
   */
  public void attach(ClassData cd) {
    attach(cd.name.split("\\."), 0, cd.weight);
  }

  /**
   * Recursively creates the package structure representation, remembering the weight of registered
   * classes.
   *
   * @param pkg Split Java package name
   * @param depth The nesting level of this object (for index in pkg)
   * @param weight Weight of the class that is registered
   */
  private void attach(String[] pkg, int depth, long weight) {
    // remember weight
    this.weight += weight;
    // if additional packages are available, do recursion (last element is class name!)
    if (depth < pkg.length - 1) {
      // create new child if necessary
      PkgNode child = children.computeIfAbsent(pkg[depth], k -> new PkgNode());
      // propagate pkg to child
      child.attach(pkg, depth + 1, weight);
    }
  }

  /**
   * Returns a string representation of the represented package structure.
   *
   * @return String representation of this package structure
   */
  public String stringify() {
    long childrenWeight = children.values().stream().mapToLong(c -> c.weight).sum();
    StringBuilder sb = new StringBuilder("\n");
    if (children.size() > 0) {
      if (children.size() == 1 && children.values().iterator().next().weight == weight) {
        // special case, all code is contained in a single child node
        stringify("", sb, weight);
      } else {
        // common case, the default package has more than one child
        sb.append("<default package> (")
            .append(Utils.toPercent((double) (weight - childrenWeight) / weight))
            .append(", total: ")
            .append(Utils.toPercent(1.))
            .append(")\n");
        children.forEach((pkg, node) -> node.stringify("  " + pkg, sb, weight));
      }
    } else {
      // special case, everything is found in the default package
      stringify("<default package>", sb, weight);
    }
    return sb.toString();
  }

  /**
   * Creates a string representation of the package structure below the current item.
   *
   * @param prefix The string prefix, consisting of leading spaces and package names
   * @param sb The StringBuilder to use for output collection
   * @param totalWeight The weight at the root of the package structure
   */
  private void stringify(String prefix, StringBuilder sb, double totalWeight) {
    // collapse packages that contain everything below them
    PkgNode curNode = this;
    while (curNode.children.size() == 1) {
      Map.Entry<String, PkgNode> entry = curNode.children.entrySet().iterator().next();
      if (entry.getValue().weight != curNode.weight) {
        break;
      }
      if (!prefix.isEmpty()) {
        prefix += ".";
      }
      prefix += entry.getKey();
      curNode = entry.getValue();
    }
    long childrenWeight = curNode.children.values().stream().mapToLong(c -> c.weight).sum();
    // output current item
    sb.append(prefix)
        .append(" (")
        .append(Utils.toPercent((curNode.weight - childrenWeight) / totalWeight));
    if (childrenWeight > 0) {
      sb.append(", total: ").append(Utils.toPercent(curNode.weight / totalWeight));
    }
    sb.append(")\n");
    // assemble final prefix
    String finalPrefix = "  " + prefix + ".";
    // iterate children
    curNode.children.forEach((pkg, node) -> node.stringify(finalPrefix + pkg, sb, totalWeight));
  }
}
