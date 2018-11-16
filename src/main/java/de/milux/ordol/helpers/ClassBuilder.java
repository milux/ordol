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
package de.milux.ordol.helpers;

import io.vavr.collection.List;
import io.vavr.control.Try;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.stream.Collectors;

import static de.milux.ordol.Constants.JAVAC_EXEC;
import static de.milux.ordol.helpers.Utils.syncPrint;

public final class ClassBuilder {

  public static void androidPackaging(Path src, Path androidJar) {
      Path projRoot = src.getParent();
      Path res = projRoot.resolve("res");
      Path manifest = projRoot.resolve("AndroidManifest.xml");
    if (Files.exists(res) && Files.exists(manifest)) {
      Try.run(
              () -> {
                ProcessBuilder pb =
                    new ProcessBuilder(
                        Arrays.asList(
                            "aapt",
                            "package",
                            "-v",
                            "-f",
                            "-m",
                            "-S",
                            res.toAbsolutePath().toString(),
                            "-J",
                            src.toAbsolutePath().toString(),
                            "-M",
                            manifest.toAbsolutePath().toString(),
                            "-I",
                            androidJar.toAbsolutePath().toString()));
                pb.inheritIO().start().waitFor();
              })
          .onFailure(Throwable::printStackTrace);
    }
  }

  public static void compileRecursively(Path src, Path bin, Path... cp) {
    java.util.List<List<String>> failedCommands = new LinkedList<>();
    Utils.exToRtEx(
        () -> {
          Files.createDirectories(bin);
          String classPath =
              List.of(src.toAbsolutePath().toString(), bin.toAbsolutePath().toString())
                  .appendAll(Arrays.stream(cp).map(Path::toString).collect(Collectors.toList()))
                  .mkString(";");
          Set<Path> dirsWithJavaFiles = new HashSet<>();
          Files.walkFileTree(
              src,
              new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                  // if a java file is found, add directory to the compile list
                  if (file.getFileName().toString().endsWith(".java")) {
                    dirsWithJavaFiles.add(file.getParent());
                  }
                  return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException ioe) {
                  // if directory is in the set, then start compilation
                  if (dirsWithJavaFiles.remove(dir)) {
                    Utils.exToRtEx(
                        () -> {
                          List<String> command =
                              List.of(
                                  JAVAC_EXEC,
                                  "-d",
                                  bin.toAbsolutePath().toString(),
                                  "-cp",
                                  classPath,
                                  dir.toAbsolutePath().toString() + File.separator + "*.java");
                          ProcessBuilder pb = Utils.getConsoleProcessBuilder(command);
                          int exit = pb.inheritIO().start().waitFor();
                          if (exit != 0) {
                            failedCommands.add(command);
                          }
                        });
                  }
                  return FileVisitResult.CONTINUE;
                }
              });
          if (!failedCommands.isEmpty()) {
            syncPrint("Retry failed compilation calls...");
            failedCommands.forEach(
                command ->
                    Utils.exToRtEx(
                        () -> {
                          ProcessBuilder pb = Utils.getConsoleProcessBuilder(command);
                          pb.inheritIO().start().waitFor();
                        }));
          }
        });
  }
}
