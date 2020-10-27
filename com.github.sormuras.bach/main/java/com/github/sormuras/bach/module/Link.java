package com.github.sormuras.bach.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Module-URI pair annotation. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.MODULE)
@Repeatable(Links.class)
public @interface Link {
  /** @return the module name */
  String module();

  /** @return the uniform resource identifier */
  String uri();
}
