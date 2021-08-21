import com.github.sormuras.bach.Bach;
import com.github.sormuras.bach.Call;
import com.github.sormuras.bach.ModuleLocators;
import com.github.sormuras.bach.ToolFinder;
import com.github.sormuras.bach.external.JUnit;
import java.io.File;
import java.lang.module.ModuleDescriptor.Version;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class build {

  public static void main(String... args) {
    System.setProperty("java.util.logging.config.file", ".bach/logging.properties");
    System.out.println("BEGIN");
    try (var bach = new Bach(args)) {
      var version = version(bach);

      bach.log("CAPTION:Grab required and missing external modules");
      var grabber = bach.grabber();
      var locators = locators();
      grabber.grabExternalModules(
          locators, "org.junit.jupiter", "org.junit.platform.console", "org.junit.platform.jfr");
      grabber.grabMissingExternalModules(locators);

      bach.log("CAPTION:Grab external tools");
      grabber.grab(".bach/external.properties");

      bach.log("CAPTION:Build main code space");
      var mainModules = buildMainModules(bach, version);

      bach.log("CAPTION:Build test code space");
      var testModules = buildTestModules(bach, version, mainModules);

      executeTests(bach, "com.github.sormuras.bach", mainModules, testModules);
      executeTests(bach, "test.base", mainModules, testModules);
      executeTests(bach, "test.integration", mainModules, testModules);
      executeTests(bach, "test.projects", mainModules, testModules);
    }
    System.out.println("END.");
  }

  static ModuleLocators locators() {
    return ModuleLocators.of(build::locate, JUnit.version("5.8.0-RC1"));
  }

  static String locate(String module) {
    return switch (module) {
      case "org.apiguardian.api" -> "https://repo.maven.apache.org/maven2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar#SIZE=6806";
      case "org.junit.jupiter" -> "https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter/5.8.0-RC1/junit-jupiter-5.8.0-RC1.jar#SIZE=6374";
      case "org.junit.jupiter.api" -> "https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-api/5.8.0-RC1/junit-jupiter-api-5.8.0-RC1.jar#SIZE=192751";
      case "org.junit.jupiter.engine" -> "https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-engine/5.8.0-RC1/junit-jupiter-engine-5.8.0-RC1.jar#SIZE=224308";
      case "org.junit.jupiter.params" -> "https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-params/5.8.0-RC1/junit-jupiter-params-5.8.0-RC1.jar#SIZE=575548";
      case "org.junit.platform.commons" -> "https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-commons/1.8.0-RC1/junit-platform-commons-1.8.0-RC1.jar#SIZE=100449";
      case "org.junit.platform.console" -> "https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console/1.8.0-RC1/junit-platform-console-1.8.0-RC1.jar#SIZE=488177";
      case "org.junit.platform.engine" -> "https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-engine/1.8.0-RC1/junit-platform-engine-1.8.0-RC1.jar#SIZE=185793";
      case "org.junit.platform.launcher" -> "https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-launcher/1.8.0-RC1/junit-platform-launcher-1.8.0-RC1.jar#SIZE=159616";
      case "org.junit.platform.reporting" -> "https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-reporting/1.8.0-RC1/junit-platform-reporting-1.8.0-RC1.jar#SIZE=26190";
      case "org.opentest4j" -> "https://repo.maven.apache.org/maven2/org/opentest4j/opentest4j/1.2.0/opentest4j-1.2.0.jar#SIZE=7653";
      default -> null;
    };
  }

  static Version version(Bach bach) {
    var version = bach.configuration().projectOptions().version();
    if (version.isPresent()) return version.get();
    var file = Path.of("VERSION");
    try {
      return Version.parse(Files.readString(file) + "-ea");
    } catch (Exception exception) {
      throw new RuntimeException("Reading VERSION file failed: " + file.toUri(), exception);
    }
  }

  static Path buildMainModules(Bach bach, Version version) {
    var names = List.of("com.github.sormuras.bach");
    var classes = Path.of(".bach/workspace/classes");
    bach.run(
        Call.tool("javac")
            .with("--release", "17")
            .with("--module", String.join(",", names))
            .with("--module-source-path", "./*/main/java")
            .with("-g")
            .with("-parameters")
            .with("-Werror")
            .with("-Xlint")
            .with("-encoding", "UTF-8")
            .with("-d", classes));
    var modules = Path.of(".bach/workspace/modules");
    bach.run(Call.tool("directories", "create", modules));
    for (var name : names) {
      var tag = version.toString().split("\\+")[0];
      var file = name + "@" + tag + ".jar";
      bach.run(
          Call.tool("jar")
              .with("--verbose")
              .with("--create")
              .with("--file", modules.resolve(file))
              .with("--module-version", version)
              .with("-C", classes.resolve(name), ".")
              .with("-C", Path.of(name).resolve("main/java"), "."));
    }
    return modules;
  }

  static Path buildTestModules(Bach bach, Version version, Path mainModules) {
    var names =
        List.of("test.base", "test.integration", "test.projects", "com.github.sormuras.bach");
    var mainClasses = Path.of(".bach/workspace/classes");
    var testClasses = Path.of(".bach/workspace/test-classes");
    bach.run(
        Call.tool("javac")
            .with("--module", String.join(",", names))
            .with(
                "--module-source-path",
                String.join(File.pathSeparator, "./*/test/java", "./*/test/java-module"))
            .with("--module-path", List.of(mainModules, bach.path().externalModules()))
            .with(
                "--patch-module",
                "com.github.sormuras.bach=" + mainClasses.resolve("com.github.sormuras.bach"))
            .with("-g")
            .with("-parameters")
            .with("-Werror")
            .with("-Xlint")
            .with("-encoding", "UTF-8")
            .with("-d", testClasses));
    var modules = Path.of(".bach/workspace/test-modules");
    bach.run(Call.tool("directories", "create", modules));
    for (var name : names) {
      var file = name + "@" + version + "+test.jar";
      var jar =
          Call.tool("jar")
              .with("--create")
              .with("--file", modules.resolve(file))
              .with("--module-version", version + "+test")
              .with("-C", testClasses.resolve(name), ".");
      if (name.equals("com.github.sormuras.bach")) {
        jar = jar.with("-C", mainClasses.resolve("com.github.sormuras.bach"), ".");
      }
      var resources = Path.of(name, "test", "resources");
      if (Files.isDirectory(resources)) {
        jar = jar.with("-C", resources, ".");
      }
      bach.run(jar);
    }
    return modules;
  }

  static void executeTests(Bach bach, String module, Path mainModules, Path testModules) {
    bach.log("CAPTION:Execute tests of module " + module);
    var moduleFinder =
        ModuleFinder.of(
            testModules.resolve(module + "@" + version(bach) + "+test.jar"),
            mainModules,
            testModules,
            bach.path().externalModules());
    var toolFinder = ToolFinder.of(moduleFinder, true, module);
    bach.run(
        Call.tool(toolFinder, "junit")
            .with("--select-module", module)
            .with("--reports-dir", Path.of(".bach/workspace/test-reports/junit-" + module)));
  }
}
