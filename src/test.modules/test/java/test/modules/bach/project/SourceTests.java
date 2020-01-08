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

package test.modules.bach.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.sormuras.bach.project.Source;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SourceTests {
  @Test
  void defaultComponents() {
    var source = Source.of(Path.of(""));
    assertEquals(Path.of(""), source.path());
    assertEquals(0, source.release());
    assertEquals(Set.of(), source.modifiers());

    assertFalse(source.isTargeted());
    assertFalse(source.isVersioned());
    assertTrue(source.target().isEmpty());
  }
}
