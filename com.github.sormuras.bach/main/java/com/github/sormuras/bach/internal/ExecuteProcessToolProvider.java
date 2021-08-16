package com.github.sormuras.bach.internal;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.spi.ToolProvider;

public record ExecuteProcessToolProvider() implements ToolProvider {

  @Override
  public String name() {
    return "execute-process";
  }

  @Override
  public int run(PrintWriter out, PrintWriter err, String... arguments) {
    var builder = new ProcessBuilder();
    builder.command().addAll(List.of(arguments));
    try {
      var process = builder.start();
      new Thread(new StreamLineConsumer(process.getInputStream(), out::println)).start();
      new Thread(new StreamLineConsumer(process.getErrorStream(), err::println)).start();
      return process.waitFor();
    } catch (IOException exception) {
      exception.printStackTrace(err);
      return 1;
    } catch (InterruptedException exception) {
      err.println("Interrupted");
      return 2;
    }
  }
}
