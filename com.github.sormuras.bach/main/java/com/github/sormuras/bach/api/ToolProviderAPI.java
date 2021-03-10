package com.github.sormuras.bach.api;

import com.github.sormuras.bach.Bach;
import com.github.sormuras.bach.Command;
import com.github.sormuras.bach.Options;
import com.github.sormuras.bach.Recording;
import com.github.sormuras.bach.Recordings;
import com.github.sormuras.bach.internal.ModuleLayerBuilder;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.module.ModuleFinder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

/** Methods related to finding and running provided tools. */
public interface ToolProviderAPI {

  Bach bach();

  default Stream<ToolProvider> computeToolProviders() {
    return computeToolProviders(ModuleFinder.of(bach().folders().externalModules()));
  }

  default Stream<ToolProvider> computeToolProviders(ModuleFinder finder, String... roots) {
    var layer = new ModuleLayerBuilder().before(finder).roots(Set.of(roots)).build();
    return ServiceLoader.load(layer, ToolProvider.class).stream().map(ServiceLoader.Provider::get);
  }

  default Optional<ToolProvider> computeToolProvider(String name) {
    return computeToolProviders().filter(it -> it.name().equals(name)).findFirst();
  }

  default Recording run(Command<?> command) {
    bach().debug("Run %s", command.toLine());
    if (command instanceof ToolProvider provider) return run(provider, command.arguments());
    var name = command.name();
    var provider = computeToolProvider(name);
    if (provider.isEmpty()) throw new RuntimeException("No tool provider found for name: " + name);
    return run(provider.get(), command.arguments());
  }

  default Recordings run(Command<?> command, Command<?>... commands) {
    return run(Stream.concat(Stream.of(command), Stream.of(commands)));
  }

  default Recordings run(Stream<? extends Command<?>> commands) {
    var sequential = bach().is(Options.Flag.RUN_COMMANDS_SEQUENTIALLY);
    bach().debug("Run stream of commands %s", sequential ? "sequentially" : "in parallel");
    var stream = sequential ? commands.sequential() : commands.parallel();
    var recordings = stream.map(this::run).toList();
    bach().debug("Return %d recording%s", recordings.size(), recordings.size() == 1 ? "" : "s");
    return new Recordings(recordings);
  }

  default Recording run(Command<?> command, ModuleFinder finder, String... roots) {
    bach().debug("Run %s", command.toLine());
    var providers = computeToolProviders(finder, roots);
    var provider = providers.filter(it -> it.name().equals(command.name())).findFirst();
    return run(provider.orElseThrow(), command.arguments());
  }

  default Recording run(ToolProvider provider, List<String> arguments) {
    var name = provider.name();
    var currentThread = Thread.currentThread();
    var currentLoader = currentThread.getContextClassLoader();
    currentThread.setContextClassLoader(provider.getClass().getClassLoader());

    var out = new StringWriter();
    var err = new StringWriter();
    var args = arguments.toArray(String[]::new);
    var start = Instant.now();
    int code;
    try {
      var skips = bach().options().values(Options.Property.SKIP_TOOL);
      var skip = skips.contains(name);
      if (skip) bach().debug("Skip run of '%s' due to --skip-tool=%s", name, skips);
      code = skip ? 0 : provider.run(new PrintWriter(out), new PrintWriter(err), args);
    } finally {
      currentThread.setContextClassLoader(currentLoader);
    }

    var duration = Duration.between(start, Instant.now());
    var tid = currentThread.getId();
    var output = out.toString().trim();
    var errors = err.toString().trim();
    var recording = new Recording(name, arguments, tid, duration, code, output, errors);

    bach().record(recording);
    return recording;
  }
}
