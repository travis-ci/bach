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

package de.sormuras.bach.project;

import de.sormuras.bach.util.Paths;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public /*record*/ class Realm {

  public static final String ALL_MODULES = "ALL-REALM";
  public static final String JAVADOC_MODULES_OPTION = "javadoc --module";

  public enum Modifier {
    MAIN,
    TEST,
    PREVIEW
  }

  public static Map<String, List<String>> defaultArgumentsFor(String realm) {
    if (!"test".equals(realm)) {
      return Map.of(
          "javac",
          List.of("-encoding", "UTF-8", "-parameters", "-Werror", "-X" + "lint"),
          "javadoc",
          List.of("-encoding", "UTF-8", "-locale", "en", "-Xdoclint:-missing"),
          Realm.JAVADOC_MODULES_OPTION,
          List.of(ALL_MODULES));
    }
    return Map.of( // Test realm is special
        "javac", List.of("-encoding", "UTF-8", "-parameters", "-Werror", "-X" + "lint:-preview"),
        "junit", List.of());
  }

  private final String name;
  private final Set<Modifier> modifiers;
  private final int release;
  private final List<Path> sourcePaths;
  private final List<Path> modulePaths;
  private final Map<String, List<String>> argumentsFor;

  public Realm(
      String name,
      Set<Modifier> modifiers,
      int release,
      List<Path> sourcePaths,
      List<Path> modulePaths,
      Map<String, List<String>> argumentsFor) {
    this.name = name;
    this.modifiers = modifiers.isEmpty() ? Set.of() : EnumSet.copyOf(modifiers);
    this.release = release;
    this.sourcePaths = List.copyOf(sourcePaths);
    this.modulePaths = List.copyOf(modulePaths);
    this.argumentsFor = Map.copyOf(argumentsFor);
  }

  public String name() {
    return name;
  }

  public Set<Modifier> modifiers() {
    return modifiers;
  }

  public Optional<Integer> release() {
    return release == 0 ? Optional.empty() : Optional.of(release);
  }

  public boolean isMainRealm() {
    return modifiers.contains(Modifier.MAIN);
  }

  public boolean isTestRealm() {
    return modifiers.contains(Modifier.TEST);
  }

  public boolean isPreviewRealm() {
    return modifiers.contains(Modifier.PREVIEW);
  }

  public List<Path> sourcePaths() {
    return sourcePaths;
  }

  public List<Path> modulePaths() {
    return modulePaths;
  }

  public String moduleSourcePath() {
    return Paths.join(sourcePaths).replace("{MODULE}", "*");
  }

  public Map<String, List<String>> argumentsFor() {
    return argumentsFor;
  }

  public List<String> argumentsFor(String tool) {
    return argumentsFor.getOrDefault(tool, List.of());
  }

  @Override
  public String toString() {
    return String.format("Realm{name=%s, modifiers=%s}", name, modifiers);
  }
}
