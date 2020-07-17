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

import de.sormuras.bach.Scribe;
import de.sormuras.bach.internal.Factory;
import de.sormuras.bach.internal.Factory.Kind;
import de.sormuras.bach.internal.Modules;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** A source set of {@code main} modules. */
public final class MainSources implements Realm<MainSources>, Scribe {

  /** A modifier on a main source set. */
  public enum Modifier {
    INCLUDE_SOURCES_IN_MODULAR_JAR,
    INCLUDE_RESOURCES_IN_SOURCES_JAR,
    NO_API_DOCUMENTATION,
    NO_CUSTOM_RUNTIME_IMAGE
  }

  private final Set<Modifier> modifiers;
  private final JavaRelease release;
  private final SourceUnitMap units;

  public MainSources(Set<Modifier> modifiers, JavaRelease release, SourceUnitMap units) {
    this.modifiers = modifiers.isEmpty() ? Set.of() : EnumSet.copyOf(modifiers);
    this.release = release;
    this.units = units;
  }

  public Set<Modifier> modifiers() {
    return modifiers;
  }

  public JavaRelease release() {
    return release;
  }

  public SourceUnitMap units() {
    return units;
  }

  //
  // Configuration API
  //

  @Factory
  public static MainSources of() {
    return new MainSources(Set.of(), JavaRelease.ofRuntime(), SourceUnitMap.of());
  }

  @Factory(Kind.SETTER)
  public MainSources modifiers(Set<Modifier> modifiers) {
    return new MainSources(modifiers, release, units);
  }

  @Factory(Kind.SETTER)
  public MainSources release(JavaRelease release) {
    return new MainSources(modifiers, release, units);
  }

  @Factory(Kind.SETTER)
  public MainSources release(int feature) {
    return release(JavaRelease.of(feature));
  }

  @Factory(Kind.SETTER)
  public MainSources units(SourceUnitMap units) {
    return new MainSources(modifiers, release, units);
  }

  @Factory(Kind.OPERATOR)
  public MainSources with(Modifier... moreModifiers) {
    var mergedModifiers = new TreeSet<>(modifiers);
    mergedModifiers.addAll(Set.of(moreModifiers));
    return modifiers(mergedModifiers);
  }

  @Factory(Kind.OPERATOR)
  public MainSources without(Modifier... redundantModifiers) {
    var mergedModifiers = new TreeSet<>(modifiers);
    mergedModifiers.removeAll(Set.of(redundantModifiers));
    return modifiers(mergedModifiers);
  }

  //
  // Normal API
  //

  @Override
  public void scribe(Scroll scroll) {
    scroll.append("MainSources.of()");
    for (var modifier : modifiers) scroll.addNewLine().add(".with", modifier);
    scroll.addNewLine().add(".release", release);
    scroll.addNewLine().add(".units", units);
  }

  @Override
  public String name() {
    return "";
  }

  public boolean is(Modifier modifier) {
    return modifiers.contains(modifier);
  }

  public Optional<String> findMainModule() {
    return Modules.findMainModule(units.toUnits().map(SourceUnit::descriptor));
  }
}
