package com.github.sormuras.bach.api;

import com.github.sormuras.bach.Bach;
import com.github.sormuras.bach.Options;
import com.github.sormuras.bach.Options.Property;
import com.github.sormuras.bach.Project;
import com.github.sormuras.bach.ProjectInfo;
import com.github.sormuras.bach.internal.Strings;
import com.github.sormuras.bach.lookup.ModuleLookup;
import com.github.sormuras.bach.project.Libraries;
import com.github.sormuras.bach.project.Name;
import com.github.sormuras.bach.project.Spaces;
import com.github.sormuras.bach.project.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Methods related to building projects. */
public interface ProjectBuilderAPI {

  Bach bach();

  default Project computeProject() {
    var info = computeProjectInfo();
    var name =
        bach().get(Property.PROJECT_NAME).map(Name::of).orElseGet(() -> computeProjectName(info));
    var version =
        bach()
            .get(Property.PROJECT_VERSION)
            .map(Version::of)
            .orElseGet(() -> computeProjectVersion(info));
    var libraries = computeProjectLibraries(info);
    var spaces = computeProjectSpaces(info);
    return new Project(name, version, libraries, spaces);
  }

  default ProjectInfo computeProjectInfo() {
    var info = getClass().getModule().getAnnotation(ProjectInfo.class);
    if (info != null) return info;
    return Bach.class.getModule().getAnnotation(ProjectInfo.class);
  }

  default Name computeProjectName(ProjectInfo info) {
    var name = info.name();
    if (!name.equals("*")) return Name.of(name);
    return Name.of("" + bach().base().directory().toAbsolutePath().getFileName());
  }

  default Version computeProjectVersion(ProjectInfo info) {
    return Version.of(info.version());
  }

  default Libraries computeProjectLibraries(ProjectInfo info) {
    var requires = Set.of(info.requires());
    var lookups = new ArrayList<ModuleLookup>();
    for (var external : info.lookup()) lookups.add(computeProjectModuleLookup(external));
    return new Libraries(requires, List.copyOf(lookups));
  }

  default ModuleLookup computeProjectModuleLookup(ProjectInfo.External external) {
    var module = external.module();
    var target = external.via();
    return switch (external.type()) {
      case AUTO -> Libraries.lookup(module).via(target);
      case URI -> Libraries.lookup(module).viaUri(target);
      case PATH -> Libraries.lookup(module).viaPath(external.pathBase(), target);
      case MAVEN -> Libraries.lookup(module).viaMaven(external.mavenRepository(), target);
    };
  }

  default Spaces computeProjectSpaces(ProjectInfo info) {
    return new Spaces(info.format());
  }

  default String computeMainJarFileName(String module) {
    return module + '@' + bach().project().version().toNumberAndPreRelease() + ".jar";
  }

  default void buildProject() throws Exception {
    var bach = bach();
    var project = bach.project();
    bach.print("Build %s %s", project.name().name(), project.version().version());
    if (bach.is(Options.Flag.VERBOSE)) bach.info();
    var start = Instant.now();
    if (bach.is(Options.Flag.STRICT)) bach.formatJavaSourceFiles(JavaFormatterAPI.Mode.VERIFY);
    bach.loadMissingExternalModules();
    buildProjectMainSpace();
    bach.print("Build took %s", Strings.toString(Duration.between(start, Instant.now())));
  }

  default void buildProjectMainSpace() throws Exception {}
}