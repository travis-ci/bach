package com.github.sormuras.bach.api;

import com.github.sormuras.bach.Command;
import com.github.sormuras.bach.project.Flag;
import com.github.sormuras.bach.project.LocalModule;
import com.github.sormuras.bach.project.SourceFolder;
import com.github.sormuras.bach.tool.JDeps;
import com.github.sormuras.bach.tool.JLink;
import com.github.sormuras.bach.tool.Jar;
import com.github.sormuras.bach.tool.Javac;
import com.github.sormuras.bach.tool.Javadoc;
import com.github.sormuras.bach.util.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Methods related to building projects. */
public interface ProjectBuildMainSpaceAPI extends API {

  /**
   * Build all assets of the main code space.
   *
   * <ul>
   *   <li>Compile sources and jar generated classes
   *   <li>Check modules
   *   <li>Generate API documentation
   *   <li>Generate custom runtime image
   * </ul>
   *
   * @see #buildProjectMainBuildModules()
   * @see #buildProjectMainCheckModules()
   * @see #buildProjectMainGenerateAPIDocumentation()
   * @see #buildProjectMainGenerateCustomRuntimeImage()
   */
  default void buildProjectMainSpace() throws Exception {
    buildProjectMainBuildModules();
    buildProjectMainCheckModules();
    buildProjectMainGenerateAPIDocumentation();
    buildProjectMainGenerateCustomRuntimeImage();
  }

  /** Compile and jar main modules. */
  default void buildProjectMainBuildModules() {
    var main = bach().project().spaces().main();
    var modules = main.declarations();
    if (modules.isEmpty()) {
      log("Main module list is empty, nothing to build here.");
      return;
    }
    var s = modules.size() == 1 ? "" : "s";
    say("Build %d main module%s: %s", modules.size(), s, modules.toNames(", "));

    var release = main.release();
    var feature = release != 0 ? release : Runtime.version().feature();
    var classes = bach().folders().workspace("classes-main-" + feature);

    var workspaceModules = bach().folders().workspace("modules");
    Paths.deleteDirectories(workspaceModules);
    if (feature == 8) {
      bach().run(buildProjectMainJavac(9, classes)).requireSuccessful();
      buildProjectMainSpaceClassesForJava8(classes);
    } else {
      bach().run(buildProjectMainJavac(release, classes)).requireSuccessful();
    }

    Paths.createDirectories(workspaceModules);
    var jars = new ArrayList<Jar>();
    var javacs = new ArrayList<Javac>();
    for (var declaration : modules.map().values()) {
      for (var folder : declaration.sources().list()) {
        if (!folder.isTargeted()) continue;
        javacs.add(buildProjectMainJavac(declaration, folder, classes));
      }
      jars.add(buildProjectMainJar(declaration, classes));
    }
    if (!javacs.isEmpty()) bach().run(javacs.stream()).requireSuccessful();
    bach().run(jars.stream()).requireSuccessful();
  }

  /** Check main modules. */
  default void buildProjectMainCheckModules() {
    if (!bach().project().settings().tools().enabled("jdeps")) return;
    say("Check main modules");
    var main = bach().project().spaces().main();
    var modules = main.declarations();
    var jdeps = new ArrayList<JDeps>();
    for (var declaration : modules.map().values()) {
      jdeps.add(buildProjectMainJDeps(declaration));
    }
    bach().run(jdeps.stream()).requireSuccessful();
  }

  /** Generate HTML pages of API documentation from main modules' source files. */
  default void buildProjectMainGenerateAPIDocumentation() {
    if (!bach().project().settings().tools().enabled("javadoc")) return;
    say("Generate API documentation");
    var api = bach().folders().workspace("documentation", "api");
    bach().run(buildProjectMainJavadoc(api)).requireSuccessful();
    bach().run(buildProjectMainJavadocJar(api)).requireSuccessful();
  }

  /** Assemble and optimize main modules and their dependencies into a custom runtime image. */
  default void buildProjectMainGenerateCustomRuntimeImage() {
    if (!bach().project().settings().tools().enabled("jlink")) return;
    say("Assemble custom runtime image");
    var image = bach().folders().workspace("image");
    Paths.deleteDirectories(image);
    bach().run(buildProjectMainJLink(image));
  }

  default void buildProjectMainSpaceClassesForJava8(Path classes) {
    var project = bach().project();
    var folders = project.settings().folders();
    var main = project.spaces().main();
    var classPaths = new ArrayList<Path>();
    main.declarations().toNames().forEach(name -> classPaths.add(classes.resolve(name)));
    classPaths.addAll(Paths.list(bach().folders().externalModules(), Paths::isJarFile));
    var javacs = new ArrayList<Javac>();
    for (var declaration : main.declarations().map().values()) {
      var name = declaration.name();
      var sources = declaration.sources();
      var root = sources.list().isEmpty() ? folders.root() : sources.first().path();
      var java8Files = Paths.find(root, 99, ProjectBuildMainSpaceAPI::isJava8File);
      if (java8Files.isEmpty()) continue; // skip aggregator module
      var compileSources =
          Command.javac()
              .with("--release", main.release()) // 8
              .with("--class-path", classPaths)
              .ifTrue(bach().is(Flag.STRICT), javac -> javac.with("-Xlint").with("-Werror"))
              .withAll(main.tweaks().arguments("javac"))
              .with("-d", classes.resolve(name))
              .withAll(java8Files);
      javacs.add(compileSources);
    }
    if (javacs.isEmpty()) return; // no javac command collected
    bach().run(javacs.stream()).requireSuccessful();
  }

  /** Test supplied path for pointing to a Java 8 compilation unit. */
  private static boolean isJava8File(Path path) {
    var name = Paths.name(path);
    return !path.startsWith(".bach") // ignore all files in `.bach` directory
        && name.endsWith(".java")
        && !name.equals("module-info.java") // ignore module declaration compilation units
        && Files.isRegularFile(path);
  }

  /**
   * {@return the {@code javac} call to compile all configured modules of the main space}
   *
   * @param release the Java feature release number to compile modules for
   */
  default Javac buildProjectMainJavac(int release, Path classes) {
    var project = bach().project();
    var main = project.spaces().main();
    return Command.javac()
        .ifTrue(release != 0, javac -> javac.with("--release", release))
        .with("--module", main.declarations().toNames(","))
        .with("--module-version", project.version())
        .forEach(
            main.declarations().toModuleSourcePaths(false),
            (javac, path) -> javac.with("--module-source-path", path))
        .ifPresent(main.modulePaths().pruned(), (javac, paths) -> javac.with("--module-path", paths))
        .ifTrue(bach().is(Flag.STRICT), javac -> javac.with("-Xlint").with("-Werror"))
        .withAll(main.tweaks().arguments("javac"))
        .with("-d", classes);
  }

  /** {@return the {@code javac} call to compile a specific version of a multi-release module} */
  default Javac buildProjectMainJavac(
      LocalModule local, SourceFolder folder, Path classes) {
    var name = local.name();
    var project = bach().project();
    var main = project.spaces().main();
    var release = folder.release();
    var javaSourceFiles = Paths.find(folder.path(), 99, Paths::isJavaFile);
    return Command.javac()
        .with("--release", release)
        .with("--module-version", project.version())
        .ifPresent(main.modulePaths().pruned(), (javac, paths) -> javac.with("--module-path", paths))
        .with("--class-path", classes.resolve(name))
        .with("-implicit:none") // generate classes for explicitly referenced source files
        .withAll(main.tweaks().arguments("javac"))
        .withAll(main.tweaks().arguments("javac(" + name + ")"))
        .withAll(main.tweaks().arguments("javac(" + release + ")"))
        .withAll(main.tweaks().arguments("javac(" + name + "@" + release + ")"))
        .with("-d", buildProjectMultiReleaseClassesDirectory(name, release))
        .withAll(javaSourceFiles);
  }

  private Path buildProjectMultiReleaseClassesDirectory(String module, int release) {
    return bach().folders().workspace("classes-mr-" + release, module);
  }

  default Jar buildProjectMainJar(LocalModule local, Path classes) {
    var project = bach().project();
    var main = project.spaces().main();
    var name = local.name();
    var file = bach().folders().workspace("modules", buildProjectMainJarFileName(name));
    var mainClass = local.reference().descriptor().mainClass();
    var jar =
        Command.jar()
            .ifTrue(bach().is(Flag.VERBOSE), command -> command.with("--verbose"))
            .with("--create")
            .with("--file", file)
            .ifPresent(mainClass, (args, value) -> args.with("--main-class", value))
            .withAll(main.tweaks().arguments("jar"))
            .withAll(main.tweaks().arguments("jar(" + name + ")"));
    var baseClasses = classes.resolve(name);
    if (Files.isDirectory(baseClasses)) jar = jar.with("-C", baseClasses, ".");
    // include base resources
    for (var folder : local.resources().list()) {
      if (folder.isTargeted()) continue; // handled later
      jar = jar.with("-C", folder.path(), ".");
    }
    // add (future) targeted classes and targeted resources in ascending order
    for (int release = 9; release <= Runtime.version().feature(); release++) {
      var paths = new ArrayList<Path>();
      var isSourceTargeted = local.sources().stream(release).findAny().isPresent();
      if (isSourceTargeted) paths.add(buildProjectMultiReleaseClassesDirectory(name, release));
      local.resources().stream(release).map(SourceFolder::path).forEach(paths::add);
      if (paths.isEmpty()) continue;
      jar = jar.with("--release", release);
      for (var path : paths) jar = jar.with("-C", path, ".");
    }
    return jar;
  }

  default String buildProjectMainJarFileName(String module) {
    return module + '@' + bach().project().versionNumberAndPreRelease() + ".jar";
  }

  default JDeps buildProjectMainJDeps(LocalModule local) {
    var folders = bach().folders();
    return Command.jdeps()
        .with("--check", local.name())
        .with("--multi-release", "BASE")
        .with("--module-path", List.of(folders.workspace("modules"), folders.externalModules()));
  }

  default Javadoc buildProjectMainJavadoc(Path destination) {
    var project = bach().project();
    var main = project.spaces().main();
    return Command.javadoc()
        .with("--module", main.declarations().toNames(","))
        .forEach(
            main.declarations().toModuleSourcePaths(false),
            (javadoc, path) -> javadoc.with("--module-source-path", path))
        .ifPresent(
            main.modulePaths().pruned(),
            (javadoc, paths) -> javadoc.with("--module-path", paths))
        .ifTrue(bach().is(Flag.STRICT), javadoc -> javadoc.with("-Xdoclint").with("-Werror"))
        .withAll(main.tweaks().arguments("javadoc"))
        .with("-d", destination);
  }

  /** {@return the jar call generating the API documentation archive} */
  default Jar buildProjectMainJavadocJar(Path api) {
    var project = bach().project();
    var file = project.name() + "-api-" + project.version() + ".zip";
    return Command.jar()
        .with("--create")
        .with("--file", api.getParent().resolve(file))
        .with("--no-manifest")
        .with("-C", api, ".");
  }

  default JLink buildProjectMainJLink(Path image) {
    var project = bach().project();
    var main = project.spaces().main();
    var test = project.spaces().test();
    return Command.jlink()
        .ifPresent(main.launcher(), (jlink, launcher) -> jlink.with("--launcher", launcher.value()))
        .with("--add-modules", main.declarations().toNames(","))
        .ifPresent(test.modulePaths().pruned(), (jlink, paths) -> jlink.with("--module-path", paths))
        .withAll(main.tweaks().arguments("jlink"))
        .with("--output", image);
  }
}
