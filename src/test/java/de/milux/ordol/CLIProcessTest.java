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

import de.milux.ordol.helpers.IOHelper;
import de.milux.ordol.helpers.IntHolder;
import de.milux.ordol.helpers.Utils;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.junit.After;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static de.milux.ordol.Constants.ANDROID_PLATFORMS;
import static de.milux.ordol.Constants.LIBS_DIRECTORY;
import static de.milux.ordol.LibraryMapper.LIBS_ARCHIVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class CLIProcessTest {

  private int deleteJsonAndApk(Path dir) {
    IntHolder intHolder = new IntHolder(0);
    IOHelper.visitAllFiles(
        dir,
        f -> {
          if (f.getFileName().toString().endsWith(".json.zlib")) {
            Try.run(() -> Files.delete(f))
                .onSuccess(x -> intHolder.inc())
                .onFailure(
                    e -> {
                      throw new RuntimeException(e);
                    });
          }
          if (f.getFileName().toString().endsWith(".apk")) {
            Utils.exToRtEx(() -> Files.delete(f));
          }
        });
    return intHolder.get();
  }

  private int countJson(Path dir) {
    IntHolder intHolder = new IntHolder(0);
    IOHelper.visitAllFiles(
        dir,
        f -> {
          if (f.getFileName().toString().endsWith(".json.zlib")) {
            intHolder.inc();
          }
        });
    return intHolder.get();
  }

  /** Removes the shutdown hook which will be activated when calling CLI.main() */
  @After
  public void removeShutdownHook() {
    CLI.removeShutdownHook();
  }

  @Test
  public void processSourceAndJar() {
    Path directory = LIBS_DIRECTORY.resolve("ActionBarSherlock");
    Path absPath = LIBS_ARCHIVE.resolve("ActionBarSherlock");
    Path abs4Path = absPath.resolve("ABS4");
    Path android13 =
        ANDROID_PLATFORMS.resolve("android-13").resolve("android.jar").toAbsolutePath();
    // delete all compiled stuff in ABS v. 2/3 folders
    Utils.exToRtEx(
        () ->
            Files.list(absPath)
                .filter(
                    i ->
                        Files.isDirectory(i)
                            && i.getFileName().toString().matches(".*-[2-3][0-9.]+"))
                .forEach(
                    d -> {
                      Path binDir = d.resolve("bin");
                      if (Files.exists(binDir)) {
                        Utils.exToRtEx(() -> IOHelper.deleteRecursive(binDir));
                      }
                    }));
    int deleted = deleteJsonAndApk(directory);
    assertNotEquals(0, deleted);
    CLI.main(
        List.of(
                "process",
                "-m",
                "SOURCE",
                "-ld",
                absPath.toString(),
                "-jcp",
                "C:/Program Files/Java/jdk1.7.0_80/bin/javac.exe",
                "-n",
                "ActionBarSherlock",
                "-p",
                ".*-([2-3][0-9.]+)",
                "-vcg",
                "1",
                "-sp",
                "library/src;actionbarsherlock/src;src",
                "-cp",
                "libs/android-support-v4.jar;" + android13.toString(),
                "-al",
                android13.toString())
            .toJavaArray(String.class));
    // ActionBarSherlock 4.x
    CLI.main(
        ("process -m JAR -ld \""
                + abs4Path
                + "\" -n ActionBarSherlock "
                + "-p \".*actionbarsherlock-(4[0-9.]+)\\.jar\" -vcg 1")
            .split(" "));
    int created = countJson(directory);
    assertEquals(deleted, created);
  }

  @Test
  public void processAars() {
    Path directory = LIBS_DIRECTORY.resolve("crashlytics");
    Path libPath = LIBS_ARCHIVE.resolve("crashlytics");
    int deleted = deleteJsonAndApk(directory);
    assertNotEquals(0, deleted);
    CLI.main(
        ("process -m AAR -ld \""
                + libPath
                + "\" -n crashlytics "
                + "-p \".*-([0-9.]+)\\.aar\" -vcg 1")
            .split(" "));
    int created = countJson(directory);
    assertEquals(deleted, created);
  }

  @Test
  public void processJars() {
    Path directory = LIBS_DIRECTORY.resolve("flurry");
    Path libPath = LIBS_ARCHIVE.resolve("flurry");
    int deleted = deleteJsonAndApk(directory);
    assertNotEquals(0, deleted);
    CLI.main(
        ("process -m JAR -ld \"" + libPath + "\" -n flurry " + "-p \".*-([0-9.]+)\\.jar\" -vcg 1")
            .split(" "));
    int created = countJson(directory);
    assertEquals(deleted, created);
  }
}
