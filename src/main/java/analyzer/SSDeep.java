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
package analyzer;

import cz.adamh.utils.NativeUtils;

public class SSDeep {

  static {
    try {
      NativeUtils.loadLibraryFromJar("/" + System.mapLibraryName("ssdeep"));
    } catch (Exception nue) {
      try {
        System.loadLibrary("src/main/resources/ssdeep");
      } catch (Exception e) {
        System.err.println("No ssdeep library found :(");
        nue.printStackTrace();
        e.printStackTrace();
      }
    }
  }

  public static native int fuzzyCompare(String sig1, String sig2);

  public static native String hashString(String s);

  public static double fuzzySimilarity(String sig1, String sig2) {
    return fuzzyCompare(sig1, sig2) / 100.;
  }
}
