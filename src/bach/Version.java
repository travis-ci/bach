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
    var readmeMd = Path.of("README.md");
    var bachJava = Path.of("src/bach/Bach.java");
    var buildJava = Path.of("src/bach/Build.java");
    var bachInitJsh = Path.of("src/bach/Init.jsh");
    var bachBuildJsh = Path.of("src/bach/build.jsh");

    var matcher = Pattern.compile("String VERSION = \"(.+)\";").matcher(Files.readString(buildJava));
    if (!matcher.find()) {
      throw new Error("Version constant not found in: " + buildJava);
    }
    var current = matcher.group(1);
    // Only print current version?
    if (args.length == 0) {
      System.out.println(current);
      return;
    }
    // Set new version
    if (args.length > 1) {
      throw new Error("Exactly one argument, the new version, expected! args=" + List.of(args));
    }
    var version = args[0];
    if (version.equals(current)) {
      System.out.println("Same version already set: " + current);
      return;
    }
    var masterOrVersion = version.endsWith("-ea") ? "master" : version;

    sed(readmeMd, "# Bach.java .+ -", "# Bach.java " + version + " -");
    sed(bachJava, "String VERSION = \".+\";", "String VERSION = \"" + version + "\";");
    sed(buildJava, "String VERSION = \".+\";", "String VERSION = \"" + version + "\";");
    sed(bachInitJsh, "String VERSION = \".+\"", "String VERSION = \"" + masterOrVersion + '"');
    sed(bachBuildJsh, "raw/.+/src", "raw/" + masterOrVersion + "/src");
  }

  private static void sed(Path path, String regex, String replacement) throws Exception {
    Files.writeString(path, Files.readString(path).replaceAll(regex, replacement));
  }
}
