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

package de.sormuras.bach.api;

import java.lang.module.FindException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 3rd-party modules information. */
public /*static*/ final class Library {

  private final Set<String> requires;
  private final List<Locator> locators;

  public Library(Set<String> requires, List<Locator> locators) {
    this.requires = Objects.requireNonNull(requires, "requires");
    this.locators = Objects.requireNonNull(locators, "locators");
  }

  public Set<String> requires() {
    return requires;
  }

  public List<Locator> locators() {
    return locators;
  }

  public URI uri(String module) {
    for (var locator : locators) {
      var located = locator.locate(module);
      if (located.isPresent()) return located.get();
    }
    throw new FindException("Module " + module + " not locatable via: " + locators());
  }
}