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

public class DetectionResult {
  private String name;
  private double matchScore;
  private double coverageScore;
  private PkgNode pkgNode;

  public DetectionResult(String name, double matchScore, double coverageScore, PkgNode pkgNode) {
    this.name = name;
    this.matchScore = matchScore;
    this.coverageScore = coverageScore;
    this.pkgNode = pkgNode;
  }

  public String getName() {
    return name;
  }

  public double getMatchScore() {
    return matchScore;
  }

  public double getCoverageScore() {
    return coverageScore;
  }

  public PkgNode getPkgNode() {
    return pkgNode;
  }
}
