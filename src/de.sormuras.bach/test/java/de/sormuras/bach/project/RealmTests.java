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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.sormuras.bach.API;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RealmTests {

  @Test
  void empty() {
    var empty = API.emptyRealm();
    assertEquals("empty", empty.name());
    assertEquals(0, empty.release());
    assertFalse(empty.preview());
    assertEquals(0, empty.units().size());
    assertNull(empty.mainUnit());
    assertTrue(empty.toString().contains("empty"));
    assertEquals(Optional.empty(), empty.toMainUnit());
  }
}