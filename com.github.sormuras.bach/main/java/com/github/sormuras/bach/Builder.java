package com.github.sormuras.bach;

import com.github.sormuras.bach.internal.Paths;
import com.github.sormuras.bach.module.ModuleDirectory;
import com.github.sormuras.bach.module.ModuleSearcher;
import com.github.sormuras.bach.project.CodeSpaces;
import com.github.sormuras.bach.project.MainCodeSpace;
import com.github.sormuras.bach.project.ModuleDeclaration;
import com.github.sormuras.bach.project.Project;
import com.github.sormuras.bach.project.SourceFolder;
import com.github.sormuras.bach.project.TestCodeSpace;
import com.github.sormuras.bach.tool.Command;
import com.github.sormuras.bach.tool.ToolCall;
import com.github.sormuras.bach.tool.ToolResponse;
import com.github.sormuras.bach.tool.ToolRunner;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.System.Logger.Level;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** A modular Java project builder. */
public class Builder {

  private final Bach bach;
  private final Project project;
  private final ModuleDirectory moduleDirectory;
  private final ToolRunner runner;

  /**
   * Initialize this builder with the given components.
   *
   * @param bach the underlying Bach instance
   * @param project the project to build
   */
  public Builder(Bach bach, Project project) {
    this.bach = bach;
    this.project = project;

    this.moduleDirectory = ModuleDirectory.of(Bach.LIBRARIES, project.library().links());
    this.runner = new ToolRunner(moduleDirectory.finder());
  }

  /** Builds a modular Java project. */
  public void build() {
    info("Build project %s %s", project.name(), project.version());
    var start = Instant.now();
    var logbook = bach.logbook();
    try {
      loadRequiredAndMissingModules();
      buildAllSpaces(project.spaces());
    } catch (Exception exception) {
      var trace = new StringWriter();
      exception.printStackTrace(new PrintWriter(trace));
      var message = "Build failed: " + exception + '\n' + trace.toString().indent(0);
      logbook.log(Level.ERROR, message);
      throw new BuildException(message);
    } finally {
      info("Build took %s", Logbook.toString(Duration.between(start, Instant.now())));
      var file = logbook.write(project);
      logbook.accept("Logbook written to " + file.toUri());
    }
  }

  /** Load required and missing modules in a best-effort manner. */
  public void loadRequiredAndMissingModules() {
    var searchers = new ArrayList<>(project.library().searchers());
    searchers.add(ModuleSearcher.ofBestEffort(bach));
    var searcher = ModuleSearcher.compose(searchers.toArray(ModuleSearcher[]::new));
    var requires = project.library().requires();
    requires.forEach(module -> bach.loadModule(moduleDirectory, searcher, module));
    bach.loadMissingModules(moduleDirectory, searcher);
  }

  /**
   * Builds all code spaces.
   *
   * @param spaces the code spaces to build
   */
  public void buildAllSpaces(CodeSpaces spaces) {
    if (spaces.isEmpty()) throw new BuildException("No modules declared?!");
    buildMainCodeSpace(spaces.main());
    buildTestCodeSpace(spaces.test());
  }

  /**
   * Builds the main space.
   *
   * <ul>
   *   <li>javac + jar
   *   <li>javadoc
   *   <li>jlink
   *   <li>jpackage
   * </ul>
   *
   * @param main the main space to build
   */
  public void buildMainCodeSpace(MainCodeSpace main) {
    var modules = main.modules();
    info("Compile %d main module%s", modules.size(), modules.size() == 1 ? "" : "s");
    if (modules.isEmpty()) return;

    Paths.deleteDirectories(main.workspace("modules"));
    var release = main.release();
    if (release >= 9) run(computeMainJavacCall(release));
    else {
      var feature = Runtime.version().feature();
      run(computeMainJavacCall(feature));
      buildMainSingleReleaseVintageModules(feature);
    }

    Paths.createDirectories(main.workspace("modules"));
    for (var declaration : main.modules().map().values()) {
      declaration.sources().list().stream()
          .filter(SourceFolder::isTargeted)
          .peek(System.out::println)
          .forEach(folder -> run(computeMainJavacCall(declaration.name(), folder)));
      run(computeMainJarCall(declaration));
    }

    if (isGenerateApiDocumentation()) {
      info("Generate API documentation");
      run(computeMainDocumentationJavadocCall());
      run(computeMainDocumentationJarCall());
    }

    if (isGenerateCustomRuntimeImage()) {
      info("Generate custom runtime image");
      Paths.deleteDirectories(main.workspace("image"));
      run(computeMainJLinkCall());
    }
  }

  /**
   * @param release the release
   * @return the {@code javac} call to compile all modules of the main space
   */
  public ToolCall computeMainJavacCall(int release) {
    var main = project.spaces().main();
    return Command.builder("javac")
        .with("--release", release)
        .with("--module", main.modules().toNames(","))
        .with("--module-version", project.version())
        .with("--module-source-path", main.toModuleSourcePath())
        .with("--module-path", main.toModulePath())
        .withEach(main.tweaks().arguments("javac"))
        .with("-d", main.classes(release))
        .build();
  }

  /**
   * @param module the name of the module to compile
   * @param folder the source folder to compile
   * @return the {@code javac} call to compile a version of a multi-release module
   */
  public ToolCall computeMainJavacCall(String module, SourceFolder folder) {
    var main = project.spaces().main();
    var release = folder.release();
    var classes = main.workspace("classes-mr", release + "/" + module);
    var javaSourceFiles = new ArrayList<Path>();
    Paths.find(Path.of(module, "main/java-" + release), "**.java", javaSourceFiles::add);
    return Command.builder("javac")
        .with("--release", release)
        .with("--module-version", project.version())
        .with("--module-path", main.classes())
        .with("-implicit:none") // generate classes for explicitly referenced source files
        .withEach(main.tweaks().arguments("javac"))
        .with("-d", classes)
        .withEach(javaSourceFiles)
        .build();
  }

  /**
   * Builds all modules targeting Java 7 or Java 8.
   *
   * @param mainRelease the main classes release feature number
   */
  public void buildMainSingleReleaseVintageModules(int mainRelease) {
    var main = project.spaces().main();
    var release = main.release();
    if (release > 8) throw new IllegalStateException("release too high: " + release);

    var classPaths = new ArrayList<Path>();
    var libraries = Bach.LIBRARIES;
    main.modules().toNames().forEach(name -> classPaths.add(main.classes(mainRelease, name)));
    if (Files.isDirectory(libraries)) classPaths.addAll(Paths.list(libraries, Paths::isJarFile));

    for (var declaration : main.modules().map().values()) {
      var moduleInfoJavaFiles = new ArrayList<Path>();
      declaration.sources().list().stream()
          .filter(SourceFolder::isModuleInfoJavaPresent)
          .forEach(folder -> moduleInfoJavaFiles.add(folder.path().resolve("module-info.java")));

      var compileModuleOnly =
          Command.builder("javac")
              .with("--release", 9)
              .with("--module-version", project.version())
              .with("--module-source-path", main.toModuleSourcePath())
              .with("--module-path", main.toModulePath())
              .with("-implicit:none") // generate classes for explicitly referenced source files
              .withEach(main.tweaks().arguments("javac"))
              .with("-d", main.classes())
              .withEach(moduleInfoJavaFiles)
              .build();
      run(compileModuleOnly);

      var module = declaration.name();
      var path = Path.of(module, "main/java");
      if (Files.notExists(path)) continue;

      var javaSourceFiles = new ArrayList<Path>();
      Paths.find(path, "**.java", javaSourceFiles::add);
      var javac =
          Command.builder("javac")
              .with("--release", release) // 7 or 8
              .with("--class-path", Paths.join(classPaths))
              .withEach(main.tweaks().arguments("javac"))
              .with("-d", main.classes().resolve(module))
              .withEach(javaSourceFiles);
      run(javac.build());
    }
  }

  /**
   * @param module the module declaration to create an archive for
   * @return the {@code jar} call to archive all assets for the given module
   */
  public ToolCall computeMainJarCall(ModuleDeclaration module) {
    var main = project.spaces().main();
    var archive = computeMainJarFileName(module);
    var mainClass = module.reference().descriptor().mainClass();
    var name = module.name();
    var jar =
        Command.builder("jar")
            .with("--create")
            .with("--file", main.workspace("modules", archive))
            .with(mainClass, (builder, className) -> builder.with("--main-class", className))
            .withEach(main.tweaks().arguments("jar"))
            .withEach(main.tweaks().arguments("jar(" + name + ')'));
    // add base classes
    var baseClasses = main.classes().resolve(name);
    if (Files.isDirectory(baseClasses)) jar.with("-C", baseClasses, ".");
    // add base resources
    if (module.reference().info().toString().equals("module-info.java")) {
      jar.with("module-info.java");
      var dot = name.indexOf('.');
      var prefix = name.substring(0, dot > 0 ? dot : name.length());
      try (var stream = Files.walk(Path.of(""))) {
        stream.filter(path -> path.startsWith(prefix)).forEach(jar::with);
      } catch (Exception ignore) {}
    } else {
      for (var folder : module.resources().list()) {
        if (folder.isTargeted()) continue; // handled later
        jar.with("-C", folder.path(), ".");
      }
      // add targeted classes and targeted resources in ascending order
      for (var directory : computeMainJarTargetedDirectories(module).entrySet()) {
        jar.with("--release", directory.getKey());
        for (var path : directory.getValue()) jar.with("-C", path, ".");
      }
    }
    return jar.build();
  }

  /**
   * @param module the module declaration
   * @return the name of the JAR file for the given module declaration
   */
  public String computeMainJarFileName(ModuleDeclaration module) {
    var slug = project.spaces().main().jarslug();
    var builder = new StringBuilder(module.name());
    if (!slug.isEmpty()) builder.append('@').append(slug);
    return builder.append(".jar").toString();
  }

  /**
   * @param module the module declaration
   * @return a map with "release to list-of-path" entries
   */
  public TreeMap<Integer, List<Path>> computeMainJarTargetedDirectories(ModuleDeclaration module) {
    var main = project.spaces().main();
    var assets = new TreeMap<Integer, List<Path>>();
    // targeted classes
    for (var source : module.sources().list()) {
      if (!source.isTargeted()) continue;
      var release = source.release();
      var classes = main.workspace("classes-mr", release + "/" + module.name());
      assets.merge(
          release,
          List.of(classes),
          (o, n) -> Stream.concat(o.stream(), n.stream()).collect(Collectors.toList()));
    }
    // targeted resources
    for (var resource : module.resources().list()) {
      if (!resource.isTargeted()) continue;
      var release = resource.release();
      assets.merge(
          release,
          List.of(resource.path()),
          (o, n) -> Stream.concat(o.stream(), n.stream()).collect(Collectors.toList()));
    }
    return assets;
  }

  /** @return the javadoc call generating the API documentation for all main modules */
  public ToolCall computeMainDocumentationJavadocCall() {
    var main = project.spaces().main();
    var api = main.documentation("api");
    return Command.builder("javadoc")
        .with("--module", main.modules().toNames(","))
        .with("--module-source-path", main.toModuleSourcePath())
        .with("--module-path", main.toModulePath())
        .withEach(main.tweaks().arguments("javadoc"))
        .with("-d", api)
        .build();
  }

  /** @return the jar call generating the API documentation archive */
  public ToolCall computeMainDocumentationJarCall() {
    var main = project.spaces().main();
    var api = main.documentation("api");
    var file = project.name() + "-api-" + project.version() + ".zip";
    return Command.builder("jar")
        .with("--create")
        .with("--file", api.getParent().resolve(file))
        .with("--no-manifest")
        .with("-C", api, ".")
        .build();
  }

  /** @return the jllink call */
  public ToolCall computeMainJLinkCall() {
    var main = project.spaces().main();
    var test = project.spaces().test();
    return Command.builder("jlink")
        .with("--add-modules", main.modules().toNames(","))
        .with("--module-path", test.toModulePath())
        .withEach(main.tweaks().arguments("jlink"))
        .with("--output", main.workspace("image"))
        .build();
  }

  /**
   * Builds the test space.
   *
   * <ul>
   *   <li>javac + jar
   *   <li>junit
   * </ul>
   *
   * @param test the test space to build
   */
  public void buildTestCodeSpace(TestCodeSpace test) {
    var modules = test.modules();
    info("Compile %d test module%s", modules.size(), modules.size() == 1 ? "" : "s");
    if (modules.isEmpty()) return;

    Paths.deleteDirectories(test.workspace("modules-test"));

    info("Compile test modules");
    run(computeTestJavacCall());
    Paths.createDirectories(test.workspace("modules-test"));
    for (var module : project.spaces().test().modules().map().values()) {
      run(computeTestJarCall(module));
    }

    if (moduleDirectory.finder().find("org.junit.platform.console").isPresent()) {
      for (var declaration : project.spaces().test().modules().map().values()) {
        var module = declaration.name();
        var archive = module + "@" + project.version() + "+test.jar";
        var finder =
            ModuleFinder.of(
                test.workspace("modules-test", archive), // module under test
                test.workspace("modules"), // main modules
                test.workspace("modules-test"), // (more) test modules
                Bach.LIBRARIES // external modules
                );
        info("Launch JUnit Platform for test module: %s", module);
        var junit = computeTestJUnitCall(declaration);
        info(junit.toCommand().toString());
        var response = runner.run(junit, finder, module);
        bach.logbook().log(response);
      }
      var errors = bach.logbook().responses(ToolResponse::isError);
      if (errors.size() > 0) {
        throw new BuildException("JUnit reported failed test module(s): " + errors.size());
      }
    }
  }

  /** @return the {@code javac} call to compile all modules of the test space. */
  public ToolCall computeTestJavacCall() {
    var main = project.spaces().main();
    var test = project.spaces().test();
    return Command.builder("javac")
        .with("--module", test.modules().toNames(","))
        .with("--module-source-path", test.toModuleSourcePath())
        .with("--module-path", test.toModulePath())
        .withEach(
            test.modules().toModulePatches(main.modules()).entrySet(),
            (javac, patch) -> javac.with("--patch-module", patch.getKey() + '=' + patch.getValue()))
        .withEach(test.tweaks().arguments("javac"))
        .with("-d", test.classes())
        .build();
  }

  /**
   * @param declaration the module declaration to create an archive for
   * @return the {@code jar} call to archive all assets for the given module
   */
  public ToolCall computeTestJarCall(ModuleDeclaration declaration) {
    var module = declaration.name();
    var archive = module + "@" + project.version() + "+test.jar";
    var test = project.spaces().test();
    return Command.builder("jar")
        .with("--create")
        .with("--file", test.workspace("modules-test", archive))
        .withEach(test.tweaks().arguments("jar"))
        .withEach(test.tweaks().arguments("jar(" + module + ')'))
        .with("-C", test.classes().resolve(module), ".")
        .build();
  }

  /**
   * @param declaration the module declaration to scan for tests
   * @return the {@code junit} call to launch the JUnit Platform for
   */
  public ToolCall computeTestJUnitCall(ModuleDeclaration declaration) {
    var module = declaration.name();
    var test = project.spaces().test();
    return Command.builder("junit")
        .with("--select-module", module)
        .with("--reports-dir", test.workspace("reports", "junit-test", module))
        .withEach(test.tweaks().arguments("junit"))
        .withEach(test.tweaks().arguments("junit(" + module + ')'))
        .build();
  }

  /**
   * Log a formatted message at info level.
   *
   * @param format the message format
   * @param args the arguments to apply
   */
  public void info(String format, Object... args) {
    bach.logbook().log(Level.INFO, format, args);
  }

  /** @return {@code true} if an API documenation should be generated, else {@code false} */
  public boolean isGenerateApiDocumentation() {
    return project.spaces().main().generateApiDocumentation();
  }

  /** @return {@code true} if a custom runtime image should be generated, else {@code false} */
  public boolean isGenerateCustomRuntimeImage() {
    return project.spaces().main().generateCustomRuntimeImage();
  }

  /**
   * Runs the given tool call.
   *
   * @param call the tool call to run
   */
  public void run(ToolCall call) {
    info(call.toCommand().toString());
    var response = runner.run(call);
    bach.logbook().log(response);
    response.checkSuccessful();
  }
}
