/*
 * Bach - Java Shell Builder
 * Copyright (C) 2019 Christian Stein
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.sormuras.bach;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

/*BODY*/
public class Bach {

  public static String VERSION = "2-ea";

  /**
   * Create new Bach instance with default properties.
   *
   * @return new default Bach instance
   */
  public static Bach of() {
    var out = new PrintWriter(System.out, true);
    var err = new PrintWriter(System.err, true);
    var home = Path.of("");
    var work = Path.of("bin");
    return new Bach(out, err, home, work);
  }

  public static void main(String... args) {
    var bach = Bach.of();
    bach.main(args.length == 0 ? List.of("build") : List.of(args));
  }

  /** Text-output writer. */
  final PrintWriter out, err;
  /** Home directory. */
  final Path home;
  /** Workspace directory. */
  final Path work;

  public Bach(PrintWriter out, PrintWriter err, Path home, Path work) {
    this.out = out;
    this.err = err;
    this.home = home;
    this.work = work;
  }

  void main(List<String> args) {
    var arguments = new ArrayDeque<>(args);
    while (!arguments.isEmpty()) {
      var argument = arguments.pop();
      try {
        switch (argument) {
          case "build": build(); continue;
          case "validate": validate(); continue;
        }
        // Try Bach API method w/o parameter -- single argument is consumed
        var method = Util.findApiMethod(getClass(), argument);
        if (method.isPresent()) {
          method.get().invoke(this);
          continue;
        }
        // Try provided tool -- all remaining arguments are consumed
        var tool = ToolProvider.findFirst(argument);
        if (tool.isPresent()) {
          var code = tool.get().run(out, err, arguments.toArray(String[]::new));
          if (code != 0) {
            throw new RuntimeException("Tool " + argument + " returned: " + code);
          }
          return;
        }
      } catch (ReflectiveOperationException e) {
        throw new Error("Reflective operation failed for: " + argument, e);
      }
      throw new IllegalArgumentException("Unsupported argument: " + argument);
    }
  }

  String getBanner() {
    var module = getClass().getModule();
    try (var stream = module.getResourceAsStream("de/sormuras/bach/banner.txt")) {
      if (stream == null) {
        return String.format("Bach.java %s (member of %s)", VERSION, module);
      }
      var lines = new BufferedReader(new InputStreamReader(stream)).lines();
      var banner = lines.collect(Collectors.joining(System.lineSeparator()));
      return banner + " " + VERSION;
    } catch (IOException e) {
      throw new UncheckedIOException("loading banner resource failed", e);
    }
  }

  public void help() {
    out.println("F1! F1! F1!");
    out.println("Method API");
    Arrays.stream(getClass().getMethods())
        .filter(Util::isApiMethod)
        .map(m -> "  " + m.getName() + " (" + m.getDeclaringClass().getSimpleName() + ")")
        .sorted()
        .forEach(out::println);
    out.println("Provided tools");
    ServiceLoader.load(ToolProvider.class).stream()
        .map(provider -> "  " + provider.get().name())
        .sorted()
        .forEach(out::println);
  }

  public void build() {
    info();
    validate();
  }

  public void info() {
    out.printf("Bach (%s)%n", VERSION);
    out.printf("  home='%s' -> %s%n", home, home.toUri());
    out.printf("  work='%s'%n", work);
  }

  public void validate() {
    class Error extends AssertionError {
      private Error(String expected, String actual, Object hint) {
        super(String.format("expected %s to be %s: %s", expected, actual, hint));
      }
    }
    if (!Files.isDirectory(home)) throw new Error("home", "a directory", home.toUri());
    if (Files.exists(work)) {
      if (!Files.isDirectory(work)) throw new Error("work", "a directory: %s", work.toUri());
      if (!work.toFile().canWrite()) throw new Error("work", "writable: %s", work.toUri());
    } else {
      var parentOfWork = work.toAbsolutePath().getParent();
      if (parentOfWork != null && !parentOfWork.toFile().canWrite())
        throw new Error("parent of work", "writable", parentOfWork.toUri());
    }
  }

  public void version() {
    out.println(getBanner());
  }
}
