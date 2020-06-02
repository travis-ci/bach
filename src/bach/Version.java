/*
 * Bach - Java Shell Builder
 * Copyright (C) 2020 Christian Stein
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

// default package

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** Set various version values to the one of this program's first argument. */
public class Version {

  public static void main(String... args) throws Exception {
    if (args.length > 1) throw new Error("Only new version expected: " + List.of(args));

    // IN
    var bach = Path.of("src/de.sormuras.bach/main/java/de/sormuras/bach/Bach.java");

    // OUT
    var readmeMd = Path.of("README.md");
    var pomXml = Path.of("src/de.sormuras.bach/main/maven/pom.xml");
    // var bachJava = Path.of("src/bach/Bach.java");
    // var buildJava = Path.of("src/bach/Build.java");
    // var bachInitJsh = Path.of("src/bach/Init.jsh");
    // var bachBuildJsh = Path.of("src/bach/build.jsh");

    var pattern = Pattern.compile("Version VERSION = Version.parse\\(\"(.+)\"\\);");
    var matcher = pattern.matcher(Files.readString(bach));
    if (!matcher.find()) throw new Error("Version constant not found in: " + bach);

    var current = matcher.group(1);
    // Only print current version?
    if (args.length == 0) {
      System.out.println(current);
      return;
    }
    // Set new version
    var version = args[0];
    // var masterOrVersion = version.endsWith("-ea") ? "master" : version;

    sed(bach, pattern.pattern(), "Version VERSION = Version.parse(\"" + version + "\");");
    sed(readmeMd, "# Bach.java .+ -", "# Bach.java " + version + " -");
    sed(pomXml, "<version>.+</version>", "<version>" + version + "</version>");
    // sed(bachJava, "String VERSION = \".+\";", "String VERSION = \"" + version + "\";");
    // sed(buildJava, "String VERSION = \".+\";", "String VERSION = \"" + version + "\";");
    // sed(bachInitJsh, "String VERSION = \".+\"", "String VERSION = \"" + masterOrVersion + '"');
    // sed(bachBuildJsh, "raw/.+/src", "raw/" + masterOrVersion + "/src");
  }

  private static void sed(Path path, String regex, String replacement) throws Exception {
    Files.writeString(path, Files.readString(path).replaceAll(regex, replacement));
  }
}