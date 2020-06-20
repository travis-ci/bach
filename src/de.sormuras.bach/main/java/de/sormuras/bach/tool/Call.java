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

package de.sormuras.bach.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.spi.ToolProvider;

/** A tool call configuration. */
public interface Call<T extends Call<T>> {

  /**
   * Return the name of this tool call configuration.
   *
   * @return A string representation of a tool
   * @see #tool()
   */
  String name();

  /**
   * Return the arguments of this tool call configuration.
   *
   * @return A possible empty list of argument instances
   */
  List<Argument> arguments();

  /**
   * Create new instance of a tool call configuration with the given arguments.
   *
   * @param arguments The possible empty list of argument objects
   * @return An instance of {@code T} with the given arguments
   */
  T with(List<Argument> arguments);

  /**
   * Return the activation state of this tool call configuration.
   *
   * @return {@code true} if this tool call is to executed, else {@code false}
   */
  default boolean activated() {
    return !arguments().isEmpty();
  }

  /**
   * Return a tool provider instance in an optional object.
   *
   * @return An optional tool provider instance
   * @see #name()
   */
  default Optional<ToolProvider> tool() {
    return ToolProvider.findFirst(name());
  }

  /**
   * Create new call instance with the given additional arguments.
   *
   * @param argument The first additional argument
   * @param arguments The array of more additional arguments
   * @return A new call instance with the given arguments
   */
  default T with(Argument argument, Argument... arguments) {
    var list = new ArrayList<>(arguments());
    list.add(argument);
    if (arguments.length > 0) Collections.addAll(list, arguments);
    return with(list);
  }
  
  /**
   * Create new call instance with one additional argument.
   *
   * @param option The option to used as an additional argument
   * @return A new call instance with the given argument
   */
  default T with(String option) {
    return with(new Argument(option, List.of()));
  }
  
  /**
   * Create new call instance with one or more additional arguments.
   *
   * @param option The option to used as an additional argument
   * @param values The possible empty array of additional arguments
   * @return A new call instance with the given arguments
   */
  default T with(String option, Object... values) {
    var strings = new ArrayList<String>();
    for (var value : values) strings.add(value.toString());
    return with(new Argument(option, strings));
  }

  /**
   * Return the arguments of this tool call configuration as an array of string objects.
   *
   * @return An array of strings representing the arguments of this tool call.
   */
  default String[] toStringArray() {
    var list = new ArrayList<String>();
    for (var argument : arguments()) {
      list.add(argument.key());
      list.addAll(argument.values());
    }
    return list.toArray(String[]::new);
  }
}
