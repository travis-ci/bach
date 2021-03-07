package com.github.sormuras.bach.api;

import com.github.sormuras.bach.Bach;
import com.github.sormuras.bach.Command;
import com.github.sormuras.bach.Options;
import com.github.sormuras.bach.internal.Strings;
import com.github.sormuras.bach.project.ModuleDeclaration;
import com.github.sormuras.bach.project.SourceFolder;
import com.github.sormuras.bach.tool.Jar;
import com.github.sormuras.bach.tool.Javac;
import com.github.sormuras.bach.util.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

/** Methods related to building projects. */
public interface ProjectBuilderAPI {

  Bach bach();

  default void buildProject() throws Exception {
    var bach = bach();
    var project = bach.project();
    bach.print("Build %s %s", project.name(), project.version());
    if (bach.is(Options.Flag.VERBOSE)) bach.info();
    var start = Instant.now();
    if (bach.is(Options.Flag.STRICT)) bach.formatJavaSourceFiles(JavaFormatterAPI.Mode.VERIFY);
    bach.loadMissingExternalModules();
    bach.verifyExternalModules();
    buildProjectMainSpace();
    buildProjectTestSpace();
    bach.print("Build took %s", Strings.toString(Duration.between(start, Instant.now())));
  }

  default void buildProjectMainSpace() throws Exception {
    var main = bach().project().spaces().main();
    var modules = main.declarations();
    if (modules.isEmpty()) {
      bach().debug("Main module list is empty, nothing to build here.");
      return;
    }
    var s = modules.size() == 1 ? "" : "s";
    bach().print("Build %d main module%s: %s", modules.size(), s, modules.toNames(", "));

    var release = main.release();
    var feature = release != 0 ? release : Runtime.version().feature();
    var classes = bach().folders().workspace("classes", main.name(), String.valueOf(feature));

    var workspaceModules = bach().folders().workspace("modules");
    Paths.deleteDirectories(workspaceModules);
    bach().run(buildProjectMainJavac(release, classes)).requireSuccessful();

    Files.createDirectories(workspaceModules);
    var jars = new ArrayList<Jar>();
    var javacs = new ArrayList<Javac>();
    for (var declaration : modules.map().values()) {
      for (var folder : declaration.sources().list()) {
        if (!folder.isTargeted()) continue;
        javacs.add(computeMainJavacCall(declaration, folder));
      }
      jars.add(buildProjectMainJar(declaration, classes));
    }
    if (!javacs.isEmpty()) bach().run(javacs.stream()).requireSuccessful();
    bach().run(jars.stream()).requireSuccessful();
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
        .ifTrue(release != 0, javac -> javac.add("--release", release))
        .add("--module", main.declarations().toNames(","))
        .add("--module-version", project.version())
        .forEach(
            main.declarations().toModuleSourcePaths(false),
            (javac, path) -> javac.add("--module-source-path", path))
        .ifPresent(main.modulePaths().pruned(), (javac, paths) -> javac.add("--module-path", paths))
        .ifTrue(bach().is(Options.Flag.STRICT), javac -> javac.add("-Xlint").add("-Werror"))
        .addAll(main.tweaks().arguments("javac"))
        .add("-d", classes);
  }

  /** {@return the {@code javac} call to compile a specific version of a multi-release module} */
  default Javac computeMainJavacCall(ModuleDeclaration declaration, SourceFolder folder) {
    var name = declaration.name();
    var project = bach().project();
    var main = project.spaces().main();
    var release = folder.release();
    var javaSourceFiles = Paths.find(folder.path(), 99, Paths::isJavaFile);
    return Command.javac()
        .add("--release", release)
        .add("--module-version", project.version())
        .ifPresent(main.modulePaths().pruned(), (javac, paths) -> javac.add("--module-path", paths))
        .add("-implicit:none") // generate classes for explicitly referenced source files
        .addAll(main.tweaks().arguments("javac"))
        .addAll(main.tweaks().arguments("javac(" + name + ")"))
        .addAll(main.tweaks().arguments("javac(" + release + ")"))
        .addAll(main.tweaks().arguments("javac(" + name + "@" + release + ")"))
        .add("-d", buildProjectMultiReleaseClasses(name, release))
        .addAll(javaSourceFiles);
  }

  default Jar buildProjectMainJar(ModuleDeclaration declaration, Path classes) {
    var project = bach().project();
    var main = project.spaces().main();
    var name = declaration.name();
    var file = bach().folders().workspace("modules", buildProjectJarFileName(name));
    var jar =
        Command.jar()
            .ifTrue(bach().is(Options.Flag.VERBOSE), command -> command.add("--verbose"))
            .add("--create")
            .add("--file", file)
            .addAll(main.tweaks().arguments("jar"))
            .addAll(main.tweaks().arguments("jar(" + name + ")"));
    var baseClasses = classes.resolve(name);
    if (Files.isDirectory(baseClasses)) jar = jar.add("-C", baseClasses, ".");
    // include base resources
    for (var folder : declaration.resources().list()) {
      if (folder.isTargeted()) continue; // handled later
      jar = jar.add("-C", folder.path(), ".");
    }
    // add targeted classes and targeted resources in ascending order
    for (int release = 9; release <= Runtime.version().feature(); release++) {
      var paths = new ArrayList<Path>();
      var pathN = buildProjectMultiReleaseClasses(name, release);
      if (Files.isDirectory(pathN))
        declaration.sources().targets(release).ifPresent(it -> paths.add(pathN));
      declaration.resources().targets(release).ifPresent(it -> paths.add(it.path()));
      if (paths.isEmpty()) continue;
      jar = jar.add("--release", release);
      for (var path : paths) jar = jar.add("-C", path, ".");
    }
    return jar;
  }

  private Path buildProjectMultiReleaseClasses(String module, int release) {
    return bach().folders().workspace("classes-mr", String.valueOf(release), module);
  }

  default String buildProjectJarFileName(String module) {
    return module + '@' + bach().project().versionNumberAndPreRelease() + ".jar";
  }

  default void buildProjectTestSpace() throws Exception {
    var test = bach().project().spaces().test();
    var modules = test.declarations();
    if (modules.isEmpty()) {
      bach().debug("Test module list is empty, nothing to build here.");
      return;
    }
    bach().print("Build %d test module%s", modules.size(), modules.size() == 1 ? "" : "s");

    throw new UnsupportedOperationException("buildProjectTestSpace() needs some love");
  }
}
