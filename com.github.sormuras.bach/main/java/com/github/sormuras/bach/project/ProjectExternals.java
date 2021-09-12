package com.github.sormuras.bach.project;

import com.github.sormuras.bach.ExternalModuleLocator;
import com.github.sormuras.bach.ExternalModuleLocators;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

public record ProjectExternals(Set<String> requires, ExternalModuleLocators locators) {

  @FunctionalInterface
  public interface Operator extends UnaryOperator<ProjectExternals> {}

  public ProjectExternals withRequiresModule(String module) {
    var requires = new TreeSet<>(this.requires);
    requires.add(module);
    return new ProjectExternals(Set.copyOf(requires), locators);
  }

  public ProjectExternals withExternalModuleLocator(ExternalModuleLocator locator) {
    return new ProjectExternals(requires, locators.with(locator));
  }
}