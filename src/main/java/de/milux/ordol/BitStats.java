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
package de.milux.ordol;

import static de.milux.ordol.Constants.M_PRIME;

import de.milux.ordol.helpers.CLIDispatcher;
import de.milux.ordol.helpers.IOHelper;
import de.milux.ordol.helpers.Utils;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class BitStats implements CLIDispatcher {
  @Override
  public Options getOptions() {
    return new Options();
  }

  @Override
  public void dispatch(CommandLine cmd, Options options) {
    Map<String, BitSet> libBitmaps = new HashMap<>();
    short[] bitFrequencies = new short[M_PRIME];

    // collect common (AND) bits for all versions of each library
    IOHelper.getLibraryMap()
        .forEach(
            (fullLibName, libPath) -> {
              try {
                String[] splitName = fullLibName.split(" ");
                String libName = splitName[0];
                BitSet lh = IOHelper.BITSET_CACHE.get(libPath).bitSet;
                if (libBitmaps.containsKey(libName)) {
                  libBitmaps.get(libName).and(lh);
                } else {
                  libBitmaps.put(libName, (BitSet) lh.clone());
                }
                Utils.println("Processed " + fullLibName);
              } catch (Exception e) {
                System.err.println("Could not read " + fullLibName + " (" + libPath + ")");
                e.printStackTrace();
              }
            });

    // count bit occurrence over all libraries
    libBitmaps.forEach(
        (libName, bits) -> {
          for (int i = 0; i < M_PRIME; i++) {
            if (bits.get(i)) {
              bitFrequencies[i]++;
            }
          }
        });

    // count rates of frequencies
    int[] freqCardinality = new int[libBitmaps.size() + 1];
    for (int b = 0; b < M_PRIME; b++) {
      for (int f = bitFrequencies[b]; f > 0; f--) {
        freqCardinality[f]++;
      }
    }

    // output statistics
    double pDouble = Arrays.stream(freqCardinality).sum();
    double sum = 0;
    for (int i = libBitmaps.size(); i > 0; i--) {
      sum += freqCardinality[i];
      Utils.println(
          "bits occurring in "
              + i
              + " or more libraries: "
              + freqCardinality[i]
              + " ("
              + Utils.toPercent(freqCardinality[i] / pDouble)
              + ", cumulative: "
              + Utils.toPercent(sum / pDouble)
              + ")");
    }
  }
}
