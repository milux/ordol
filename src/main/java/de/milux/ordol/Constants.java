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

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.regex.Pattern;
import javaslang.collection.List;

public final class Constants {

  /** Filesystem stuff */
  public static final FileSystem FS = FileSystems.getDefault();

  public static Path ANDROID_PLATFORMS = FS.getPath(System.getenv("ANDROID_HOME"), "platforms");
  public static Path ANDROID_JAR =
      ANDROID_PLATFORMS.resolve(FS.getPath("android-23", "android.jar"));
  public static Path LIBS_DIRECTORY = FS.getPath("ordol_libs");
  public static Path BITSET_CACHE_PATH =
      FS.getPath(System.getProperty("java.io.tmpdir"), "LibDetector");
  /** Path to javac compiler for compilation of source files */
  public static String JAVAC_EXEC = "javac";
  /**
   * The platform-dependent command used to execute scripts (cmd, sh, ...) as immutable JavaSlang
   * List
   */
  public static List<String> SCRIPT_INTERPRETER = List.of("cmd /c".split(" "));
  /** Maximum number of threads for parallelized analysis */
  public static int NUM_THREADS = Runtime.getRuntime().availableProcessors();
  /** Threshold for a library to be scanned for in the analyzed application. */
  public static String BITSET_THRESHOLD = "max(0.1 + appBsPop, 0.9 - 0.2 * log10(max(1, size)))";
  /** Range from the best result (bit hashes) downwards to be examined in one round */
  public static double SCAN_RANGE = .03;
  /** Threshold for a library to be considered as contained in the analyzed application. */
  public static String DETECT_THRESHOLD = "max(0.1, 0.7 - 0.15 * log10(max(1, size)))";
  /**
   * Threshold for type mapping and call analysis (iterative part of detection) For a matched method
   * in a matched class being used, it must fulfill the following condition: Similarity(app_class,
   * lib_class) * Similarity(app_method, lib_method) >= MAPPING_THRESHOLD
   */
  public static double MAPPING_THRESHOLD = .0;
  /**
   * BitSet-Threshold for classes to be considered somewhat similar. Classes with a lower similarity
   * during their "pre-check" will not be examined further. Similarity of classes that end up with a
   * lower value than this threshold will be set to 0.
   */
  public static double SIMILARITY_CUTOFF_THRESHOLD = .1;
  /**
   * The influence of the assumed calls of a method for the method matching in a matched class pair
   * Value 0 means the assumed calls aren't used for optimization at all. Only the similarity of the
   * contents is used to map application methods to library methods. Value 1 means that the
   * information about assumed calls completely replaces the content-based similarity. Any value in
   * between represents a mix of content-based similarity with statistics about assumed calls.
   */
  public static double CALLREF_INFLUENCE = .99;
  /** Allow app classes to have more methods as lib classes */
  public static boolean ALLOW_ADDITIONAL_APP_CLASS_METHODS = false;
  /** Length of K-grams */
  public static int K = 5;
  /** Largest prime < 2^23 (2^23 - 15), used for bit vectors over whole libraries */
  public static int M_PRIME = 8388593;
  /**
   * max. hashed entries for each prime, collision prob. <= 50% for n <= 599 Calculated according to
   * birthday paradox: floor(1.17 * sqrt(prime))
   */
  public static int[] LIMITS = {
    13, 37, 105, 299,
  };
  /** Prime numbers for bit field lengths */
  public static int[] PRIMES = {
    127, 1021, 8191, 65521, 262139,
  };
  /**
   * Results to log for output, flags can be combined via bitwise or 1 = print mismatching class
   * names and missed classes (for non-obfuscated apps) 2 = resulting class mappings 4 = also print
   * information about method matchings in classes
   */
  public static int VERBOSE = 0;
  /** Whether to log time consumed for certain operations */
  public static boolean LOG_BENCHMARKS = false;
  /** A Blacklist for the LibraryMapper */
  public static Pattern LIB_BLACKLIST = Pattern.compile("^$");
  /** A Blacklist for the AppAnalyzer */
  public static Pattern EXCLUDE_LIBS = Pattern.compile("^$");
}
