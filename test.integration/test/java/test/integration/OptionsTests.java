package test.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.sormuras.bach.Logbook;
import com.github.sormuras.bach.Options;
import com.github.sormuras.bach.api.Action;
import com.github.sormuras.bach.api.CodeSpace;
import com.github.sormuras.bach.api.ExternalLibraryName;
import com.github.sormuras.bach.api.ExternalLibraryVersion;
import com.github.sormuras.bach.api.ExternalModuleLocation;
import com.github.sormuras.bach.api.Tweak;
import java.lang.module.ModuleDescriptor.Version;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class OptionsTests {

  @Nested
  class NamesTests {
    @Test
    void toCli() {
      assertEquals("--abc-def-ghi", OptionsLines.toCli("abcDefGhi"));
    }

    @Test
    void toComponentName() {
      assertEquals("abcDefGhi", Options.toComponentName("--abc-def-ghi"));
    }
  }

  @Nested
  class EmptyOptionsTests {
    final Options empty = Options.of();

    @Test
    void emptyInstanceIsCached() {
      assertSame(empty, Options.of());
    }

    @TestFactory
    Stream<DynamicTest> flagsAreFalse() {
      return Stream.of(Options.class.getRecordComponents())
          .filter(component -> component.getType() == boolean.class)
          .map(this::flagIsFalse);
    }

    DynamicTest flagIsFalse(RecordComponent component) {
      return DynamicTest.dynamicTest(
          "%s flag is false".formatted(component.getName()),
          () -> assertFalse((boolean) component.getAccessor().invoke(empty)));
    }
  }

  @Nested
  class ComposeTests {

    @Test
    void compose() {
      var logbook = Logbook.of();
      var options =
          Options.compose(
              "test options",
              logbook,
              Options.of().id("top layer"),
              Options.of().id("1st layer").with("--verbose", true),
              Options.of().id("2nd layer").with("--project-requires", "foo"));

      assertEquals("test options", options.id().orElseThrow());
      assertTrue(options.verbose(), options.toString());
      assertLinesMatch(
          """
          Compose options from 3 layers
          [0] = top layer
          [1] = 1st layer
          [2] = 2nd layer
          >>>>
          [1] verbose -> true
          [2] projectRequires -> [foo]
          """
              .lines(),
          logbook.lines());
    }
  }

  @Nested
  class LinesTests {
    @Test
    void empty() {
      assertLinesMatch("".lines(), new OptionsLines(Options.of()).lines());
    }

    @Test
    void withEverything() {
      var options = new Options(
          Optional.of("ID"),
          true,
          true,
          true,
          true,
          true,
          true,
          true,
          true,
          true,
          Optional.of("TOOL"),
          Optional.of("MODULE"),
          true,
          Optional.empty(), // Optional.of(Command.of("TOOL", "ARGS...")),
          Optional.of(Path.of("PATH")),
          Optional.of("MODULE"),
          Optional.of("NAME"),
          Optional.of(Version.parse("0-ea+VERSION")),
          List.of("M1", "M2"),
          List.of("*", "**"),
          List.of("PATH", "PATH"),
          Optional.of(9),
          true,
          List.of("test", "**/test", "**/test/**"),
          List.of("PATH", "PATH"),
          Optional.of("TOOL"),
          Optional.of("TOOL"),
          List.of(new Tweak(EnumSet.allOf(CodeSpace.class), "TRIGGER", List.of("ARGS..."))),
          List.of(new ExternalModuleLocation("M1", "U1"), new ExternalModuleLocation("M2", "U2")),
          List.of(new ExternalLibraryVersion(ExternalLibraryName.JUNIT, "VERSION")),
          List.copyOf(EnumSet.allOf(Action.class))
      );
      assertLinesMatch(
          """
          --actions
            BUILD
          --actions
            CLEAN
          --actions
            COMPILE_MAIN
          --actions
            COMPILE_TEST
          --actions
            EXECUTE_TESTS
          --actions
            WRITE_LOGBOOK
          --bach-info
            MODULE
          --chroot
            PATH
          --describe-tool
            TOOL
          --dry-run
          --external-library-versions
            JUNIT
            VERSION
          --external-module-locations
            M1
            U1
          --external-module-locations
            M2
            U2
          --help
          --help-extra
          --id
            ID
          --limit-tools
            TOOL
          --list-configuration
          --list-modules
          --list-tools
          --load-external-module
            MODULE
          --load-missing-external-modules
          --main-jar-with-sources
          --main-java-release
            9
          --main-module-paths
            PATH
          --main-module-paths
            PATH
          --main-module-patterns
            *
          --main-module-patterns
            **
          --project-name
            NAME
          --project-requires
            M1
          --project-requires
            M2
          --project-version
            0-ea+VERSION
          --run-commands-sequentially
          --skip-tools
            TOOL
          --test-module-paths
            PATH
          --test-module-paths
            PATH
          --test-module-patterns
            test
          --test-module-patterns
            **/test
          --test-module-patterns
            **/test/**
          --tweaks
            main,test
            TRIGGER
            1
            ARGS...
          --verbose
          --version
          """.lines(),
          new OptionsLines(options).lines());

      var cli = Options.ofCommandLineArguments(new OptionsLines(options).lines().toList());
      assertEquals(options, cli);
      assertEquals(options, cli);
    }
  }
}
