package com.github.sormuras.bach.internal;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Opens;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Modules {

  public static TreeSet<String> declared(ModuleFinder finder) {
    return declared(finder.findAll().stream().map(ModuleReference::descriptor));
  }

  public static TreeSet<String> declared(Stream<ModuleDescriptor> descriptors) {
    return descriptors.map(ModuleDescriptor::name).collect(Collectors.toCollection(TreeSet::new));
  }

  public static TreeSet<String> required(ModuleFinder finder) {
    return required(finder.findAll().stream().map(ModuleReference::descriptor));
  }

  public static TreeSet<String> required(Stream<ModuleDescriptor> descriptors) {
    return descriptors
        .map(ModuleDescriptor::requires)
        .flatMap(Set::stream)
        .filter(requires -> !requires.modifiers().contains(Requires.Modifier.MANDATED))
        .filter(requires -> !requires.modifiers().contains(Requires.Modifier.STATIC))
        .filter(requires -> !requires.modifiers().contains(Requires.Modifier.SYNTHETIC))
        .map(Requires::name)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  // https://github.com/openjdk/jdk/blob/80380d51d279852f4a24ebbd384921106611bc0c/src/java.base/share/classes/sun/launcher/LauncherHelper.java#L1105
  public static String describe(ModuleReference mref) {
    var md = mref.descriptor();
    var writer = new StringWriter();
    var out = new PrintWriter(writer);

    // one-line summary
    out.print(md.toNameAndVersion());
    mref.location().filter(uri -> !isJrt(uri)).ifPresent(uri -> out.format(" %s", uri));
    if (md.isOpen()) out.print(" open");
    if (md.isAutomatic()) out.print(" automatic");
    out.println();

    // unqualified exports (sorted by package)
    md.exports().stream()
        .filter(e -> !e.isQualified())
        .sorted(Comparator.comparing(Exports::source))
        .forEach(e -> out.format("exports %s%n", toString(e.source(), e.modifiers())));

    // dependences (sorted by name)
    md.requires().stream()
        .sorted(Comparator.comparing(Requires::name))
        .forEach(r -> out.format("requires %s%n", toString(r.name(), r.modifiers())));

    // service use and provides (sorted by name)
    md.uses().stream().sorted().forEach(s -> out.format("uses %s%n", s));
    md.provides().stream()
        .sorted(Comparator.comparing(Provides::service))
        .forEach(
            ps -> {
              var names = String.join("\n", new TreeSet<>(ps.providers()));
              out.format("provides %s with%n%s", ps.service(), names.indent(2));
            });

    // qualified exports (sorted by package)
    md.exports().stream()
        .filter(Exports::isQualified)
        .sorted(Comparator.comparing(Exports::source))
        .forEach(
            e -> {
              var who = String.join("\n", new TreeSet<>(e.targets()));
              out.format("qualified exports %s to%n%s", e.source(), who.indent(2));
            });

    // open packages (sorted by package)
    md.opens().stream()
        .sorted(Comparator.comparing(Opens::source))
        .forEach(
            opens -> {
              if (opens.isQualified()) out.print("qualified ");
              out.format("opens %s", toString(opens.source(), opens.modifiers()));
              if (opens.isQualified()) {
                var who = String.join("\n", new TreeSet<>(opens.targets()));
                out.format(" to%n%s", who.indent(2));
              } else out.println();
            });

    // non-exported/non-open packages (sorted by name)
    var concealed = new TreeSet<>(md.packages());
    md.exports().stream().map(Exports::source).forEach(concealed::remove);
    md.opens().stream().map(Opens::source).forEach(concealed::remove);
    concealed.forEach(p -> out.format("contains %s%n", p));

    return writer.toString();
  }

  private static <T> String toString(String name, Set<T> modifiers) {
    var strings = modifiers.stream().map(e -> e.toString().toLowerCase());
    return Stream.concat(Stream.of(name), strings).collect(Collectors.joining(" "));
  }

  private static boolean isJrt(URI uri) {
    return (uri != null && uri.getScheme().equalsIgnoreCase("jrt"));
  }

  /** Hidden default constructor. */
  private Modules() {}
}
