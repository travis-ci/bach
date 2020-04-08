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

package de.sormuras.bach.task;

import java.io.PrintWriter;
import java.util.List;
import java.util.spi.ToolProvider;

public class NoopToolProvider implements ToolProvider {

  private final int code;
  private final boolean printArguments;
  private final boolean printErrorLine;

  public NoopToolProvider() {
    this(0, false, false);
  }

  public NoopToolProvider(int code, boolean printArguments, boolean printErrorLine) {
    this.code = code;
    this.printArguments = printArguments;
    this.printErrorLine = printErrorLine;
  }

  @Override
  public String name() {
    return "noop";
  }

  @Override
  public int run(PrintWriter out, PrintWriter err, String... args) {
    if (printArguments) out.println("args=" + List.of(args));
    if (printErrorLine) err.println("an error line presented by " + getClass().getSimpleName());
    return code;
  }
}
