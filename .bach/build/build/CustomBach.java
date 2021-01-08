package build;

import com.github.sormuras.bach.Bach;
import com.github.sormuras.bach.Base;
import com.github.sormuras.bach.Command;
import com.github.sormuras.bach.Flag;
import com.github.sormuras.bach.Main;
import com.github.sormuras.bach.ModuleLookup;
import com.github.sormuras.bach.ModuleLookups.GitHubReleases;
import com.github.sormuras.bach.ModuleLookups.JUnit;
import com.github.sormuras.bach.ModuleLookups.JavaFX;
import com.github.sormuras.bach.ModuleLookups.LWJGL;
import com.github.sormuras.bach.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;

public class CustomBach extends Bach {

  public static void main(String... args) {
    var bach = new CustomBach(new Flag[0]);
    var main = new Main(bach);
    var code = args.length == 0 ? main.performAction("build") : main.performActions(args);
    System.exit(code);
  }

  public CustomBach() {
    this(Flag.VERBOSE);
  }

  public CustomBach(Flag... flags) {
    super(Base.ofSystem(), System.out::println, flags);
  }

  @Override
  public Project newProject() throws Exception {
    var now = LocalDateTime.now(ZoneOffset.UTC);
    var version =
        Project.defaultVersion(
            Files.readString(Path.of("VERSION"))
                + "-custom+"
                + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now));
    return super.newProject().version(version);
  }

  @Override
  public ModuleLookup newModuleLookup() {
    return ModuleLookup.compose(
        JUnit.V_5_7_0,
        new JavaFX("15.0.1"),
        new LWJGL("3.2.3"),
        new GitHubReleases(this),
        ModuleLookup.ofProvidersFoundInExternalModules());
  }

  @Override
  public void buildMainSpace() throws Exception {
    var module = "com.github.sormuras.bach";
    var moduleVersion = project().version();
    var version = project().versionNumberAndPreRelease();
    var javaRelease = 16;
    var destination = base().workspace("classes", "main", "" + javaRelease);
    var modules = base().workspace("modules");

    run(
        Command.javac()
            .add("--release", javaRelease)
            .add("--module", module)
            .add("--module-version", moduleVersion)
            .add("--module-source-path", "./*/main/java")
            .add("--module-path", Bach.EXTERNALS)
            .add("-encoding", "UTF-8")
            .add("-g")
            .add("-parameters")
            .add("-Xlint")
            .add("-Werror")
            .add("-d", destination));

    Files.createDirectories(modules);
    var file = modules.resolve(computeMainJarFileName(module));
    run(
        Command.jar()
            .add("--verbose")
            .add("--create")
            .add("--file", file)
            .add("--main-class", module + ".Main")
            .add("-C", destination.resolve(module), ".")
            .add("-C", base().directory(module, "main/java"), "."));

    run(
        Command.javadoc()
            .add("--module", module)
            .add("--module-source-path", "./*/main/java")
            .add("--module-path", Bach.EXTERNALS)
            .add("-encoding", "UTF-8")
            .add("-windowtitle", "🎼 Bach " + version)
            .add("-doctitle", "🎼 Bach " + version)
            .add("-header", "🎼 Bach " + version)
            .add("-notimestamp")
            .add("-Xdoclint:-missing")
            .add("-Werror")
            .add("-d", base().workspace("documentation", "api")));

    generateMavenConsumerPom(module, version, file);
  }

  private void generateMavenConsumerPom(String module, String version, Path file) throws Exception {
    var maven = Files.createDirectories(base().workspace("deploy", "maven"));

    var pom =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>

          <groupId>com.github.sormuras.bach</groupId>
          <artifactId>${MODULE}</artifactId>
          <version>${VERSION}</version>

          <name>Bach</name>
          <description>🎼 Java Shell Builder</description>

          <url>https://github.com/sormuras/bach</url>
          <scm>
            <url>https://github.com/sormuras/bach.git</url>
          </scm>

          <developers>
            <developer>
              <name>Christian Stein</name>
              <id>sormuras</id>
            </developer>
          </developers>

          <licenses>
            <license>
              <name>MIT License</name>
              <url>https://opensource.org/licenses/mit-license</url>
              <distribution>repo</distribution>
            </license>
          </licenses>

          <dependencies>
            <!-- system module: java.base -->
            <!-- system module: java.net.http -->
            <!-- system module: jdk.compiler -->
            <!-- system module: jdk.crypto.ec -->
            <!-- system module: jdk.jartool -->
            <!-- system module: jdk.javadoc -->
            <!-- system module: jdk.jdeps -->
            <!-- system module: jdk.jlink -->
          </dependencies>
        </project>
        """
            .replace("${MODULE}", module)
            .replace("${VERSION}", version);

    // deploy/maven/${MODULE}.pom.xml
    var pomFile = Files.writeString(maven.resolve(module + ".pom.xml"), pom);

    // deploy/maven/empty.zip
    // https://central.sonatype.org/pages/requirements.html#supply-javadoc-and-sources
    var emptyZip = maven.resolve("empty.zip");
    byte[] bytes = {0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    Files.write(emptyZip, bytes);

    // deploy/maven/${MODULE}.files
    var files =
        new StringJoiner(" ")
            .add("-DpomFile=" + pomFile)
            .add("-Dfile=" + file)
            .add("-Dsources=" + emptyZip)
            .add("-Djavadoc=" + emptyZip);
    Files.writeString(maven.resolve(module + ".files"), files.toString());
  }
}
