/*
 * Bach - Java Shell Builder
 * Copyright (C) 2017 Christian Stein
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

class Build {

  private static final Path TARGET = Paths.get("target", "build");
  private static final Path CLASSES = TARGET.resolve("classes");
  private static final Path JAVADOC = TARGET.resolve("javadoc");
  private static final Path ARTIFACTS = TARGET.resolve("artifacts");

  public static void main(String... args) throws IOException {
    Bach.Builder builder = new Bach.Builder();
    Bach bach = builder.build();

    mainFormat(bach, Paths.get("src"));

    {
      URI uriJupiter = Bach.Util.jcenter("org.junit.jupiter", "junit-jupiter-api", "5.0.0-M4");
      URI uriCommons =
          Bach.Util.jcenter("org.junit.platform", "junit-platform-commons", "1.0.0-M4");
      URI uriOpenTest4J = Bach.Util.jcenter("org.opentest4j", "opentest4j", "1.0.0-M2");
      bach.resolve("org.junit.jupiter.api", uriJupiter);
      bach.resolve("org.junit.platform.commons", uriCommons);
      bach.resolve("org.opentest4j", uriOpenTest4J);
    }
    bach.call("java", "--version");
    bach.call("javac", "-d", CLASSES, "src/main/java/Bach.java");
    bach.execute(
        new Bach.Command("javac")
            .addAll("-d", CLASSES)
            .addAll("--class-path", mainCompileTestsClassPath(bach))
            .mark(1)
            .addAll(Paths.get("src", "test", "java"), Bach.Util::isJavaSourceFile));

    bach.call("javadoc", "-Xdoclint:none", "-d", JAVADOC, "src/main/java/Bach.java");

    Files.createDirectories(ARTIFACTS);
    bach.call(
        "jar", "--create", "--file=" + ARTIFACTS.resolve("bach.jar"), "-C", CLASSES, "Bach.class");
    bach.call(
        "jar",
        "--create",
        "--file=" + ARTIFACTS.resolve("bach-sources.jar"),
        "-C",
        "src/main/java",
        "Bach.java");
    bach.call(
        "jar", "--create", "--file=" + ARTIFACTS.resolve("bach-javadoc.jar"), "-C", JAVADOC, ".");

    mainTestWithJUnitPlatform(bach);
  }

  private static void mainFormat(Bach bach, Path... paths) throws IOException {
    String mode = Boolean.getBoolean("bach.format.replace") ? "replace" : "validate";
    URI uri =
        URI.create(
            "https://jitpack.io/com/"
                + "github/sormuras/google-java-format/"
                + "google-java-format/validate-SNAPSHOT/"
                + "google-java-format-validate-SNAPSHOT-all-deps.jar");
    Path jar =
        Bach.Util.download(
            uri, bach.path(Bach.Folder.TOOLS).resolve("google-java-format-validate"));
    Bach.Command command =
        new Bach.Command(bach.path(Bach.Folder.JDK_HOME).resolve("bin/java"))
            .add("-jar")
            .add(jar)
            .add("--" + mode)
            .mark(10);
    for (Path path : paths) {
      Files.walk(path)
          .filter(Bach.Util::isJavaSourceFile)
          .map(Path::toString)
          .forEach(command::add);
      bach.execute(command);
    }
  }

  private static String mainCompileTestsClassPath(Bach bach) throws IOException {
    List<String> entries = new ArrayList<>();
    entries.add(CLASSES.toString());
    Files.walk(bach.path(Bach.Folder.DEPENDENCIES))
        .filter(Bach.Util::isJarFile)
        .map(Object::toString)
        .forEach(entries::add);
    return String.join(File.pathSeparator, entries);
  }

  private static void mainTestWithJUnitPlatform(Bach bach) {
    String artifact = "junit-platform-console-standalone";
    URI uri = Bach.Util.jcenter("org.junit.platform", artifact, "1.0.0-M4");
    Path jar = Bach.Util.download(uri, bach.path(Bach.Folder.TOOLS).resolve(artifact));
    Bach.Command command = new Bach.Command(bach.path(Bach.Folder.JDK_HOME).resolve("bin/java"));
    command.add("-ea");
    command.add("-jar");
    command.add(jar);
    command.add("--scan-classpath");
    command.add(CLASSES);
    command.add("--class-path");
    command.add(CLASSES);
    if (bach.execute(command) != 0) {
      throw new AssertionError("test run failed");
    }
  }
}
