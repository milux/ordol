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

import de.milux.ordol.helpers.CLIDispatcher;
import de.milux.ordol.helpers.CLIHelper;
import de.milux.ordol.helpers.Utils;
import io.vavr.control.Try;
import org.apache.commons.cli.*;

import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static de.milux.ordol.Constants.K;
import static de.milux.ordol.Constants.NUM_THREADS;
import static de.milux.ordol.helpers.Utils.println;

public class CLI {
  private static final String MAPPER_CMD = "process";
  private static final String ANALYZER_CMD = "analyze";
  private static final String STATS_CMD = "stats";
  private static final String MAPPER_ALL = "processAll";
  private static volatile boolean keepRunning = true;
  private static volatile boolean hasShutdownHook = false;
  private static Thread shutdownHook = null;

  public static void main(String[] args) {
    // wait for graceful shutdown of main thread
    synchronized (CLI.class) {
      if (!hasShutdownHook) {
        final Thread mainThread = Thread.currentThread();
        shutdownHook =
            new Thread(
                () -> {
                  if (mainThread.isAlive()) {
                    Utils.syncPrint("\n### Received shutdown command, shutting down... ###\n");
                  }
                  keepRunning = false;
                  Try.run(mainThread::join).onFailure(Throwable::printStackTrace);
                });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        hasShutdownHook = true;
      }
    }
    // remove the first part (command) from the commandline argument list
    String[] destArgs = new String[args.length - 1];
    System.arraycopy(args, 1, destArgs, 0, destArgs.length);
    // create a dispatcher object depending on the requested task
    CLIDispatcher cliDispatcher;
    if (MAPPER_CMD.equals(args[0])) {
      cliDispatcher = new LibraryMapper();
    } else if (ANALYZER_CMD.equals(args[0])) {
      cliDispatcher = new AppAnalyzer();
    } else if (STATS_CMD.equals(args[0])) {
      cliDispatcher = new BitStats();
    } else if (MAPPER_ALL.equals(args[0])) {
      cliDispatcher =
          new LibraryMapper() {
            @Override
            public Options getOptions() {
              return new Options()
                  .addOption(
                      Option.builder("jcp")
                          .longOpt("javac-path")
                          .hasArg()
                          .argName("path")
                          .desc("The path of the javac executable to call for Java compilation.")
                          .build());
            }

            @Override
            public void dispatch(CommandLine cmd, Options options) {
              if (cmd.hasOption("jcp")) {
                Constants.JAVAC_EXEC = cmd.getOptionValue("jcp");
              }
              whileException(this::analyzeAll);
              indexDuplicates();
            }
          };
    } else {
      System.out.println(
          "Usage: "
              + MAPPER_CMD
              + "|"
              + MAPPER_ALL
              + "|"
              + ANALYZER_CMD
              + "|"
              + STATS_CMD
              + " [-h] [OPTIONS...]");
      return;
    }
    Options options =
        cliDispatcher
            .getOptions()
            .addOption(
                Option.builder("k")
                    .hasArg()
                    .argName("K")
                    .type(Number.class)
                    .desc("Length of k-grams for mapping or analysis, default: " + K)
                    .build())
            .addOption(
                Option.builder("mt")
                    .hasArg()
                    .argName("threads")
                    .type(Number.class)
                    .desc(
                        "Maximum number of parallel tasks/threads, default: "
                            + "# processors ("
                            + NUM_THREADS
                            + ")")
                    .build())
            .addOption(Option.builder("h").desc("Print help/usage information to console").build());
    try {
      CommandLine cmd = new DefaultParser().parse(options, destArgs);
      if (cmd.hasOption("h")) {
        printHelp(options, args[0]);
        return;
      }
      // override K value
      K = CLIHelper.validateInt(cmd, "k", 1, 10, K);
      // override max threads
      NUM_THREADS = CLIHelper.validateInt(cmd, "mt", 1, Integer.MAX_VALUE, NUM_THREADS);
      // parallel app analysis
      if (ANALYZER_CMD.equals(args[0]) && cmd.hasOption("pa")) {
        // get number of apps to analyse in parallel
        int parallelAnalysis = CLIHelper.validateInt(cmd, "pa", 1, Integer.MAX_VALUE, 1);
        if (parallelAnalysis > 1) {
          List<Future<?>> taskList =
              IntStream.range(0, parallelAnalysis)
                  .mapToObj(
                      i ->
                          Utils.submitRunnable(
                              () ->
                                  Try.run(
                                          () ->
                                              (i == 0 ? cliDispatcher : new AppAnalyzer())
                                                  .dispatch(cmd, options))
                                      .onFailure(
                                          e -> {
                                            if (e instanceof ParseException
                                                || e instanceof IllegalArgumentException) {
                                              println("Error parsing command:", e.getMessage(), "");
                                              printHelp(options, args[0]);
                                            } else {
                                              e.printStackTrace();
                                            }
                                          })))
                  .collect(Collectors.toList());
          Utils.waitForTasks(taskList);
          return;
        }
      }
      cliDispatcher.dispatch(cmd, options);
    } catch (ParseException | IllegalArgumentException e) {
      println("Error parsing command:", e.getMessage(), "");
      printHelp(options, args[0]);
    }
  }

  /**
   * Returns true when shutdown was requested for the application.
   *
   * @return A boolean indicating whether the application is shutting down
   */
  public static boolean keepRunning() {
    return keepRunning;
  }

  /** Removes the shutdown hook which waits for the main thread to finish */
  public static void removeShutdownHook() {
    synchronized (CLI.class) {
      if (shutdownHook != null) {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        shutdownHook = null;
      }
      hasShutdownHook = false;
    }
  }

  /**
   * Prints help/usage information to console.
   *
   * @param options options that are parsed by this application
   */
  public static void printHelp(Options options, String firstParam) {
    HelpFormatter hf = new HelpFormatter();
    hf.setWidth(100);
    hf.printHelp(firstParam, options, true);
  }
}
