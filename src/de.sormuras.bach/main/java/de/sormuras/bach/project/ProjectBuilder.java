/*
 * Bach - Java Shell Builder
 * Copyright (C) 2019 Christian Stein
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

import de.sormuras.bach.Log;
import de.sormuras.bach.project.Unit.Type;
import de.sormuras.bach.util.Modules;
import de.sormuras.bach.util.Paths;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Version;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import javax.lang.model.SourceVersion;

public class ProjectBuilder {

  enum Property {
    NAME("project"),
    VERSION("0");

    final String defaultValue;

    Property(String defaultValue) {
      this.defaultValue = defaultValue;
    }

    String get(Properties properties) {
      return get(properties, defaultValue);
    }

    String get(Properties properties, String defaultValue) {
      return properties.getProperty(name().toLowerCase(), defaultValue);
    }
  }

  private final Log log;

  public ProjectBuilder() {
    this(Log.ofNullWriter());
  }

  public ProjectBuilder(Log log) {
    this.log = log;
  }

  /** Create project instance auto-configured by scanning the current working directory. */
  public Project auto(Path base) {
    return auto(Folder.of(base));
  }

  public Project auto(Folder folder) {
    return auto(folder, properties(folder));
  }

  public Project auto(Folder folder, Properties properties) {
    var directory =
        Optional.ofNullable(folder.base().toAbsolutePath().getFileName())
            .map(Path::toString)
            .orElse(Property.NAME.defaultValue);
    var name = Property.NAME.get(properties, directory);
    var version = Property.VERSION.get(properties);
    return new Project(name, Version.parse(version), structure(folder), null);
  }

  public Properties properties(Folder folder) {
    var file = System.getProperty("project.properties", ".bach/project.properties");
    return Paths.load(new Properties(), folder.base().resolve(file));
  }

  public Structure structure(Folder folder) {
    if (!Files.isDirectory(folder.base())) {
      throw new IllegalArgumentException("Not a directory: " + folder.base());
    }
    var main =
        new Realm(
            "main",
            Set.of(Realm.Modifier.DEPLOY),
            List.of(folder.src("{MODULE}/main/java")),
            List.of(folder.lib()),
            Map.of(
                "javac",
                List.of("-encoding", "UTF-8", "-parameters", "-W" + "error", "-X" + "lint")));
    var test =
        new Realm(
            "test",
            Set.of(Realm.Modifier.TEST),
            List.of(folder.src("{MODULE}/test/java"), folder.src("{MODULE}/test/module")),
            List.of(folder.modules("main"), folder.lib()),
            Map.of(
                "javac",
                List.of(
                    "-encoding", "UTF-8", "-parameters", "-W" + "error", "-X" + "lint:-preview")));
    var realms = List.of(main, test);

    var modules = new TreeMap<String, List<String>>(); // local realm-based module registry
    var units = new ArrayList<Unit>();
    for (var root : Paths.list(folder.src(), Files::isDirectory)) {
      log.debug("root = %s", root);
      var module = root.getFileName().toString();
      if (!SourceVersion.isName(module.replace(".", ""))) continue;
      log.debug("module = %s", module);
      for (var realm : realms) {
        modules.putIfAbsent(realm.name(), new ArrayList<>());
        log.debug("realm.name = %s", realm.name());
        if (Files.isDirectory(root.resolve(realm.name()))) {
          var unit =
              unit(
                  root,
                  realm,
                  () -> {
                    var patches = new ArrayList<Path>();
                    if (realm == test && modules.get("main").contains(module)) {
                      patches.add(folder.src().resolve(module).resolve("main/java"));
                    }
                    return patches;
                  });
          modules.get(realm.name()).add(module);
          units.add(unit);
        }
      }
    }
    return new Structure(folder, Library.of(), realms, units);
  }

  private Path info(Path path) {
    for (var directory : List.of("java", "module")) {
      var info = path.resolve(directory).resolve("module-info.java");
      if (Paths.isJavaFile(info)) return info;
    }
    throw new IllegalArgumentException("Couldn't find module-info.java file in: " + path);
  }

  private Unit unit(Path root, Realm realm, Supplier<List<Path>> patcher) {
    var module = root.getFileName().toString();
    var relative = root.resolve(realm.name()); // realm-relative
    // jigsaw
    if (Files.isDirectory(relative.resolve("java"))) { // no trailing "...-${N}"
      var info = info(relative);
      var descriptor = Modules.describe(Paths.readString(info));
      var sources = List.of(Source.of(relative.resolve("java")));
      var resources = Paths.filterExisting(List.of(root.resolve("resources")));
      var patches = patcher.get();
      log.debug("info = %s", info);
      log.debug("descriptor = %s", descriptor);
      log.debug("sources = %s", sources);
      log.debug("resources = %s", resources);
      log.debug("patches = %s", patches);
      return new Unit(realm, descriptor, info, Type.JIGSAW, sources, resources, patches);
    }
    // multi-release
    if (!Paths.list(relative, "java-*").isEmpty()) {
      Path info = null;
      ModuleDescriptor descriptor = null;
      var sources = new ArrayList<Source>();
      for (int feature = 7; feature <= Runtime.version().feature(); feature++) {
        var sourced = relative.resolve("java-" + feature);
        if (Files.notExists(sourced)) continue; // feature
        log.debug("sourced = %s", sourced);
        sources.add(Source.of(sourced, feature));
        var infoPath = sourced.resolve("module-info.java");
        if (info == null && Paths.isJavaFile(infoPath)) { // select first
          info = infoPath;
          descriptor = Modules.describe(Paths.readString(info));
        }
      }
      var resources = Paths.filterExisting(List.of(root.resolve("resources")));
      var patches = patcher.get();
      log.debug("info = %s", info);
      log.debug("descriptor = %s", descriptor);
      log.debug("sources = %s", sources);
      log.debug("resources = %s", resources);
      log.debug("patches = %s", patches);
      return new Unit(realm, descriptor, info, Type.MULTI_RELEASE, sources, resources, patches);
    }
    throw new IllegalArgumentException("Unknown unit layout: " + module + " <- " + root.toUri());
  }
}
