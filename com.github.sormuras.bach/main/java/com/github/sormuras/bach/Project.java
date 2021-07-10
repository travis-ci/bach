package com.github.sormuras.bach;

import com.github.sormuras.bach.external.ExternalModuleLocator;
import com.github.sormuras.bach.internal.RecordComponents;
import com.github.sormuras.bach.project.ProjectDefaults;
import com.github.sormuras.bach.project.ProjectExternals;
import com.github.sormuras.bach.project.ProjectName;
import com.github.sormuras.bach.project.ProjectSpace;
import com.github.sormuras.bach.project.ProjectSpaces;
import com.github.sormuras.bach.project.ProjectVersion;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public record Project(
    ProjectName name,
    ProjectVersion version,
    ProjectDefaults defaults,
    ProjectSpaces spaces,
    ProjectExternals externals) {

  public String toNameAndVersion() {
    return "%s %s".formatted(name().value(), version().value().toString());
  }

  public static Project of(String name, String version) {
    return new Project(
        new ProjectName(name),
        new ProjectVersion(ModuleDescriptor.Version.parse(version)),
        new ProjectDefaults(StandardCharsets.UTF_8),
        new ProjectSpaces(new ProjectSpace("main", ""), new ProjectSpace("test", "-test")),
        new ProjectExternals(Set.of(), List.of()));
  }

  public Project assertJDK(int feature) {
    return assertJDK(
        version -> version.feature() == feature,
        "Expected JDK %d but runtime version is %s".formatted(feature, Runtime.version()));
  }

  public Project assertJDK(Predicate<Runtime.Version> predicate, String message) {
    if (predicate.test(Runtime.version())) return this;
    throw new AssertionError(message);
  }

  public Project with(Object component) {
    RecordComponents.of(Project.class).findUnique(component.getClass()).orElseThrow();
    return new Project(
        component instanceof ProjectName name ? name : name,
        component instanceof ProjectVersion version ? version : version,
        component instanceof ProjectDefaults defaults ? defaults : defaults,
        component instanceof ProjectSpaces spaces ? spaces : spaces,
        component instanceof ProjectExternals externals ? externals : externals);
  }

  public Project withName(String name) {
    return with(new ProjectName(name));
  }

  public Project withVersion(String version) {
    return with(new ProjectVersion(ModuleDescriptor.Version.parse(version)));
  }

  public Project withDefaultSourceFileEncoding(String encoding) {
    return withDefaultSourceFileEncoding(Charset.forName(encoding));
  }

  public Project withDefaultSourceFileEncoding(Charset encoding) {
    return with(new ProjectDefaults(encoding));
  }

  public Project withMainSpace(UnaryOperator<ProjectSpace> operator) {
    return with(new ProjectSpaces(operator.apply(spaces.main()), spaces.test()));
  }

  public Project withTestSpace(UnaryOperator<ProjectSpace> operator) {
    return with(new ProjectSpaces(spaces.main(), operator.apply(spaces.test())));
  }

  public Project withExternals(UnaryOperator<ProjectExternals> operator) {
    return with(operator.apply(externals));
  }

  public Project withRequiresExternalModules(String module, String... more) {
    return with(externals.withRequires(module, more));
  }

  public Project withExternalModuleLocators(ExternalModuleLocator locator, ExternalModuleLocator... more) {
    return with(externals.with(locator, more));
  }
}
