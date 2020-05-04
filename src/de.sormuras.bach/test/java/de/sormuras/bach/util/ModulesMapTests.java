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

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ModulesMapTests {

  @TestFactory
  Stream<DynamicTest> mapValueIsValidUri() {
    return new ModulesMap().entrySet().stream().map(e -> checkUriIsValid(e.getKey(), e.getValue()));
  }

  private DynamicTest checkUriIsValid(String module, String uri) {
    return dynamicTest(module, () -> URI.create(uri));
  }
}