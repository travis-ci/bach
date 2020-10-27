package com.github.sormuras.bach;

import com.github.sormuras.bach.internal.Modules;
import com.github.sormuras.bach.internal.Paths;
import java.io.PrintStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Predicate;
import java.util.spi.ToolProvider;

/** Print-related API. */
public /*sealed*/ interface Print /*permits Bach*/ {

  /**
   * Returns the print stream for printing messages.
   *
   * @return the print stream for printing messages
   */
  PrintStream printer();

  /**
   * Print a listing of all files matching the given glob pattern.
   *
   * @param glob the glob pattern
   */
  default void printFind(String glob) {
    var matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
    var start = Path.of("");
    try (var stream =
        Files.find(start, 99, (path, bfa) -> Paths.isVisible(path) && matcher.matches(path))) {
      stream.map(Paths::slashed).filter(Predicate.not(String::isEmpty)).forEach(printer()::println);
    } catch (Exception exception) {
      throw new RuntimeException("find failed: " + glob, exception);
    }
  }

  /**
   * Print a sorted list of all modules locatable by the given module finder.
   *
   * @param finder the module finder to query for modules
   */
  default void printModules(ModuleFinder finder) {
    finder.findAll().stream()
        .map(ModuleReference::descriptor)
        .map(ModuleDescriptor::toNameAndVersion)
        .sorted()
        .forEach(printer()::println);
  }

  /**
   * Print a sorted list of all provided tools locatable by the given module finder.
   *
   * @param finder the module finder to query for tool providers
   */
  default void printToolProviders(ModuleFinder finder) {
    ServiceLoader.load(Modules.layer(finder), ToolProvider.class).stream()
        .map(ServiceLoader.Provider::get)
        .map(Print::describe)
        .sorted()
        .forEach(printer()::println);
  }

  private static String describe(ToolProvider tool) {
    var name = tool.name();
    var module = tool.getClass().getModule();
    var by = Optional.ofNullable(module.getDescriptor()).map(ModuleDescriptor::toNameAndVersion).orElse(module.toString());
    var info = switch (name) {
      case "jar" -> "Create an archive for classes and resources, and update or restore resources";
      case "javac" -> "Read Java class and interface definitions and compile them into class files";
      case "javadoc" -> "Generate HTML pages of API documentation from Java source files";
      case "javap" -> "Disassemble one or more class files";
      case "jdeps" -> "Launch the Java class dependency analyzer";
      case "jlink" -> "Assemble and optimize a set of modules into a custom runtime image";
      case "jmod" -> "Create JMOD files and list the content of existing JMOD files";
      case "jpackage" -> "Package a self-contained Java application";
      case "junit" -> "Launch the JUnit Platform";
      default -> tool.toString();
    };
    return "%s (provided by module %s)\n%s".formatted(name, by, info.indent(2));
  }
}