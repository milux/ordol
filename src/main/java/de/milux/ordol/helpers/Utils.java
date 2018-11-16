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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.milux.ordol.Constants;
import de.milux.ordol.data.ClassData;
import de.milux.ordol.data.MethodData;
import de.milux.ordol.data.UnitData;
import de.milux.ordol.gson.ClassDataAdapter;
import de.milux.ordol.gson.InterningStringDeserializer;
import de.milux.ordol.gson.MethodDataAdapter;
import de.milux.ordol.gson.UnitDataAdapter;
import io.vavr.CheckedRunnable;
import io.vavr.control.Try;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiPredicate;

public final class Utils {

  private Utils() {}

  public static final java.util.function.Function<Object, String> INTERN_STRING =
      s -> s == null ? null : ((String) s).intern();
  private static Map<String, Expression> expressionMap = new HashMap<>();
  private static Gson g = null;
  private static Gson gp = null;

  private static GsonBuilder getDefaultGsonBuilder() {
    return new GsonBuilder()
        .disableHtmlEscaping()
        .registerTypeAdapter(UnitData.class, new UnitDataAdapter())
        .registerTypeAdapter(MethodData.class, new MethodDataAdapter())
        .registerTypeAdapter(ClassData.class, new ClassDataAdapter())
        .registerTypeAdapter(String.class, new InterningStringDeserializer());
  }

  public static Gson getGson() {
    if (g == null) {
      g = getDefaultGsonBuilder().create();
    }
    return g;
  }

  public static Gson getGsonPretty() {
    if (gp == null) {
      gp = getDefaultGsonBuilder().setPrettyPrinting().create();
    }
    return gp;
  }

  public static ProcessBuilder getConsoleProcessBuilder(io.vavr.collection.List<String> cmd) {
    return new ProcessBuilder(Constants.SCRIPT_INTERPRETER.appendAll(cmd).toJavaList());
  }

  public static <T> List<T> getTrimmed(List<T> list) {
    if (list instanceof ArrayList) {
      ((ArrayList<T>) list).trimToSize();
    }
    return list;
  }

  public static void syncPrint(String... strings) {
    synchronized (System.out) {
      println(-1, strings);
    }
  }

  public static void syncPrint(long threadId, String... strings) {
    synchronized (System.out) {
      println(threadId, strings);
    }
  }

  public static void println(String... strings) {
    println(-1, strings);
  }

  public static void println(long threadId, String... strings) {
    if (strings.length == 0) {
      System.out.println();
    } else {
      for (String s : strings) {
        if (s.length() == 0) {
          System.out.println();
        } else {
          if (threadId < 0) {
            System.out.println(s);
          } else {
            System.out.println("#" + threadId + ": " + s);
          }
        }
      }
    }
  }

  /**
   * Returns the constant empty map if provided map is empty, an unmodifiable version of map
   * otherwise
   *
   * @param map the provided map
   * @return constant empty map or unmodifiable map
   */
  public static <T, U> Map<T, U> emptyOrROMap(Map<T, U> map) {
    if (map.isEmpty()) {
      return Collections.emptyMap();
    } else {
      return Collections.unmodifiableMap(map);
    }
  }

  /**
   * Returns the constant empty list if provided map is empty, an unmodifiable version of list
   * otherwise
   *
   * @param list the provided map
   * @return constant empty list or unmodifiable list
   */
  public static <T> List<T> emptyOrROList(List<T> list) {
    if (list.isEmpty()) {
      return Collections.emptyList();
    } else {
      return Collections.unmodifiableList(list);
    }
  }

  /**
   * Returns the constant empty set if provided map is empty, an unmodifiable version of set
   * otherwise
   *
   * @param set the provided map
   * @return constant empty set or unmodifiable set
   */
  public static <T> Set<T> emptyOrROSet(Set<T> set) {
    if (set.isEmpty()) {
      return Collections.emptySet();
    } else {
      return Collections.unmodifiableSet(set);
    }
  }

  private static Expression getExpression(@Nonnull String formula) {
    if (!expressionMap.containsKey(formula)) {
      Function min =
          new Function("min", 2) {
            @Override
            public double apply(double... args) {
              return Math.min(args[0], args[1]);
            }
          };
      Function max =
          new Function("max", 2) {
            @Override
            public double apply(double... args) {
              return Math.max(args[0], args[1]);
            }
          };
      ExpressionBuilder eb = new ExpressionBuilder(formula).function(min).function(max);
      if (formula.contains("size")) {
        eb.variable("size");
      }
      if (formula.contains("appBsPop")) {
        eb.variable("appBsPop");
      }
      Expression expression = eb.build();
      expressionMap.put(formula, expression);
      return expression;
    } else {
      return expressionMap.get(formula);
    }
  }

  public static double getDetectionThreshold(double size) {
    return getExpression(Constants.DETECT_THRESHOLD).setVariable("size", size).evaluate();
  }

  public static double getBitSetThreshold(double size, double appBsPop) {
    return getExpression(Constants.BITSET_THRESHOLD)
        .setVariable("size", size)
        .setVariable("appBsPop", appBsPop)
        .evaluate();
  }

  public static String toPercent(double val) {
    if (val < 0. || val > 1.) {
      throw new IllegalArgumentException(val + " is not in the interval [0.0; 1.0]");
    }
    return String.format("%.5f%%", val * 100.);
  }

  public static String benchmark(LongHolder th) {
    return benchmark(th, null);
  }

  public static String benchmark(LongHolder th, String s) {
    long elapsed = System.currentTimeMillis() - th.get();
    th.setCurrentTimeMillis();
    if (s != null) {
      return s + String.format(" Duration: %.3f sec", elapsed / 1000.);
    } else {
      return String.format("Duration: %.3f sec", elapsed / 1000.);
    }
  }

  /**
   * The central ExecutionService for parallel execution of tasks This ExecutionService uses daemon
   * threads which are terminated by the runtime environment on exit.
   */
  private static ForkJoinPool exs = new ForkJoinPool(Constants.NUM_THREADS);

  private static List<Future<?>> taskList = Collections.synchronizedList(new LinkedList<>());

  public static <V> Future<V> submitTask(Callable<V> task) {
    Future<V> f = exs.submit(task);
    taskList.add(f);
    return f;
  }

  public static Future<?> submitRunnable(Runnable runnable) {
    return exs.submit(runnable);
  }

  public static void waitForTasks() {
    waitForTasks(taskList);
  }

  public static void waitForTasks(List<Future<?>> taskList) {
    while (!taskList.isEmpty()) {
      try {
        taskList.remove(0).get();
      }catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        ie.printStackTrace();
      } catch (ExecutionException | CancellationException e) {
        e.printStackTrace();
      }
    }
  }

  public static void exToRtEx(CheckedRunnable r) {
    Try.run(r)
        .onFailure(
            t -> {
              throw new RuntimeException(t);
            });
  }

  public static <T> int indexOfFrom(T e, List<T> list, int offset, BiPredicate<T, T> comp) {
    if (comp == null) {
      comp =
          (a, b) -> {
            if (a == null) {
              return b == null;
            } else {
              return a.equals(b);
            }
          };
    }
    for (int i = offset, size = list.size(); i < size; i++) {
      if (comp.test(e, list.get(i))) {
        return i;
      }
    }
    return -1;
  }

  public static int getBitSetPrimeIdx(int kGrams) {
    int pIdx = -1;
    //noinspection StatementWithEmptyBody
    while (Constants.LIMITS.length > ++pIdx && kGrams > Constants.LIMITS[pIdx]) ;
    return pIdx;
  }

  public static BitSet makeBitSet(Set<Integer> hashes, int prime) {
    BitSet bitField = new BitSet();
    for (int h : hashes) {
      int bit = (int) (Integer.toUnsignedLong(h) % prime);
      // use linear probing until an unset bit is found or all bits are set
      int pr = 0;
      while (bitField.get(bit) && ++pr <= prime) {
        bit = (bit + 1) % prime;
      }
      bitField.set(bit);
      // all bits are set, exit loop
      if (pr > prime) {
        break;
      }
    }
    return bitField;
  }

  public static BitSet[] makeBitSets(Set<Integer> hashes) {
    BitSet[] bHashes = new BitSet[Constants.PRIMES.length];
    for (int i = 0; i < Constants.PRIMES.length; i++) {
      int prime = Constants.PRIMES[i];
      bHashes[i] = makeBitSet(hashes, prime);
    }
    return bHashes;
  }

  public static double getBitSetSimilarity(
      int primeIdx1, int primeIdx2, double divisor, BitSet[] bitSets1, BitSet[] bitSets2) {
    if (divisor == 0.) {
      return 0.;
    } else if (primeIdx1 >= 0 && primeIdx2 >= 0) {
      int pIdx = Math.max(primeIdx1, primeIdx2);
      BitSet tmpBs = (BitSet) bitSets1[pIdx].clone();
      tmpBs.and(bitSets2[pIdx]);
      return tmpBs.cardinality() / divisor;
    } else {
      // if both methods are empty, it's a perfect match, otherwise it's no match at all
      if (primeIdx1 == primeIdx2) {
        return 1.;
      } else {
        return 0.;
      }
    }
  }

  /** Thread-safe shared buffer for StringBuilder objects */
  private static Stack<StringBuilder> availBuilders = new Stack<>();

  /**
   * Get a string builder from a shared buffer or create a new one
   *
   * @return A recycled StringBuilder or a new StringBuilder with size initial 256
   */
  public static StringBuilder getBuilder() {
    try {
      return availBuilders.pop();
    } catch (EmptyStackException e) {
      return new StringBuilder(256);
    }
  }

  /**
   * Reads the given StringBuffer calling toString(), then resets the StringBuffer with setLength(0)
   * and adds it to the pool
   *
   * @param b The builder to be read-out and recycled
   * @return The result String of the StringBuilder
   */
  public static String freeBuilder(StringBuilder b) {
    String res = b.toString();
    // reset StringBuilder
    b.setLength(0);
    availBuilders.push(b);
    return res;
  }
}
