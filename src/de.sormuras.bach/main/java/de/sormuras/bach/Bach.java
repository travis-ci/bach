/*
 * Bach - Java Shell Builder
 * Copyright (C) 2019 Christian Stein
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

package de.sormuras.bach;

/** Build modular Java project. */
public class Bach {

  /** Bach.java's version. */
  public static final String VERSION = "2.0-ea";

  /** Main entry-point. */
  public static void main(String... args) {
    var log = Log.ofSystem();
    var bach = new Bach(log);
    bach.execute(Task.build());
  }

  private final Log log;

  public Bach(Log log) {
    this.log = log;
    log.debug("Bach.java %s initialized.", VERSION);
  }

  public Log log() {
    return log;
  }

  public void execute(Task... tasks) {
    try {
      for (var task : tasks) {
        log.debug("Executing task: %s", task.getClass().getSimpleName());
        task.execute(this);
      }
    } catch (Exception e) {
      throw new Error("Task failed to execute: " + e, e);
    }
  }
}
