import static com.github.sormuras.bach.Note.caption;

import com.github.sormuras.bach.Bach;
import com.github.sormuras.bach.Call;
import com.github.sormuras.bach.ToolFinder;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;

class build {
  public static void main(String... args) {
    try (var bach = new Bach(args)) {
      bach.log(caption("Compile Main Space"));
      var mainModules = compileMainModules(bach);
      bach.run(Call.module(ModuleFinder.of(mainModules), "com.greetings"))
          .visit(run -> bach.log(run.output()));

      bach.log(caption("Compile Test Space"));
      var testModules = compileTestModules(bach);
      var finder = ModuleFinder.of(testModules, mainModules);

      bach.log(caption("Execute Tests"));
      bach.run(Call.module(finder, "test.modules", 1, 2, 3));
      bach.run(Call.tool(ToolFinder.of(finder, true, "test.modules"), "test", 4, 5, 6));
    }
  }

  private static Path compileMainModules(Bach bach) {
    var classes = Path.of(".bach/workspace/classes");
    var modules = Path.of(".bach/workspace/modules");
    bach.run(
        Call.tool("javac")
            .with("--module", "com.greetings,org.astro")
            .with("--module-source-path", "./*/main/java")
            .with("-d", classes));
    bach.run(Call.tool("directories", "clean", modules));
    bach.runParallel(
        Call.tool("jar")
            .with("--create")
            .with("--file", modules.resolve("com.greetings@99.jar"))
            .with("--module-version", "99")
            .with("--main-class", "com.greetings.Main")
            .with("-C", classes.resolve("com.greetings"), "."),
        Call.tool("jar")
            .with("--create")
            .with("--file", modules.resolve("org.astro@99.jar"))
            .with("--module-version", "99")
            .with("-C", classes.resolve("org.astro"), "."));
    return modules;
  }

  private static Path compileTestModules(Bach bach) {
    var classes = Path.of(".bach/workspace/test-classes");
    var modules = Path.of(".bach/workspace/test-modules");
    bach.run(
        Call.tool("javac")
            .with("--module", "test.modules")
            .with("--module-source-path", "./*/test/java")
            .with("--module-path", ".bach/workspace/modules")
            .with("-d", ".bach/workspace/test-classes"));
    bach.run(Call.tool("directories", "clean", modules));
    bach.run(
        Call.tool("jar")
            .with("--create")
            .with("--file", modules.resolve("test.modules@99+test.jar"))
            .with("--module-version", "99+test")
            .with("--main-class", "test.modules.Main")
            .with("-C", classes.resolve("test.modules"), "."));
    return modules;
  }
}