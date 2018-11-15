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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.reflect.TypeToken;
import de.milux.ordol.Constants;
import de.milux.ordol.data.BitSetWrapper;
import de.milux.ordol.data.ClassData;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javaslang.control.Try;

import javax.annotation.Nonnull;
import javax.xml.bind.DatatypeConverter;

public final class IOHelper {
  public static final Type CLASSDATA_LIST_TYPE = new TypeToken<ArrayList<ClassData>>() {}.getType();
  /** A cache for BitSet objects, using soft values (i.e. SoftReference gc semantics) */
  public static final LoadingCache<String, BitSetWrapper> BITSET_CACHE =
      CacheBuilder.newBuilder()
          .softValues()
          .build(
              new CacheLoader<String, BitSetWrapper>() {
                @Override
                public BitSetWrapper load(String libraryPath) throws Exception {
                  Path bitSetPath =
                      Constants.BITSET_CACHE_PATH.resolve(
                          Constants.LIBS_DIRECTORY.relativize(
                              Constants.FS.getPath(libraryPath + ".bs" + Constants.K + ".zlib")));
                  if (Files.exists(bitSetPath)) {
                    byte[] compressedBytes;
                    byte[] buffer = new byte[(Constants.M_PRIME / 64 + 1) * 8];
                    compressedBytes = Files.readAllBytes(bitSetPath);
                    Inflater inflater = new Inflater();
                    inflater.setInput(compressedBytes);
                    int inflated = inflater.inflate(buffer);
                    return new BitSetWrapper(BitSet.valueOf(ByteBuffer.wrap(buffer, 0, inflated)));
                  } else {
                    List<ClassData> cdList = getClassData(libraryPath);
                    Set<Integer> hashes = new HashSet<>();
                    cdList.forEach(cd -> cd.forEach(md -> hashes.addAll(md.getKGramHashes())));
                    Files.createDirectories(bitSetPath.getParent());
                    BitSet bitSet = Utils.makeBitSet(hashes, Constants.M_PRIME);
                    Deflater deflater = new Deflater(Deflater.BEST_SPEED);
                    deflater.setStrategy(Deflater.FILTERED);
                    try (OutputStream w =
                        new DeflaterOutputStream(Files.newOutputStream(bitSetPath), deflater)) {
                      w.write(bitSet.toByteArray());
                    }
                    return new BitSetWrapper(bitSet);
                  }
                }
              });

  private static final LoadingCache<Path, byte[]> libDataCache =
      CacheBuilder.newBuilder()
          .softValues()
          .build(
              new CacheLoader<Path, byte[]>() {
                @Override
                public byte[] load(Path libraryPath) throws Exception {
                  return Files.readAllBytes(libraryPath);
                }
              });
  private static Map<String, String> libraryMap = null;
  private static final Object libraryMapMonitor = new Object();

  private static void initLibraryMap() {
    Map<String, String> libMap = new LinkedHashMap<>();
    try {
      Files.walkFileTree(
          Constants.LIBS_DIRECTORY,
          new SimpleFileVisitor<Path>() {
            Map<String, String> used = Collections.emptyMap();

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
              if (Files.exists(dir.resolve("index.json"))) {
                try (Reader r =
                    Files.newBufferedReader(dir.resolve("index.json"), Charset.forName("UTF-8"))) {
                  Map<String, Set<String>> initMap =
                      Utils.getGson()
                          .fromJson(r, new TypeToken<Map<String, Set<String>>>() {}.getType());
                  used =
                      initMap
                          .values()
                          .stream()
                          .map(set -> set.stream().sorted().toArray(String[]::new))
                          .collect(
                              Collectors.toMap(
                                  a -> a[0],
                                  a -> {
                                    if (a.length == 1) {
                                      return a[0];
                                    } else {
                                      return String.join(", ", (CharSequence[]) a);
                                    }
                                  }));
                }
              } else {
                used = Collections.emptyMap();
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              final String ext = ".json.zlib";
              if (!used.isEmpty()) {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(ext)) {
                  String fileNameWoExt = fileName.substring(0, fileName.length() - ext.length());
                  if (used.containsKey(fileNameWoExt)) {
                    String absPath = file.toString();
                    String relPath =
                        Constants.LIBS_DIRECTORY
                            .relativize(file.getParent())
                            .toString()
                            .replace(File.separator, " ");
                    absPath = absPath.substring(0, absPath.length() - ext.length());
                    libMap.put(relPath + " " + used.get(fileNameWoExt), absPath);
                  }
                }
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (Exception iox) {
      iox.printStackTrace();
    }
    libraryMap = Collections.unmodifiableMap(libMap);
  }

  public static Map<String, String> getLibraryMap() {
    if (libraryMap == null) {
      synchronized (libraryMapMonitor) {
        if (libraryMap == null) {
          initLibraryMap();
        }
      }
    }
    return libraryMap;
  }

  public static List<ClassData> getClassData(String libraryPath) throws IOException {
    return getClassData(Constants.FS.getPath(libraryPath + ".json.zlib"));
  }

  public static List<ClassData> getClassData(Path libraryPath) throws IOException {
    try (Reader reader =
        new InputStreamReader(
            new InflaterInputStream(new ByteArrayInputStream(libDataCache.get(libraryPath))),
                StandardCharsets.UTF_8)) {
      return Utils.getGson().fromJson(reader, CLASSDATA_LIST_TYPE);
    } catch (ExecutionException ee) {
      if (ee.getCause() instanceof IOException) {
        throw (IOException) ee.getCause();
      } else {
        throw new RuntimeException(ee);
      }
    }
  }

  public static Path firstExisting(
      Path parent, javaslang.collection.List<Path> subPaths, boolean resolve) {
    return allExisting(parent, subPaths, resolve).getOrElse((Path) null);
  }

  public static javaslang.collection.List<Path> allExisting(
      Path parent, javaslang.collection.List<Path> subPaths, boolean resolve) {
    if (resolve) {
      return subPaths.map(parent::resolve).filter(Files::exists);
    } else {
      return subPaths.filter(sp -> Files.exists(parent.resolve(sp)));
    }
  }

  public static void visitAllFiles(@Nonnull Path directory, @Nonnull Consumer<Path> visitor) {
    visitAllFiles(
        directory,
        visitor,
        t -> {
          throw new RuntimeException(t);
        });
  }

  public static void visitAllFiles(
      @Nonnull Path directory,
      @Nonnull Consumer<Path> visitor,
      @Nonnull Consumer<Throwable> errorHandler) {
    Try.run(
            () ->
                Files.walkFileTree(
                    directory,
                    new SimpleFileVisitor<Path>() {
                      @Override
                      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                          throws IOException {
                        visitor.accept(file);
                        return FileVisitResult.CONTINUE;
                      }
                    }))
        .onFailure(errorHandler);
  }

  public static String getSHA256(Path f) {
    try (SeekableByteChannel bc = Files.newByteChannel(f)) {
      ByteBuffer bb = ByteBuffer.allocate(8192);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      int numRead;
      do {
        numRead = bc.read(bb);
        if (numRead > 0) {
          digest.update(bb.array(), 0, numRead);
        }
        bb.clear();
      } while (numRead != -1);
      return DatatypeConverter.printHexBinary(digest.digest());
    } catch (NoSuchAlgorithmException nsa) {
      throw new RuntimeException(
          "This platform does not support SHA-256 hash algorithm, file hashing failed!");
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  public static void deleteRecursive(Path path) throws IOException {
    Files.walkFileTree(
        path,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
        });
  }
}
