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

import de.milux.ordol.data.ClassData;
import de.milux.ordol.helpers.CLIDispatcher;
import de.milux.ordol.helpers.ClassBuilder;
import de.milux.ordol.helpers.IOHelper;
import de.milux.ordol.helpers.Utils;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javaslang.collection.List;
import javaslang.control.Try;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import soot.G;
import soot.PackManager;
import soot.Scene;

import javax.annotation.Nonnull;

public class LibraryMapper implements CLIDispatcher {
  private enum Mode {
    SOURCE,
    JAR,
    AAR
  }

  public static final Path LIBS_ARCHIVE = Constants.FS.getPath("ordol_libs");
  private static final String REGEX_SEP = Pattern.quote(File.separator);

  private boolean hasException = false;
  private Set<String> libDirectories = new HashSet<>();

  @Override
  public Options getOptions() {
    String modeList =
        Arrays.stream(Mode.values()).map(Mode::toString).collect(Collectors.joining(", "));
    return new Options()
        .addOption(
            Option.builder("m")
                .longOpt("mode")
                .required()
                .hasArg()
                .argName("mode")
                .desc("The mode to process the library.\nCan be one of these: " + modeList)
                .build())
        .addOption(
            Option.builder("ld")
                .longOpt("lib-dir")
                .required()
                .hasArg()
                .argName("path")
                .desc("Absolute library directory path to search for java/class/jar/aar files")
                .build())
        .addOption(
            Option.builder("n")
                .longOpt("name")
                .hasArg()
                .argName("name")
                .desc(
                    "The static name used for the library (target directory), "
                        + "instead of name extraction from the regex pattern, see -ncg")
                .build())
        .addOption(
            Option.builder("p")
                .longOpt("pattern")
                .required()
                .hasArg()
                .argName("regex")
                .desc(
                    "Pattern used to find valid source files/directories, given as regular expression.\n"
                        + "Slashes (\"/\") will be replaced by the platform's separator (\"/\" or \"\\\").")
                .build())
        .addOption(
            Option.builder("al")
                .longOpt("android-lib")
                .hasArg()
                .argName("path")
                .desc(
                    "["
                        + Mode.SOURCE
                        + " only] Absolute path to the android.jar for aapt to build R class")
                .build())
        .addOption(
            Option.builder("sp")
                .longOpt("source-paths")
                .hasArg()
                .argName("paths")
                .desc(
                    "["
                        + Mode.SOURCE
                        + " only] Relative candidate paths for the source file "
                        + "(src) directory, separated by \""
                        + File.pathSeparator
                        + "\".\n"
                        + "First directory found is used as src directory.")
                .build())
        .addOption(
            Option.builder("cp")
                .longOpt("class-paths")
                .hasArg()
                .argName("paths")
                .desc(
                    "["
                        + Mode.SOURCE
                        + " only] Relative candidate paths for additional "
                        + "class/java file directories for javac, separated by \""
                        + File.pathSeparator
                        + "\".\nFirst directory found is used as bin directory.")
                .build())
        .addOption(
            Option.builder("ncg")
                .longOpt("name-capture-group")
                .hasArg()
                .argName("group")
                .type(Number.class)
                .desc(
                    "The regex capture group number to extract the library name from.\n"
                        + "Can be omitted if name is given explicitly via -n option")
                .build())
        .addOption(
            Option.builder("vcg")
                .longOpt("version-capture-group")
                .required()
                .hasArg()
                .type(Number.class)
                .argName("group")
                .desc("The regex capture group number to extract the library version from.")
                .build())
        .addOption(
            Option.builder("jcp")
                .longOpt("javac-path")
                .hasArg()
                .argName("path")
                .desc("The path of the javac executable to call for Java compilation.")
                .build());
  }

  @Override
  public void dispatch(CommandLine cmd, Options options)
      throws ParseException, IllegalArgumentException {
    Mode m = Mode.valueOf(cmd.getOptionValue("m"));
    Path libDir = Constants.FS.getPath(cmd.getOptionValue("ld"));
    // allowed to be null
    String name = cmd.getOptionValue("n");
    // throws a subclass of IllegalArgumentException if compilation not possible
    Pattern pattern;
    if (File.separator.equals("\\")) {
      pattern = Pattern.compile(cmd.getOptionValue("p").replaceAll("/", "\\\\\\\\"));
    } else {
      pattern = Pattern.compile(cmd.getOptionValue("p"));
    }
    // parse numerical values here, replace them with -1 if necessary
    Number ncgNum = (Number) cmd.getParsedOptionValue("ncg"),
        vcgNum = (Number) cmd.getParsedOptionValue("vcg");
    if (ncgNum == null && name == null) {
      throw new ParseException(
          "Must provide name \"-n\" or name capture group \"-ncg\", aborting.");
    }
    int ncg = ncgNum == null ? -1 : ncgNum.intValue(), vcg = vcgNum.intValue();
    // plausibility check of version capture group
    if (vcg < 1 || vcg > 2 || (ncgNum != null && (ncg < 1 || ncg > 2))) {
      throw new ParseException(
          "Version/name capture group (\"-vcg\"/\"-ncg\") must be in range [1;2].\n"
              + "Use non-capturing groups (?: ... ) in regex pattern to avoid unnecessary capture groups!");
    }
    // check if library directory is OK
    if (Files.notExists(libDir)) {
      throw new ParseException("Must provide a valid library directory \"-ld\"");
    }
    // call the processing method
    switch (m) {
      case SOURCE:
        if (cmd.hasOption("jcp")) {
          Constants.JAVAC_EXEC = cmd.getOptionValue("jcp");
        }
        // allowed to be null, which assumes aapt will not be used
        Path androidJar =
            cmd.hasOption("al") ? Constants.FS.getPath(cmd.getOptionValue("al")) : null;
        if (!cmd.hasOption("sp") || !cmd.hasOption("cp")) {
          throw new ParseException(
              "Must provide source path (\"-sp\") candidates "
                  + "and class path (\"-cp\") candidates!");
        }
        List<Path> srcPaths =
            List.ofAll(
                Arrays.stream(cmd.getOptionValue("sp").split(File.pathSeparator))
                    .map(Constants.FS::getPath)
                    .collect(Collectors.toList()));
        List<Path> classPaths =
            List.ofAll(
                Arrays.stream(cmd.getOptionValue("cp").split(File.pathSeparator))
                    .map(Constants.FS::getPath)
                    .collect(Collectors.toList()));
        whileException(
            () ->
                analyzeSourceFileDirs(
                    libDir, name, pattern, androidJar, srcPaths, classPaths, ncg, vcg));
        break;
      case JAR:
        whileException(() -> analyzeJars(libDir, name, pattern, ncg, vcg));
        break;
      case AAR:
        whileException(() -> analyzeAars(libDir, name, pattern, ncg, vcg));
        break;
    }
    indexDuplicates();
  }

  public void whileException(Runnable r) {
    do {
      // reset exception state
      hasException = false;
      // run procedure, may span concurrent tasks
      r.run();
      // wait for all tasks to finish
      Utils.waitForTasks();
    } while (hasException);
  }

  public void analyzeAll() {
    // gson
    analyzeSourceFileDirs(
        LIBS_ARCHIVE.resolve("gson"),
        "gson",
        Pattern.compile(".*?([0-9.]+(?:-beta)?)"),
        null,
        List.of(
            Constants.FS.getPath("src", "main", "java"),
            Constants.FS.getPath("gson", "src", "main", "java")),
        List.of(Constants.FS.getPath("src", "generated", "java")),
        -1,
        1);

    // ActionBarSherlock 2.x-3.x
    Path android13Jar =
        Constants.ANDROID_PLATFORMS.resolve(Constants.FS.getPath("android-13", "android.jar"));
    analyzeSourceFileDirs(
        LIBS_ARCHIVE.resolve("ActionBarSherlock"),
        "ActionBarSherlock",
        Pattern.compile(".*-([2-3][0-9.]+)"),
        android13Jar,
        List.of(
            Constants.FS.getPath("library", "src"),
            Constants.FS.getPath("actionbarsherlock", "src"),
            Constants.FS.getPath("src")),
        List.of(Constants.FS.getPath("libs", "android-support-v4.jar"), android13Jar),
        -1,
        1);
    // ActionBarSherlock 4.x
    analyzeJars(
        LIBS_ARCHIVE.resolve(Constants.FS.getPath("ActionBarSherlock", "ABS4")),
        "ActionBarSherlock",
        Pattern.compile(".*" + REGEX_SEP + "actionbarsherlock-(4[0-9.]+)\\.jar"),
        -1,
        1);

    // Android Support Library
    Path supportSrc = LIBS_ARCHIVE.resolve("AndroidSupport");
    // Path prefix forbidding to process "test" directory
    String supportPathQ = Pattern.quote(supportSrc.toString()) + REGEX_SEP + "(?!test).*";
    analyzeAars(
        supportSrc,
        "support-",
        Pattern.compile(
            supportPathQ + "[0-9.]+" + REGEX_SEP + "(?:support-)?([a-z0-9-]+)-([0-9.]+)\\.aar"),
        1,
        2);
    analyzeJars(
        supportSrc,
        "support-",
        Pattern.compile(
            supportPathQ + "[0-9.]+" + REGEX_SEP + "(?:support-)?([a-z0-9-]+)-([0-9.]+)\\.jar"),
        1,
        2);
    analyzeJars(
        LIBS_ARCHIVE.resolve("AndroidSupport_old"),
        null,
        Pattern.compile(
            ".*" + REGEX_SEP + "support_r([0-9.]+).*" + REGEX_SEP + "android-([0-9a-z-]+)\\.jar"),
        2,
        1);

    // Google Mobile Services
    analyzeAars(
        LIBS_ARCHIVE.resolve("GMS"),
        null,
        Pattern.compile(
            ".*"
                + REGEX_SEP
                + "([a-z-]+)"
                + REGEX_SEP
                + "([0-9.]+)"
                + REGEX_SEP
                + "[a-z0-9.-]+\\.aar"),
        1,
        2);

    // Apache HTTP core/client/mime
    analyzeJars(
        LIBS_ARCHIVE.resolve("apache-http"),
        null,
        Pattern.compile(".*" + REGEX_SEP + "([a-z]+)-([0-9.]+)\\.jar"),
        1,
        2);

    // facebook
    analyzeAars(
        LIBS_ARCHIVE.resolve("facebook"),
        "facebook-sdk",
        Pattern.compile(
            ".*"
                + REGEX_SEP
                + "facebook-android-sdk-([0-9.]+)"
                + REGEX_SEP
                + ".*"
                + REGEX_SEP
                + "facebook(?:-android-sdk|-release)?(?:-[0-9.]+)?\\.aar"),
        -1,
        1);
    analyzeAars(
        LIBS_ARCHIVE.resolve("facebook"),
        "facebook-AudienceNetwork",
        Pattern.compile(
            ".*"
                + REGEX_SEP
                + "[a-z-]+([0-9.]+)"
                + REGEX_SEP
                + ".*"
                + REGEX_SEP
                + "AudienceNetwork\\.aar"),
        -1,
        1);
    analyzeJars(
        LIBS_ARCHIVE.resolve("facebook"),
        "facebook-AudienceNetwork",
        Pattern.compile(
            ".*"
                + REGEX_SEP
                + "[a-z-]+([0-9.]+)"
                + REGEX_SEP
                + ".*"
                + REGEX_SEP
                + "AudienceNetwork\\.jar"),
        -1,
        1);

    // Flurry
    analyzeJars(
        LIBS_ARCHIVE.resolve("flurry"), "flurry", Pattern.compile(".*-([0-9.]+)\\.jar"), -1, 1);

    // twitter4j
    analyzeJars(
        LIBS_ARCHIVE.resolve("twitter4j"),
        null,
        Pattern.compile(
            ".*" + REGEX_SEP + "(twitter4j(?!-examples)(?:-[a-z0-9])*)-([0-9.]+)\\.jar"),
        1,
        2);
    // requirement to avoid false positives for twitter4j
    analyzeJars(LIBS_ARCHIVE.resolve("json"), "json", Pattern.compile(".*-([0-9]+)\\.jar"), -1, 1);

    // ZXing
    analyzeJars(
        LIBS_ARCHIVE.resolve("ZXing"),
        "zxing-",
        Pattern.compile(".*" + REGEX_SEP + "([a-z-]+)-([0-9.]+)\\.jar"),
        1,
        2);

    // okhttp
    analyzeJars(
        LIBS_ARCHIVE.resolve("okhttp"), "okhttp", Pattern.compile(".*-([0-9.]+)\\.jar"), -1, 1);

    // Jsoup
    analyzeJars(
        LIBS_ARCHIVE.resolve("jsoup"),
        "jsoup",
        Pattern.compile(".*" + REGEX_SEP + "jsoup-([0-9.]+)\\.jar"),
        -1,
        1);

    // ViewPagerIndicator
    analyzeSourceFileDirs(
        LIBS_ARCHIVE.resolve("ViewPagerIndicator"),
        "ViewPagerIndicator",
        Pattern.compile(".*-([0-9.]+)"),
        android13Jar,
        List.of(Constants.FS.getPath("library", "src")),
        List.of(Constants.FS.getPath("library", "libs", "android-support-v4.jar"), android13Jar),
        -1,
        1);

    // BoltsFramework
    analyzeJars(
        LIBS_ARCHIVE.resolve("bolts"),
        null,
        Pattern.compile(".*" + REGEX_SEP + "(bolts-[a-z]+)-([0-9.]+)\\.jar"),
        1,
        2);

    // Dagger
    analyzeJars(
        LIBS_ARCHIVE.resolve("dagger"),
        "dagger",
        Pattern.compile(".*" + REGEX_SEP + ".*-([0-9.]+)\\.jar"),
        -1,
        1);

    // Glide
    analyzeJars(
        LIBS_ARCHIVE.resolve("glide"),
        "glide",
        Pattern.compile(".*" + REGEX_SEP + ".*-([0-9.]+)\\.jar"),
        -1,
        1);

    // NineOldAndroids
    analyzeJars(
        LIBS_ARCHIVE.resolve("NineOldAndroids"),
        "NineOldAndroids",
        Pattern.compile(".*" + REGEX_SEP + "library-([0-9.]+)\\.jar"),
        -1,
        1);
    analyzeSourceFileDirs(
        LIBS_ARCHIVE.resolve("NineOldAndroids"),
        "NineOldAndroids",
        Pattern.compile(".*-([0-9.]+)"),
        Constants.ANDROID_JAR,
        List.of(Constants.FS.getPath("library", "src")),
        List.of(Constants.ANDROID_JAR),
        -1,
        1);

    // Crashlytics
    analyzeAars(
        LIBS_ARCHIVE.resolve("crashlytics"),
        "crashlytics",
        Pattern.compile(".*-([0-9.]+)\\.aar"),
        -1,
        1);

    // okio
    analyzeJars(LIBS_ARCHIVE.resolve("okio"), "okio", Pattern.compile(".*-([0-9.]+)\\.jar"), -1, 1);

    // Google Protocol Buffers
    analyzeJars(
        LIBS_ARCHIVE.resolve("protobuf-nano"),
        "protobuf-nano",
        Pattern.compile(".*-([0-9.]+)\\.jar"),
        -1,
        1);
    analyzeJars(
        LIBS_ARCHIVE.resolve("protobuf"), "protobuf", Pattern.compile(".*-([0-9.]+)\\.jar"), -1, 1);

    // Guava
    analyzeJars(
        LIBS_ARCHIVE.resolve("guava"), "guava", Pattern.compile(".*-r?([0-9.]+)\\.jar"), -1, 1);

    // Retrofit
    analyzeJars(
        LIBS_ARCHIVE.resolve("retrofit"), "retrofit", Pattern.compile(".*-([0-9.]+)\\.jar"), -1, 1);

    // Picasso
    analyzeJars(
        LIBS_ARCHIVE.resolve("picasso"), "picasso", Pattern.compile(".*-([0-9.]+)\\.jar"), -1, 1);

    // Chartboost
    analyzeJars(
        LIBS_ARCHIVE.resolve("Chartboost"),
        "Chartboost",
        Pattern.compile(
            ".*"
                + REGEX_SEP
                + "Chartboost(?:[_A-Za-z-]+)([0-9.]+)"
                + "(?:"
                + REGEX_SEP
                + "lib)?"
                + REGEX_SEP
                + "chartboost\\.jar"),
        -1,
        1);

    // Unity Ads
    analyzeJars(
        LIBS_ARCHIVE.resolve("unity-ads"),
        "unity-ads",
        Pattern.compile(".*-([0-9.]+)\\.jar"),
        -1,
        1);

    // AdColony
    analyzeJars(
        LIBS_ARCHIVE.resolve("adcolony"), "adcolony", Pattern.compile(".*-([0-9.]+)\\.jar"), -1, 1);

    // MoPub
    analyzeAars(
        LIBS_ARCHIVE.resolve("mopub"), "mopub", Pattern.compile(".*-([0-9.]+)\\.aar"), -1, 1);

    // InMobi
    analyzeJars(
        LIBS_ARCHIVE.resolve("InMobi"), "InMobi", Pattern.compile(".*-([0-9.]+)\\.jar"), -1, 1);

    /* LESS FREQUENTLY USED LIBRARIES */

    // Parse SDK
    analyzeAars(
        LIBS_ARCHIVE.resolve("parse"),
        "parse",
        Pattern.compile(".*" + REGEX_SEP + "parse-android-([0-9.]+)\\.aar"),
        -1,
        1);

    // james-Mime4j
    analyzeJars(
        LIBS_ARCHIVE.resolve("james-Mime4j"),
        null,
        Pattern.compile(
            ".*" + REGEX_SEP + "james-Mime4j" + REGEX_SEP + "([a-z4-]+)-([0-9.]+)\\.jar"),
        1,
        2);
  }

  private void analyzeSourceFileDirs(
      @Nonnull Path directory,
      String name,
      @Nonnull Pattern regex,
      @Nonnull Path androidJar,
      @Nonnull List<Path> srcPaths,
      @Nonnull List<Path> classPaths,
      int nameCaptureGroup,
      int versionCaptureGroup) {
    Utils.exToRtEx(
        () ->
            Files.list(directory)
                .forEach(
                    dir -> {
                      Matcher matcher = regex.matcher(dir.toString());
                      if (matcher.matches()) {
                        String nameWithVersion = name + " " + matcher.group(versionCaptureGroup);
                        Utils.submitTask(
                            () ->
                                Try.run(
                                        () ->
                                            analyze(
                                                () -> {
                                                  Path binPath = dir.resolve("bin");
                                                  if (!Files.exists(binPath)) {
                                                    Path srcPath =
                                                        IOHelper.firstExisting(dir, srcPaths, true);
                                                    if (srcPath != null) {
                                                      if (Files.exists(
                                                          srcPath.getParent().resolve("res"))) {
                                                        Utils.syncPrint(
                                                            "Folder \"res\" for "
                                                                + nameWithVersion
                                                                + " detected, try to execute aapt...");
                                                        ClassBuilder.androidPackaging(
                                                            srcPath, androidJar);
                                                      }
                                                      Utils.syncPrint("Compile " + nameWithVersion);
                                                      ClassBuilder.compileRecursively(
                                                          srcPath,
                                                          binPath,
                                                          IOHelper.allExisting(
                                                                  dir, classPaths, true)
                                                              .toJavaArray(Path.class));
                                                    }
                                                  }
                                                  return binPath;
                                                },
                                                (name == null
                                                        ? matcher.group(nameCaptureGroup)
                                                        : name)
                                                    + File.separator
                                                    + matcher.group(versionCaptureGroup)))
                                    .onFailure(
                                        t -> {
                                          t.printStackTrace();
                                          hasException = true;
                                        }));
                      }
                    }));
  }

  private void analyzeAars(
      @Nonnull Path directory,
      String nameOrPrefix,
      @Nonnull Pattern regex,
      int nameCaptureGroup,
      int versionCaptureGroup) {
    String nameSafe = nameOrPrefix == null ? "" : nameOrPrefix;
    IOHelper.visitAllFiles(
        directory,
        file -> {
          if (file.getFileName().toString().endsWith(".aar")) {
            Matcher matcher = regex.matcher(file.toString());
            if (matcher.matches()) {
              Utils.submitTask(
                  () ->
                      Try.run(
                              () ->
                                  analyze(
                                      () -> {
                                        try (ZipFile aarZip = new ZipFile(file.toFile())) {
                                          ZipEntry classesEntry = aarZip.getEntry("classes.jar");
                                          if (classesEntry != null) {
                                            // temporary jar file for classes.jar
                                            Path tmpJar = Files.createTempFile("classes", ".jar");
                                            // try to delete temporary jar file on system exit
                                            tmpJar.toFile().deleteOnExit();
                                            // copy the classes.jar to the temporary jar file
                                            try (InputStream eis =
                                                aarZip.getInputStream(classesEntry)) {
                                              // copy classes.jar from archive to temporary file
                                              Files.copy(
                                                  eis, tmpJar, StandardCopyOption.REPLACE_EXISTING);
                                            }
                                            return tmpJar;
                                          }
                                          return null;
                                        } catch (IOException e) {
                                          throw new RuntimeException(e);
                                        }
                                      },
                                      (nameSafe
                                              + (nameCaptureGroup >= 0
                                                  ? matcher.group(nameCaptureGroup)
                                                  : ""))
                                          + File.separator
                                          + matcher.group(versionCaptureGroup)))
                          .onFailure(
                              t -> {
                                t.printStackTrace();
                                hasException = true;
                              }));
            }
          }
        });
  }

  private void analyzeJars(
      @Nonnull Path directory,
      String nameOrPrefix,
      @Nonnull Pattern regex,
      int nameCaptureGroup,
      int versionCaptureGroup) {
    String nameSafe = nameOrPrefix == null ? "" : nameOrPrefix;
    IOHelper.visitAllFiles(
        directory,
        file -> {
          if (file.toString().endsWith(".jar")) {
            Try.run(
                    () -> {
                      Matcher matcher = regex.matcher(file.toString());
                      if (matcher.matches()) {
                        Utils.submitTask(
                            () ->
                                Try.run(
                                        () ->
                                            analyze(
                                                () -> file,
                                                (nameSafe
                                                        + (nameCaptureGroup >= 0
                                                            ? matcher.group(nameCaptureGroup)
                                                            : ""))
                                                    + File.separator
                                                    + matcher.group(versionCaptureGroup)))
                                    .onFailure(
                                        t -> {
                                          t.printStackTrace();
                                          hasException = true;
                                        }));
                      }
                    })
                .onFailure(Throwable::printStackTrace);
          }
        });
  }

  private void analyze(Supplier<Path> binPathSupplier, String name)
      throws IOException, InterruptedException {
    // check blacklist
    if (Constants.LIB_BLACKLIST.matcher(name.replaceAll(REGEX_SEP, " ")).matches()) {
      Utils.syncPrint(name + " skipped because of blacklist entry");
      return;
    }

    Path apkPath = Constants.LIBS_DIRECTORY.resolve(name + ".apk");
    Path jsonPath = Constants.LIBS_DIRECTORY.resolve(name + ".json.zlib");

    if (Files.notExists(apkPath)) {
      Path binPath = binPathSupplier.get();
      if (binPath == null) {
        Utils.syncPrint("Skip " + name + " because supplier could not provide classfile source.");
        return;
      }
      // check if classfile source is valid
      if (Files.notExists(binPath)) {
        throw new RuntimeException("Error: No classfile source found at " + binPath);
      }
      // make APK from library, try up to five times if necessary
      makeApk(binPath, apkPath);
      // if APK is rebuilt, remove the old JSON file, too
      Try.run(() -> Files.deleteIfExists(jsonPath));
    } else if (Files.exists(jsonPath) && Files.size(jsonPath) > 0) {
      Utils.syncPrint(name + " is already indexed");
      return;
    }

    if (Files.notExists(jsonPath) || Files.size(jsonPath) == 0) {
      java.util.List<ClassData> cdList;
      // soot stuff cannot be parallelized
      synchronized (soot.G.class) {
        // search for a directory with libraries and call configuration functions
        configure(apkPath /*, List.of("libs", "lib")
                        .map(d -> FS.getPath(libPath, d))
                        .find(Files::exists)
                        .getOrElse((Path) null)*/);

        // prepare necessary classes
        Utils.syncPrint("Soot processing: " + name);
        Scene.v().loadNecessaryClasses();
        PackManager.v().runPacks();

        // examine classes and create library map
        Utils.syncPrint("Create data structures: " + name);
        cdList =
            Scene.v()
                .getApplicationClasses()
                .stream()
                .map(ClassData::new)
                .collect(Collectors.toList());
      }
      Utils.syncPrint("Collect hash values: " + name);
      Set<Integer> hashes = new HashSet<>();
      cdList.forEach(cd -> cd.forEach(md -> hashes.addAll(md.getKGramHashes())));
      if (hashes.isEmpty()) {
        Utils.syncPrint("Skip " + name + " because it does not contain any code!");
        return;
      }
      Utils.syncPrint(
          "BitSet population for k="
              + Constants.K
              + " and library "
              + name
              + ": "
              + Utils.toPercent((double) hashes.size() / (double) Constants.M_PRIME));
      Path parent = jsonPath.getParent();
      Files.createDirectories(parent);
      if (Files.notExists(jsonPath) || Files.size(jsonPath) == 0) {
        try (Writer w =
            new OutputStreamWriter(
                new DeflaterOutputStream(
                    Files.newOutputStream(jsonPath), new Deflater(Deflater.BEST_COMPRESSION)),
                "UTF-8")) {
          Utils.getGson().toJson(cdList, w);
          this.libDirectories.add(parent.getFileName().toString());
        }
      }
      Utils.syncPrint("Finished processing " + name);
    }
  }

  private void makeApk(Path binPath, Path destPath) throws IOException, InterruptedException {
    final ProcessBuilder pb =
        Utils.getConsoleProcessBuilder(
            List.of(
                "dx",
                "--dex",
                "--output=\"" + destPath.toAbsolutePath() + "\"",
                "\"" + binPath.toAbsolutePath() + "\""));
    // create target directory for APK
    Files.createDirectories(destPath.getParent());
    // make the APK using dx --dex
    pb.inheritIO().start().waitFor();
    Utils.syncPrint("Packed library to APK: " + destPath);
  }

  public void indexDuplicates() {
    Pattern versionAndExt = Pattern.compile("(.*)\\.json\\.zlib$");
    libDirectories
        .stream()
        .map(Constants.LIBS_DIRECTORY::resolve)
        .forEach(
            d -> {
              Map<String, Set<String>> hashToFiles = new HashMap<>();
              Utils.exToRtEx(
                  () ->
                      Files.list(d)
                          .filter(Files::isRegularFile)
                          .forEach(
                              path -> {
                                Matcher fileMatcher =
                                    versionAndExt.matcher(path.getFileName().toString());
                                if (fileMatcher.matches()) {
                                  String name = fileMatcher.group(1);
                                  String hash = IOHelper.getSHA256(path);
                                  if (hashToFiles.containsKey(hash)) {
                                    hashToFiles.get(hash).add(name);
                                  } else {
                                    Set<String> list = new HashSet<>();
                                    list.add(name);
                                    hashToFiles.put(hash, list);
                                  }
                                }
                              }));
              try (Writer w =
                  Files.newBufferedWriter(d.resolve("index.json"), Charset.forName("UTF-8"))) {
                Utils.getGson().toJson(hashToFiles, w);
              } catch (IOException ioe) {
                throw new RuntimeException(ioe);
              }
            });
  }

  public static void configure(Path analyzePath /*, Path libDir*/) {
    soot.G.reset();
    G.v().out =
        new PrintStream(
            new OutputStream() {
              @Override
              public void write(int b) throws IOException {}
            },
            true);
    soot.options.Options v = soot.options.Options.v();
    v.set_allow_phantom_refs(true);
    v.set_output_format(soot.options.Options.output_format_none);
    v.set_soot_classpath(Constants.ANDROID_JAR.toString());
    v.set_process_dir(Collections.singletonList(analyzePath.toString()));
    v.set_src_prec(soot.options.Options.src_prec_apk_class_jimple);
    soot.Main.v().autoSetOptions();
  }
}
