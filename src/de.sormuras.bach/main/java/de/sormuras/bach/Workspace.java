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

package de.sormuras.bach;

import de.sormuras.bach.project.structure.Realm;
import de.sormuras.bach.project.structure.Unit;
import java.nio.file.Path;
import java.util.Optional;
import java.util.StringJoiner;

/** Well-known paths. */
public /*static*/ final class Workspace {

  public static Workspace of() {
    return of(Path.of(""));
  }

  public static Workspace of(Path base) {
    return new Workspace(base, base.resolve(".bach/workspace"));
  }

  private final Path base;
  private final Path workspace;

  public Workspace(Path base, Path workspace) {
    this.base = base;
    this.workspace = workspace;
  }

  public Path base() {
    return base;
  }

  public Path workspace() {
    return workspace;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", Workspace.class.getSimpleName() + "[", "]")
        .add("base=" + base)
        .add("workspace=" + workspace)
        .toString();
  }

  public Path workspace(String first, String... more) {
    return workspace.resolve(Path.of(first, more));
  }

  public Path classes(Realm realm) {
    return classes(realm, realm.release());
  }

  public Path classes(Realm realm, int release) {
    var version = String.valueOf(release == 0 ? Runtime.version().feature() : release);
    return workspace.resolve("classes").resolve(realm.name()).resolve(version);
  }

  public Path modules(Realm realm) {
    return workspace.resolve("modules").resolve(realm.name());
  }

  public String jarFileName(Project project, Unit unit, String classifier) {
    var unitVersion = unit.descriptor().version();
    var moduleVersion = unitVersion.isPresent() ? unitVersion : Optional.ofNullable(project.version());
    var versionSuffix = moduleVersion.map(v -> "-" + v).orElse("");
    var classifierSuffix = classifier.isEmpty() ? "" : "-" + classifier;
    return unit.name() + versionSuffix + classifierSuffix + ".jar";
  }

  public Path jarFilePath(Project project, Realm realm, Unit unit) {
    return modules(realm).resolve(jarFileName(project, unit, ""));
  }
}