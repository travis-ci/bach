package com.github.sormuras.bach;

import com.github.sormuras.bach.Options.Flag;
import com.github.sormuras.bach.Options.Property;
import com.github.sormuras.bach.api.BachAPI;
import com.github.sormuras.bach.internal.ModuleLayerBuilder;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Java Shell Builder. */
public class Bach implements BachAPI {

  public static final Path BIN = Path.of(".bach/bin");

  public static final Path EXTERNALS = Path.of(".bach/external-modules");

  public static final Path WORKSPACE = Path.of(".bach/workspace");

  public static final String INFO_MODULE = "bach.info";

  /** A {@code Bach}-creating service. */
  public interface Provider<B extends Bach> {
    /**
     * {@return a new instance of service-provider that extends {@code Bach}}
     *
     * @param options the options object to be passed on
     */
    B newBach(Options options);
  }

  public static void main(String... args) {
    System.exit(new Main().run(args));
  }

  public static Bach of(Options options) {
    var layer = ModuleLayerBuilder.build(options.get(Property.BACH_INFO, INFO_MODULE));
    return ServiceLoader.load(layer, Provider.class)
        .findFirst()
        .map(factory -> factory.newBach(options))
        .orElse(new Bach(options));
  }

  public static String version() {
    var module = Bach.class.getModule();
    if (!module.isNamed()) throw new IllegalStateException("Bach's module is unnamed?!");
    return module.getDescriptor().version().map(Object::toString).orElse("exploded");
  }

  private final Options options;
  private final Base base;
  private final Queue<Recording> recordings;
  private /*-*/ Browser browser;
  private final Project project;

  public Bach(Options options) {
    this.options = options;
    this.base = Base.of(Path.of(options.get(Property.BASE_DIRECTORY, "")));
    this.recordings = new ConcurrentLinkedQueue<>();
    this.browser = null; // defered creation in its accessor
    this.project = newProject();
  }

  /** {@return an instance of {@code HttpClient} that is memoized by this Bach object} */
  protected HttpClient newHttpClient() {
    return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  }

  /** {@return an instance of {@code Project} that is used as a component of this Bach object} */
  protected Project newProject() {
    return computeProject();
  }

  /** {@return this} */
  @Override
  public final Bach bach() {
    return this;
  }

  /** {@return the options instance passed to the constructor of this Bach object} */
  public final Options options() {
    return options;
  }

  public final Base base() {
    return base;
  }

  public final List<Recording> recordings() {
    return recordings.stream().toList();
  }

  public final synchronized Browser browser() {
    if (browser == null) browser = new Browser(this);
    return browser;
  }

  public final Project project() {
    return project;
  }

  public final boolean is(Flag flag) {
    return options.is(flag);
  }

  public final String get(Property property, String defaultValue) {
    return options.get(property, defaultValue);
  }

  public final Optional<String> get(Property property) {
    return Optional.ofNullable(get(property, null));
  }

  public final void build() throws Exception {
    buildProject();
  }

  public final void clean() throws Exception {
    debug("Clean...");

    if (Files.notExists(base.workspace())) return;
    try (var walk = Files.walk(base.workspace())) {
      var paths = walk.sorted((p, q) -> -p.compareTo(q)).toArray(Path[]::new);
      debug("Delete %s paths", paths.length);
      for (var path : paths) Files.deleteIfExists(path);
    }
  }

  public final void debug(String format, Object... args) {
    if (is(Flag.VERBOSE)) print("// " + format, args);
  }

  public final void print(String format, Object... args) {
    var message = args == null || args.length == 0 ? format : String.format(format, args);
    options.out().println(message);
  }

  public final void info() {
    print("module: %s", getClass().getModule().getName());
    print("class: %s", getClass().getName());
    print("base: %s", base);
    print("flags: %s", options.flags());
    print("project: %s", project);
  }

  public final void record(Recording recording) {
    recordings.add(recording);
  }
}
