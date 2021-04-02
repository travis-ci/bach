package test.integration.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

import com.github.sormuras.bach.Bach;
import com.github.sormuras.bach.Options;
import com.github.sormuras.bach.project.LocalModule;
import com.github.sormuras.bach.project.LocalModules;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ModuleDeclarationsTests {

  private static final Bach BACH = new Bach(Options.of());

  private static LocalModule newModuleDeclaration(Path path) {
    return newModuleDeclaration(Path.of(""), path);
  }

  private static LocalModule newModuleDeclaration(Path root, Path path) {
    return BACH.computeProjectLocalModule(root, path, false);
  }

  @Nested
  class BachProject {
    @Test
    void checkModulePatches() {
      var module = "com.github.sormuras.bach";
      var main =
          new LocalModules(
              Map.of(module, newModuleDeclaration(Path.of(module, "main", "java"))));
      var test =
          new LocalModules(
              Map.of(module, newModuleDeclaration(Path.of(module, "test", "java-module"))));

      var expected = Map.of(module, Path.of(module, "main", "java").toString());
      assertEquals(expected, test.toModulePatches(main));
    }
  }

  @Nested
  class TestProjectJigsawQuickStartWorld {

    static Path root = Path.of("test.projects", "JigsawQuickStartWorld");
    static LocalModules declarations =
        new LocalModules(
            Map.of(
                "com.greetings",
                newModuleDeclaration(root, root.resolve("com.greetings")),
                "org.astro",
                newModuleDeclaration(root, root.resolve("org.astro"))));

    @Test
    void checkNames() {
      assertEquals("com.greetings,org.astro", declarations.toNames(","));
    }

    @Test
    void checkModuleSourcePathsInModuleSpecificForm() {
      var expected =
          List.of(
              "com.greetings=" + root.resolve("com.greetings"),
              "org.astro=" + root.resolve("org.astro"));
      assertLinesMatch(expected, declarations.toModuleSourcePaths(true));
    }

    @Test
    void checkModuleSourcePathsInModulePatternForm() {
      assertLinesMatch(List.of(root.toString()), declarations.toModuleSourcePaths(false));
    }
  }
}
