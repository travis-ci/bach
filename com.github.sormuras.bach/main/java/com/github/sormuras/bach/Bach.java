package com.github.sormuras.bach;

import com.github.sormuras.bach.internal.DurationSupport;
import com.github.sormuras.bach.internal.ExecuteModuleToolProvider;
import com.github.sormuras.bach.internal.ExecuteProcessToolProvider;
import com.github.sormuras.bach.internal.ModuleLaunchingToolCall;
import com.github.sormuras.bach.internal.ModuleSupport;
import com.github.sormuras.bach.internal.ProcessStartingToolCall;
import com.github.sormuras.bach.internal.ToolProviderSupport;
import com.github.sormuras.bach.internal.ToolRunningToolCall;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

public class Bach implements AutoCloseable {

  public static String version() {
    return ModuleSupport.version(Bach.class.getModule());
  }

  private final Configuration configuration;
  private final Logbook logbook;

  public Bach(String... args) {
    this(Configuration.of().with(Options.of(args)));
  }

  public Bach(Configuration configuration) {
    this.configuration = configuration;
    this.logbook = constructLogbook();
    logbook()
        .logMessage(
            System.Logger.Level.INFO,
            "Initialized Bach %s (Java %s, %s, %s)"
                .formatted(
                    version(),
                    System.getProperty("java.version"),
                    System.getProperty("os.name"),
                    Path.of(System.getProperty("user.dir")).toUri()));
  }

  protected Logbook constructLogbook() {
    return new Logbook(this);
  }

  public Configuration configuration() {
    return configuration;
  }

  public Logbook logbook() {
    return logbook;
  }

  public Explorer explorer() {
    return new Explorer(this);
  }

  public Grabber grabber(ExternalModuleLocator... locators) {
    return grabber(ExternalModuleLocators.of(locators));
  }

  public Grabber grabber(ExternalModuleLocators locators) {
    return new Grabber(this, locators);
  }

  @Override
  public void close() {
    writeLogbook();
    var duration = DurationSupport.toHumanReadableString(logbook().uptime());
    logbook().logMessage(System.Logger.Level.INFO, "Total uptime was %s".formatted(duration));
  }

  public Configuration.Pathing path() {
    return configuration().pathing();
  }

  public PrintWriter out() {
    return configuration().printing().out();
  }

  public PrintWriter err() {
    return configuration().printing().err();
  }

  public void logCaption(String line) {
    logbook().logCaption(line);
  }

  public void logMessage(String info) {
    logbook().logMessage(System.Logger.Level.INFO, info);
  }

  public void logMessage(System.Logger.Level level, String text) {
    logbook().logMessage(level, text);
  }

  public ToolRun run(ToolCall call) {
    logbook().logToolCall(call);

    if (call instanceof ToolRunningToolCall tool) {
      var finder = tool.finder().orElse(configuration().tooling().finder());
      var provider = finder.find(tool.name());
      if (provider.isEmpty())
        throw new RuntimeException("Tool '%s' not found".formatted(tool.name()));
      return run(provider.get(), tool.arguments());
    }
    if (call instanceof ProcessStartingToolCall process) {
      var tool = new ExecuteProcessToolProvider();
      var command =
          Stream.concat(Stream.of(process.executable().toString()), process.arguments().stream())
              .toList();
      return run(tool, command);
    }
    if (call instanceof ModuleLaunchingToolCall module) {
      var tool = new ExecuteModuleToolProvider(module.finder());
      var arguments = Stream.concat(Stream.of(module.name()), module.arguments().stream()).toList();
      return run(tool, arguments);
    }
    throw new AssertionError("Where art thou, switch o' patterns?");
  }

  public ToolRun run(String tool, ToolComposer composer) {
    return run(composer.apply(ToolCall.of(tool)));
  }

  public ToolRun run(ToolFinder finder, String name, ToolComposer composer) {
    return run(composer.apply(ToolCall.of(finder, name)));
  }

  public ToolRun run(Path executable, ToolComposer composer) {
    return run(composer.apply(ToolCall.process(executable)));
  }

  public ToolRun run(ModuleFinder finder, String module, ToolComposer composer) {
    return run(composer.apply(ToolCall.module(finder, module)));
  }

  private ToolRun run(ToolProvider provider, List<String> arguments) {
    var currentThread = Thread.currentThread();
    var currentLoader = currentThread.getContextClassLoader();
    currentThread.setContextClassLoader(provider.getClass().getClassLoader());

    var out = new StringWriter();
    var err = new StringWriter();
    var start = Instant.now();
    int code;
    try {
      var strings = arguments.toArray(String[]::new);
      code = provider.run(new PrintWriter(out), new PrintWriter(err), strings);
    } finally {
      currentThread.setContextClassLoader(currentLoader);
    }

    var name = provider.name();
    var duration = Duration.between(start, Instant.now());
    var thread = currentThread.getId();
    var output = out.toString().strip();
    var errors = err.toString().strip();

    var run = new ToolRun(name, arguments, thread, duration, code, output, errors);
    var description = ToolProviderSupport.describe(provider);
    logbook().logToolRun(run, description);

    return configuration().lenient() ? run : run.requireSuccessful();
  }

  public void run(Stream<ToolCall> calls) {
    calls.forEach(this::run);
  }

  public void runParallel(ToolCall... calls) {
    run(Stream.of(calls).parallel());
  }

  public void writeLogbook() {
    try {
      var file = logbook().write(path().workspace());
      logbook().logCaption("Wrote logbook to %s".formatted(file.toUri()));
    } catch (Exception exception) {
      exception.printStackTrace(err());
    }
  }
}
