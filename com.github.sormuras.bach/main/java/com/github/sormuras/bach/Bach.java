package com.github.sormuras.bach;

/** Java Shell Builder. */
public class Bach {
  /**
   * Returns the version.
   *
   * @return the version or {@code "?"} if the version is unknown at runtime
   */
  public static String version() {
    var descriptor = Bach.class.getModule().getDescriptor();
    if (descriptor == null) return "?";
    return descriptor.version().map(Object::toString).orElse("?");
  }

  /** Hidden default constructor. */
  private Bach() {}
}
