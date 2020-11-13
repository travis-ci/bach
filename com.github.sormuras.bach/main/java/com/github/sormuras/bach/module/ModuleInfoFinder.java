package com.github.sormuras.bach.module;

import com.github.sormuras.bach.internal.Modules;
import com.github.sormuras.bach.internal.Paths;
import java.io.File;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * A module declaration finder backed by {@code module-info.java} compilation units.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jls/se9/html/jls-7.html#jls-7.7">Module
 *     Declarations</a>
 */
public final class ModuleInfoFinder implements ModuleFinder {

  /**
   * @param directory the directory to walk
   * @param moduleSourcePaths the module source paths to apply
   * @return a module declaration finder
   */
  public static ModuleInfoFinder of(Path directory, String... moduleSourcePaths) {
    var absolute = directory.normalize().toAbsolutePath();
    var empty = directory.normalize().toString().isEmpty() || absolute.equals(Paths.CWD);
    var prefix = empty ? "" : absolute + File.separator;

    var paths = new ArrayList<String>();
    var map = new TreeMap<String, ModuleReference>();
    for (var segment : moduleSourcePaths) {
      var asterisk = Path.of(prefix + segment.replace("*", "ASTERISK")).normalize();
      paths.add(asterisk.toString().replace("ASTERISK", "*"));
      var glob = new StringBuilder();
      glob.append(segment);
      if (segment.indexOf('*') < 0) glob.append("/*");
      if (!glob.toString().endsWith("/")) glob.append("/");
      glob.append("module-info.java");
      Paths.find(
          directory,
          glob.toString(),
          info -> {
            var reference = ModuleInfoReference.of(info);
            map.put(reference.descriptor().name(), reference);
          });
    }
    return new ModuleInfoFinder(List.copyOf(paths), Map.copyOf(map));
  }

  /**
   * @param references the module info references to map and find
   * @return a module declaration finder
   */
  public static ModuleInfoFinder of(ModuleReference... references) {
    var paths = new ArrayList<String>();
    var map = new TreeMap<String, ModuleReference>();
    var currentUserDirectory = Path.of("").toAbsolutePath();
    for (var reference : references) {
      var name = reference.descriptor().name();
      var path = Path.of(reference.location().orElseThrow()).getParent();
      paths.add(name + "=" + currentUserDirectory.relativize(path));
      map.put(name, reference);
    }
    return new ModuleInfoFinder(List.copyOf(paths), Map.copyOf(map));
  }

  private final List<String> moduleSourcePaths;
  private final Map<String, ModuleReference> references;
  private final Set<ModuleReference> values;

  ModuleInfoFinder(List<String> moduleSourcePaths, Map<String, ModuleReference> references) {
    this.moduleSourcePaths = moduleSourcePaths;
    this.references = references;
    this.values = Set.copyOf(references.values());
  }

  /** @return the configured module source path patterns */
  public List<String> moduleSourcePaths() {
    return moduleSourcePaths;
  }

  @Override
  public Optional<ModuleReference> find(String name) {
    return Optional.ofNullable(references.get(name));
  }

  @Override
  public Set<ModuleReference> findAll() {
    return values;
  }

  /** @return a set of module names */
  public Set<String> declared() {
    return Modules.declared(this);
  }

  /** @return a set of module names */
  public Set<String> required() {
    return Modules.required(this);
  }
}
