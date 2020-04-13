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

package de.sormuras.bach.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class FunctionsTests {

  private static String slowString(String string) {
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      // ignore
    }
    return string;
  }

  @Test
  void memoizeString() {
    var hello = Functions.memoize(() -> FunctionsTests.slowString("Hello"));
    var first = hello.get();
    assertEquals("Hello", first);
    for (int i = 0; i < 1234; i++) assertSame(first, hello.get());
  }
}