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

import de.milux.ordol.algo.ApproxMethodMatching;
import de.milux.ordol.algo.MatchingWrapper;
import de.milux.ordol.algo.MethodMatching;
import de.milux.ordol.data.*;
import de.milux.ordol.helpers.*;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.Tuple4;
import io.vavr.control.Try;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import soot.G;
import soot.PackManager;
import soot.Scene;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static de.milux.ordol.Constants.*;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class AppAnalyzer implements CLIDispatcher {

  private AtomicInteger finCount = new AtomicInteger(0);
  private AtomicInteger wrongAssign = new AtomicInteger(0);
  private AtomicInteger wrongDetect = new AtomicInteger(0);
  private AtomicInteger unmatchedCount = new AtomicInteger(0);
  private Map<String, Tuple3<Integer, Integer, Integer>> errorCounters =
      Collections.synchronizedMap(new HashMap<>());
  private static final AtomicInteger threadEnumerator = new AtomicInteger(0);
  private long threadId = threadEnumerator.incrementAndGet();

  @Override
  public Options getOptions() {
    return new Options()
        .addOption(
            Option.builder("apk")
                .required()
                .hasArg()
                .argName("apk_path")
                .desc("Android Application Package to be analyzed")
                .build())
        .addOption(
            Option.builder("v")
                .longOpt("verbose")
                .hasArg()
                .argName("level")
                .type(Number.class)
                .desc(
                    "The desired output elements as a bitmask (defaults to 0):\n"
                        + "1 = Print mismatching class names and missed classes (for non-obfuscated apps)\n"
                        + "2 = Resulting class mappings\n"
                        + "4 = Also print information about methods in affected classes")
                .build())
        .addOption(
            Option.builder("mth")
                .longOpt("mapping-threshold")
                .hasArg()
                .argName("threshold")
                .type(Number.class)
                .desc(
                    "Threshold for type mapping and call analysis "
                        + "(iterative part of detection)\nFor a matched method in a matched class being used, "
                        + "it must fulfill the following condition:\n"
                        + "sim(app_class, lib_class) * sim(app_method, lib_method) >= MAPPING_THRESHOLD")
                .build())
        .addOption(
            Option.builder("scth")
                .longOpt("similarity-cutoff-threshold")
                .hasArg()
                .argName("threshold")
                .type(Number.class)
                .desc(
                    "Class similarity below this threshold will be regarded to be zero.\n"
                        + "Further, this value must be reached in BitSet comparison "
                        + "to actually compare two classes with each other.")
                .build())
        .addOption(
            Option.builder("cri")
                .longOpt("call-reference-influence")
                .hasArg()
                .argName("influence")
                .type(Number.class)
                .desc(
                    "The influence of the call analysis for reordering of method matches "
                        + "within matched classes. 0.0 means that call analysis does not change anything, "
                        + "1.0 means call analysis completely replaces content-based comparison. "
                        + "The default value is "
                        + CALLREF_INFLUENCE)
                .build())
        .addOption(
            Option.builder("scr")
                .longOpt("scan-range")
                .hasArg()
                .argName("range")
                .type(Number.class)
                .desc(
                    "The maximum distance of the BitSet similarity of a library to the library with "
                        + "the best result. A small value can speed up analysis but increases the risk of "
                        + "detection of wrong library versions or even wrong libraries, "
                        + "along with very restricted information about alternative matches. "
                        + "The default value is "
                        + SCAN_RANGE)
                .build())
        .addOption(
            Option.builder("out")
                .longOpt("output-directory")
                .hasArg()
                .argName("directory")
                .desc(
                    "The name of the directory where to write information about the detected libraries to, "
                        + "in JSON format. The path is resolved relatively "
                        + "to the parent directory of the anlyzed app.")
                .build())
        .addOption(
            Option.builder("bench").desc("Add benchmark data (timings) to output result.").build())
        .addOption(
            Option.builder("f")
                .longOpt("force")
                .desc(
                    "Scan all APKs, "
                        + "even if there is already a JSON file with results for them.")
                .build())
        .addOption(
            Option.builder("pa")
                .longOpt("parallel-analysis")
                .desc(
                    "Analyze multiple APKs in parallel, defaults to 1 (no app-wise parallelization)")
                .hasArg()
                .argName("num")
                .type(Number.class)
                .build())
        .addOption(
            Option.builder("bsth")
                .longOpt("bitset-threshold")
                .desc(
                    "The formula for the calculation of the bitset threshold, default:\nmax(0.1 + appBsPop, "
                        + "0.9 - 0.2 * log10(max(1, size)))")
                .hasArg()
                .argName("formula")
                .build())
        .addOption(
            Option.builder("dtth")
                .longOpt("detect-threshold")
                .desc(
                    "The formula for the calculation of the detection threshold, default:\n"
                        + "max(0.1, 0.7 - 0.15 * log10(max(1, size)))")
                .hasArg()
                .argName("formula")
                .build());
  }

  @Override
  public void dispatch(CommandLine cmd, Options options) throws ParseException {
    // override default verbosity
    VERBOSE = CLIHelper.validateInt(cmd, "v", 0, 7, VERBOSE);
    // override default mapping threshold
    MAPPING_THRESHOLD = CLIHelper.validateDouble(cmd, "mth", 0., 1., MAPPING_THRESHOLD);
    // override similarity cutoff threshold
    SIMILARITY_CUTOFF_THRESHOLD =
        CLIHelper.validateDouble(cmd, "scth", 0., 1., SIMILARITY_CUTOFF_THRESHOLD);
    // override call reference influence
    CALLREF_INFLUENCE = CLIHelper.validateDouble(cmd, "cri", 0., 1., CALLREF_INFLUENCE);
    // override scan range
    SCAN_RANGE = CLIHelper.validateDouble(cmd, "scr", 0., 1., SCAN_RANGE);
    // override benchmark flag
    LOG_BENCHMARKS = cmd.hasOption("bench") || LOG_BENCHMARKS;
    // read the threshold expressions, if they have been defined
    BITSET_THRESHOLD = cmd.getOptionValue("bsth", BITSET_THRESHOLD);
    DETECT_THRESHOLD = cmd.getOptionValue("dtth", DETECT_THRESHOLD);
    // warm-up expression cache
    Utils.getBitSetThreshold(0, 0);
    Utils.getDetectionThreshold(0);

    // get output directory
    Path apkPath = FS.getPath(cmd.getOptionValue("apk")).toAbsolutePath();
    Path outputDirectory;
    if (cmd.hasOption("out")) {
      outputDirectory =
          (Files.isDirectory(apkPath) ? apkPath : apkPath.getParent())
              .resolve(FS.getPath(cmd.getOptionValue("out")));
      if (Files.notExists(outputDirectory)) {
        try {
          Files.createDirectories(outputDirectory);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    } else {
      outputDirectory = null;
    }
    if (Files.isRegularFile(apkPath)) {
      Path outputFile = null;
      if (outputDirectory != null) {
        outputFile =
            outputDirectory.resolve(
                apkPath
                    .getFileName()
                    .toString()
                    .replaceFirst("\\.[a-zA-Z]{3}$", ".k" + K + ".json"));
      }
      // analyze the provided APK
      analyze(apkPath, outputFile);
    } else if (Files.isDirectory(apkPath)) {
      Try.run(
              () ->
                  Files.list(apkPath)
                      .filter(
                          f ->
                              Files.isRegularFile(f)
                                  && f.getFileName().toString().matches(".*\\.apk"))
                      .forEach(
                          f -> {
                            // don't analyze further applications if shutdown has been requested
                            if (!CLI.keepRunning()) {
                              return;
                            }
                            Path outputFile = null;
                            if (outputDirectory != null) {
                              outputFile =
                                  outputDirectory.resolve(
                                      f.getFileName()
                                          .toString()
                                          .replaceFirst("\\.[a-zA-Z]{3}$", ".k" + K + ".json"));
                              try {
                                Files.createFile(outputFile);
                              } catch (FileAlreadyExistsException aee) {
                                if (!cmd.hasOption("f")) {
                                  Utils.syncPrint(
                                      threadId,
                                      "Skipped "
                                          + f.getFileName()
                                          + " because output file already exists.");
                                  return;
                                }
                              } catch (IOException ioe) {
                                return;
                              }
                            }
                            // analyze the provided APK
                            while (true) {
                              try {
                                analyze(f, outputFile);
                                break;
                              } catch (RuntimeException e) {
                                // catch random Soot bullshit exception and restart analysis
                                if (!"unnumbered".equals(e.getMessage())) {
                                  e.printStackTrace();
                                  break;
                                }
                              }
                            }
                          }))
          .onFailure(Throwable::printStackTrace);
    }
  }

  private void analyze(Path appPath, Path outputFile) {
    // check if APK path is valid
    if (Files.notExists(appPath)) {
      throw new RuntimeException("Error: No bin/src directory found under " + appPath);
    }

    List<ClassData> appClasses;
    LongHolder tsAll = new LongHolder();
    synchronized (soot.G.class) {
      Utils.syncPrint(threadId, "Soot processing...");
      tsAll.setCurrentTimeMillis();
      // call configuration functions
      configure(appPath);
      // prepare necessary classes and run packs
      Scene.v().loadNecessaryClasses();
      PackManager.v().runPacks();
      // benchmark Soot time
      Utils.syncPrint(threadId, Utils.benchmark(tsAll, "Soot"));
      Utils.syncPrint();
      Utils.syncPrint(threadId, "ANALYZE " + appPath + "\n");
      // benchmark full analysis time
      tsAll.setCurrentTimeMillis();
      // create List of application classes
      appClasses =
          Scene.v()
              .getApplicationClasses()
              .stream()
              .map(ClassData::new)
              .collect(CustomCollectors.toCompactList());
      // free memory occupied by Soot
      soot.G.reset();
    }

    // analyze app package structure
    PkgNode appDefaultPkg = new PkgNode();
    appClasses.forEach(appDefaultPkg::attach);
    Utils.syncPrint(appDefaultPkg.stringify());
    Utils.syncPrint();

    // load a synchronized copy of the library map (contains all relevant libraries and respective
    // paths)
    final Map<String, String> libraryMap =
        Collections.synchronizedMap(new HashMap<>(IOHelper.getLibraryMap()));
    // remove excluded libraries
    libraryMap.keySet().removeIf(name -> EXCLUDE_LIBS.matcher(name).matches());

    // app class indices lookup map
    Map<ClassData, Integer> appClassIndices = new HashMap<>();
    IndexedList.of(appClasses).forEach((i, ac) -> appClassIndices.put(ac, i));
    // stores result output for all matched libraries
    List<List<String>> resultLogStore = new ArrayList<>();
    // store result objects for JSON output
    List<DetectionResult> detectionResultList = new ArrayList<>();
    do {
      // libraries above threshold that were detected in this round
      TreeMap<Tuple2<Double, Double>, List<Tuple4<String, String, List<String>, Set<ClassData>>>>
          results = new TreeMap<>();
      finCount.set(0);
      // reorder libraries to start off with the best ones (according to bit hashes)
      TreeMap<Double, List<Map.Entry<String, String>>> scanCandidates =
          getCandidates(appClasses, libraryMap);
      // stop here if there are no libraries left to check
      if (scanCandidates.isEmpty()) {
        break;
      }
      // some precautions to prevent infinite loops
      double lastKey = scanCandidates.lastKey();
      double lowerKey = 0.;
      if (scanCandidates.lowerKey(lastKey) != null) {
        lowerKey = scanCandidates.lowerKey(lastKey);
      }
      // determine candidates to be scanned
      Collection<List<Map.Entry<String, String>>> selectedCandidates =
          scanCandidates.descendingMap().headMap(min(lastKey - SCAN_RANGE, lowerKey)).values();
      // calculate number of libraries processed in this round
      int jobSize = selectedCandidates.stream().mapToInt(List::size).sum();
      selectedCandidates
          .parallelStream()
          .forEach(
              scl ->
                  scl.parallelStream()
                      .forEach(
                          libEntry -> {
                            String name = libEntry.getKey();
                            String dataPath = libEntry.getValue();
                            Tuple2<
                                    Tuple2<Double, Double>,
                                    Tuple4<String, String, List<String>, Set<ClassData>>>
                                result =
                                    scanForLib(
                                        name, dataPath, appClasses, appClassIndices, jobSize);
                            if (result != null) {
                              // if library passed the scan, add the result to the result map
                              synchronized (results) {
                                results
                                    .computeIfAbsent(result._1, k -> new LinkedList<>())
                                    .add(result._2);
                              }
                            } else {
                              // remove libraries with a score too bad to be checked again
                              libraryMap.remove(name);
                            }
                          }));
      // process the results of this scan round
      if (!results.isEmpty()) {
        List<String> alternatives = new ArrayList<>();
        alternatives.add("ALTERNATIVE MATCHES:");
        // remember all classes of processed libraries here
        Set<ClassData> seenClasses = new HashSet<>();
        // iterate over all results
        results
            .descendingMap()
            .entrySet()
            .forEach(
                re ->
                    re.getValue()
                        .forEach(
                            library -> {
                              // check if any of the classes was contained in another (already seen)
                              // library
                              if (library._4.stream().anyMatch(seenClasses::contains)) {
                                // append information about alternative to the output
                                alternatives.add(library._2);
                              } else {
                                // analyse code distribution in package hierarchy
                                PkgNode defaultPkg = new PkgNode();
                                library._4.forEach(defaultPkg::attach);
                                // add the results of the class analysis to the result log
                                library._3.add(defaultPkg.stringify());
                                // save this final result for later (JSON) output
                                detectionResultList.add(
                                    new DetectionResult(
                                        library._1, re.getKey()._1, re.getKey()._2, defaultPkg));
                                // adjust the error counters for the accepted library
                                if ((VERBOSE & 1) != 0) {
                                  Tuple3<Integer, Integer, Integer> errorCounts =
                                      errorCounters.get(library._1);
                                  wrongAssign.addAndGet(errorCounts._1);
                                  wrongDetect.addAndGet(errorCounts._2);
                                  unmatchedCount.addAndGet(errorCounts._3);
                                }
                                // store the result in the results log
                                resultLogStore.add(library._3);
                                // remove all detected application classes from the library
                                appClasses.removeAll(library._4);
                              }
                              // mark all classes as seen
                              seenClasses.addAll(library._4);
                            }));
        // rebuild appClass index lookup
        appClassIndices.clear();
        IndexedList.of(appClasses).forEach((i, ac) -> appClassIndices.put(ac, i));
        // add alternatives to logStore if there is at least one
        if (alternatives.size() > 1) {
          alternatives.add("\n");
          resultLogStore.add(alternatives);
        }
      }
      Utils.syncPrint(threadId, "--------------------------------------------");
    } while (libraryMap.size() > 0);
    // write JSON output
    if (outputFile != null) {
      try (Writer w = Files.newBufferedWriter(outputFile, Charset.forName("UTF-8"))) {
        Utils.getGsonPretty().toJson(detectionResultList, w);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    Utils.syncPrint();
    resultLogStore.forEach(item -> item.forEach(Utils::syncPrint));
    Utils.syncPrint(threadId, Utils.benchmark(tsAll, "Scan"));
    if ((VERBOSE & 1) != 0) {
      // add mismatch/detection/unmatched counters
      Utils.syncPrint(
          threadId,
          wrongAssign.getAndSet(0)
              + " wrong class assignments, "
              + wrongDetect.getAndSet(0)
              + " wrong class detections and "
              + unmatchedCount.getAndSet(0)
              + " unmatched classes");
    }
  }

  /**
   * This method executes the BitSet filter and returns an ordered map of libraries which might be
   * detected among the available app classes. This method removes elements from the libraryMap
   * parameter which fail the filter.
   *
   * @param appClasses The ClassData objects representing the available application classes
   * @param libraryMap The map of library names and corresponding paths to load their signatures
   *     from
   * @return An ordered Map of all libraries that passed the filter.
   */
  private TreeMap<Double, List<Map.Entry<String, String>>> getCandidates(
      @Nonnull List<ClassData> appClasses, @Nonnull Map<String, String> libraryMap) {
    // make a BitSet for all K-grams that currently exist in all the (remaining) application methods
    Set<Integer> appHashes = new HashSet<>();
    appClasses.forEach(ac -> ac.forEach(am -> appHashes.addAll(am.getKGramHashes())));
    BitSet appBitSet = Utils.makeBitSet(appHashes, M_PRIME);
    double appBitSetPop = (double) appBitSet.cardinality() / (double) M_PRIME;
    Utils.syncPrint(threadId, Utils.toPercent(appBitSetPop) + " of app BitSet populated");
    // free the RAM occupied by the BitSet
    appHashes.clear();
    finCount.set(0);
    // reorder libraries to start off with the best ones (according to bit hashes)
    TreeMap<Double, List<Map.Entry<String, String>>> scanCandidates =
        libraryMap
            .entrySet()
            .parallelStream()
            .collect(
                Collectors.toMap(
                    e -> {
                      try {
                        BitSetWrapper libBitSet = IOHelper.BITSET_CACHE.get(e.getValue());
                        BitSet andBitSet = (BitSet) appBitSet.clone();
                        andBitSet.and(libBitSet.bitSet);
                        double libBits = libBitSet.popCount;
                        double bitScore = andBitSet.cardinality() / libBits;
                        double bitSetThreshold = Utils.getBitSetThreshold(libBits, appBitSetPop);
                        if (bitScore < bitSetThreshold || Double.isNaN(bitScore)) {
                          Utils.syncPrint(
                              threadId,
                              "Skip library "
                                  + e.getKey()
                                  + " because of BitSet test: "
                                  + bitScore
                                  + " < "
                                  + bitSetThreshold);
                          // map unaccepted values to NaN, so they will be removed
                          return Double.NaN;
                        } else {
                          Utils.syncPrint(
                              threadId,
                              "Library " + e.getKey() + " accepted, BitSet match: " + bitScore);
                        }
                        return bitScore;
                      } catch (Exception x) {
                        System.err.println("Could not read BitSet for " + e.getKey());
                        x.printStackTrace();
                        return Double.NaN;
                      }
                    },
                    Arrays::asList,
                    (u, v) -> {
                      List<Map.Entry<String, String>> list = new ArrayList<>();
                      list.addAll(u);
                      list.addAll(v);
                      return list;
                    },
                    TreeMap::new));
    // handle library entries marked as NaN (0/0 divisions, errors, or excluded elements)
    if (scanCandidates.containsKey(Double.NaN)) {
      // remove libraries marked as NaN
      scanCandidates.get(Double.NaN).forEach(libEntry -> libraryMap.remove(libEntry.getKey()));
      // remove the NaN key
      scanCandidates.remove(Double.NaN);
    }
    return scanCandidates;
  }

  /**
   * This method scans ClassData objects of a given app, looking for a specified library.
   *
   * @param name The name (and version) of the library to analyse
   * @param dataPath The path to the compressed JSON file holding the library signature
   * @param appClasses The ClassData objects representing the available app classes
   * @param appClassIndices A lookup map which maps ClassData objects to their index in the class
   *     list
   * @param jobSize The total number of libraries in process in this round for progress output
   * @return A result tuple, containing the numeric detection result (), name, lastRoundResult,
   *     eventLog, cm.getMatchedColumns()
   */
  private Tuple2<Tuple2<Double, Double>, Tuple4<String, String, List<String>, Set<ClassData>>>
      scanForLib(
          @Nonnull String name,
          @Nonnull String dataPath,
          @Nonnull List<ClassData> appClasses,
          @Nonnull Map<ClassData, Integer> appClassIndices,
          int jobSize) {
    Tuple2<Tuple2<Double, Double>, Tuple4<String, String, List<String>, Set<ClassData>>> result =
        null;
    List<String> eventLog = new ArrayList<>();
    LongHolder ts = LongHolder.currentTimeMillis();
    LongHolder libTs = LongHolder.currentTimeMillis();
    Set<String> appClassNames = new HashSet<>();
    if ((VERBOSE & 3) != 0) {
      appClasses.forEach(c -> appClassNames.add(c.name));
    }

    Utils.syncPrint(threadId, "Process " + name + "...");

    List<ClassData> libClasses;
    try {
      libClasses = IOHelper.getClassData(dataPath);
    } catch (Exception x) {
      System.err.println("Could not read JSON file for " + name);
      x.printStackTrace();
      finCount.incrementAndGet();
      return null;
    }

    // resolve map for index of class by name
    Map<ClassData, Integer> libClassIndices = new HashMap<>();
    IndexedList.of(libClasses).forEach((i, cd) -> libClassIndices.put(cd, i));
    long libWeight = libClasses.stream().mapToLong(c -> c.weight).sum();

    // initial class matching
    ts.setCurrentTimeMillis();
    MatchingWrapper<ClassData, ClassData> cm =
        new MatchingWrapper<>(
            libClasses,
            appClasses,
            (lc, ac) -> new ApproxMethodMatching(lc, ac).getClassSimilarity());
    // final reference to the first matching for maintaining perfect matchings
    final MatchingWrapper<ClassData, ClassData> firstCm = cm;
    if (LOG_BENCHMARKS) {
      eventLog.add(Utils.benchmark(ts, "Initial Similarity Calculation"));
    }

    List<Integer> matching;
    PollMap<String> typeMap = new PollMap<>();
    DoubleHolder sum = new DoubleHolder(Double.POSITIVE_INFINITY);
    IntHolder c = new IntHolder();
    LongHolder cw = new LongHolder();
    Set<List<Integer>> lastMatches = new HashSet<>();
    String lastRoundResult = null;
    double matchScore = Double.NaN;
    double coverageScore = Double.NaN;
    double adaptiveThreshold = Utils.getDetectionThreshold(libWeight);
    // counters for wrong detections and assignments in the current iteration
    IntHolder wrongAssignRound = new IntHolder(0);
    IntHolder wrongDetectRound = new IntHolder(0);
    for (int r = 0; ; r++) {
      // (re)do class matching
      ts.setCurrentTimeMillis();
      matching = cm.getMatching();
      if (LOG_BENCHMARKS) {
        eventLog.add(Utils.benchmark(ts, "Matching"));
      }
      // check if the matching has stabilized
      if (lastMatches.contains(matching)) {
        break;
      } else {
        lastMatches.add(matching);
      }
      // do some resets
      sum.set(0.);
      c.set(0);
      cw.set(0L);
      typeMap.clear();
      if ((VERBOSE & 1) != 0) {
        wrongAssignRound.set(0);
        wrongDetectRound.set(0);
      }

      ts.setCurrentTimeMillis();
      // map of optimal method matchings of matched classes
      Map<String, MethodMatching> matchMap = new LinkedHashMap<>();
      cm.forEach((lc, ac) -> matchMap.put(lc.name, new MethodMatching(lc, ac)));
      // notify the matchings about likely method correlations derived from other matchings
      matchMap.values().forEach(mm -> mm.processMethodCorrelations(matchMap));
      if (LOG_BENCHMARKS) {
        eventLog.add(Utils.benchmark(ts, "Method Correlations"));
      }
      // process statistics and improved type mappings from current mapping
      matchMap
          .values()
          .forEach(
              mm -> {
                ClassData lc = mm.libClass;
                ClassData ac = mm.appClass;
                double sim = mm.getClassSimilarity();
                if ((VERBOSE & 1) != 0 && !lc.name.equals(ac.name)) {
                  StringBuilder sb = Utils.getBuilder();
                  if (appClassNames.contains(lc.name)) {
                    wrongAssignRound.inc();
                    if (sim == 1. || lc.isEmpty() && ac.isEmpty()) {
                      sb.append("~ ");
                    } else {
                      sb.append("# ");
                    }
                    sb.append("WRONG ASSIGNMENT: ");
                  } else {
                    wrongDetectRound.inc();
                    if (sim == 1. || lc.isEmpty() && ac.isEmpty()) {
                      sb.append("~ ");
                    } else {
                      sb.append("X ");
                    }
                    sb.append("WRONG DETECTION: ");
                  }
                  sb.append(lc.name);
                  sb.append(" (");
                  sb.append(lc.size());
                  sb.append(") >>> ");
                  sb.append(ac.name);
                  sb.append(" (");
                  sb.append(ac.size());
                  sb.append(", ");
                  sb.append(Utils.toPercent(sim));
                  sb.append(")");
                  eventLog.add(Utils.freeBuilder(sb));
                  if ((VERBOSE & 4) != 0) {
                    mm.getMaxMapping().forEach(maxM -> eventLog.add("> " + maxM.toString()));
                  }
                }
                // update the methods matching using the correlation statistics (don't touch
                // similarity, though)
                mm.rematchWithInvocationStats();

                // update the type mapping probabilities for the next iteration
                // class hierarchy based type mappings
                if (lc.appSuperClass != null
                    && ac.appSuperClass != null
                    && sim >= MAPPING_THRESHOLD) {
                  double score = lc.weight * sim;
                  if (score > 0.) {
                    typeMap.update(lc.appSuperClass, ac.appSuperClass, score, lc.weight);
                  }
                }
                if (!lc.interfaces.isEmpty() && !ac.interfaces.isEmpty()) {
                  double score = lc.weight * sim / (lc.interfaces.size() * ac.interfaces.size());
                  lc.interfaces.forEach(
                      lci ->
                          ac.interfaces.forEach(aci -> typeMap.update(lci, aci, score, lc.weight)));
                }
                // method content based type mappings
                mm.addAssumedTypeMappings(typeMap, ac.name);

                // collect statistics
                sum.add(sim * lc.weight);
                cw.add(lc.weight);
                c.inc();
              });
      if (LOG_BENCHMARKS) {
        eventLog.add(Utils.benchmark(ts, "Rematch & Type Mappings"));
      }

      // calculate scores
      matchScore = sum.get() / cw.get();
      coverageScore = (double) cw.get() / (double) libWeight;
      // stop analysis if results get too bad
      if (Double.isNaN(matchScore) || matchScore * coverageScore < adaptiveThreshold) {
        break;
      }

      // results output to console
      StringBuilder sb = Utils.getBuilder();
      if (r == 0) {
        sb.append("CONTENT (INITIAL) SCORE (");
      } else {
        sb.append("TYPE-GRAPH SCORE ITERATION # ");
        sb.append(r);
        sb.append(" (");
      }
      sb.append(name);
      sb.append("): ");
      sb.append(Utils.toPercent(matchScore));
      sb.append(" match on ");
      sb.append(Utils.toPercent(coverageScore));
      sb.append(" of detected library classes' code units");
      lastRoundResult = Utils.freeBuilder(sb);
      eventLog.add(lastRoundResult);

      // create new matching based on type mappings
      cm =
          new MatchingWrapper<>(
              libClasses,
              appClasses,
              (lc, ac) -> {
                if (ac.size() > lc.size() && !ALLOW_ADDITIONAL_APP_CLASS_METHODS) {
                  return 0.;
                }
                double mapVal = typeMap.get(lc.name, ac.name);
                if (firstCm.getWeight(libClassIndices.get(lc), appClassIndices.get(ac)) == 1.) {
                  return max(mapVal, 1.e-6);
                } else {
                  return mapVal;
                }
              });
    }
    if (!Double.isNaN(matchScore) && matchScore * coverageScore >= adaptiveThreshold) {
      // return found candidate library
      result =
          Tuple.of(
              Tuple.of(matchScore, coverageScore),
              Tuple.of(name, lastRoundResult, eventLog, cm.getMatchedColumns()));
      // debug stuff
      if ((VERBOSE & 2) != 0) {
        eventLog.add("");
        // print output about matched classes
        cm.forEach(
            (lc, ac, sim) -> {
              MethodMatching mm = new MethodMatching(ac, lc);
              eventLog.add(
                  lc.name
                      + " >>> "
                      + ac.name
                      + " ("
                      + lc.weight
                      + " units, "
                      + Utils.toPercent(mm.getClassSimilarity())
                      + " similarity)");
              if ((VERBOSE & 4) != 0) {
                mm.getMaxMapping().forEach(maxM -> eventLog.add("> " + maxM.toString()));
              }
            });
      }
      if ((VERBOSE & 1) != 0) {
        eventLog.add("");
        // print output about unmatched classes
        IntHolder unmatchedRound = new IntHolder(0);
        cm.getUnmatched()
            .forEach(
                lc -> {
                  if (appClassNames.contains(lc.name)) {
                    unmatchedRound.inc();
                    eventLog.add(
                        "# CLASS " + lc.name + " (" + lc.size() + ") exists but remains unmapped");
                    if ((VERBOSE & 4) != 0) {
                      lc.forEach(
                          me ->
                              eventLog.add(
                                  "# > " + me.getFullName() + " (" + me.weight + " instr.)"));
                    }
                  }
                });
        errorCounters.put(
            name, Tuple.of(wrongAssignRound.get(), wrongDetectRound.get(), unmatchedRound.get()));
      }
    }
    Utils.syncPrint(
        threadId,
        "Finished processing "
            + name
            + " (Score: "
            + (matchScore * coverageScore)
            + " of "
            + adaptiveThreshold
            + ", "
            + Utils.benchmark(libTs)
            + ", "
            + finCount.incrementAndGet()
            + "/"
            + jobSize
            + ")");
    return result;
  }

  private static void configure(Path analyzeDir) {
    soot.G.reset();
    G.v().out =
        new PrintStream(
            new OutputStream() {
              @Override
              public void write(int b) throws IOException {
                // This is just a dummy to suppress debug output
              }
            },
            true);
    soot.options.Options v = soot.options.Options.v();
    v.set_allow_phantom_refs(true);
    v.set_output_format(soot.options.Options.output_format_none);
    v.set_soot_classpath(ANDROID_JAR.toString());
    // target directory and all library jars provide application classes, use only target directory
    // on error
    v.set_process_dir(Collections.singletonList(analyzeDir.toString()));
    v.set_src_prec(soot.options.Options.src_prec_apk_class_jimple);
    soot.Main.v().autoSetOptions();
  }
}
