package test.integration;

import com.github.sormuras.bach.tool.ToolCall;
import java.io.PrintWriter;
import java.util.List;
import java.util.spi.ToolProvider;

public record PrintToolProvider(boolean configured, boolean normal, String message, int code)
    implements ToolProvider, ToolCall {

  public PrintToolProvider() {
    this(false, true, "String...", 0);
  }

  public PrintToolProvider(String message) {
    this(true, message, 0);
  }

  public PrintToolProvider(boolean normal, String message, int code) {
    this(true, normal, message, code);
  }

  @Override
  public String name() {
    return "print";
  }

  @Override
  public List<String> args() {
    return configured ? List.of(message) : List.of();
  }

  @Override
  public int run(PrintWriter out, PrintWriter err, String... args) {
    (normal ? out : err).print(configured ? message : String.join(" ", args));
    return code;
  }
}