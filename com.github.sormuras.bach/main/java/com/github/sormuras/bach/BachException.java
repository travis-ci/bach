package com.github.sormuras.bach;

import java.io.Serial;

public class BachException extends RuntimeException {
  @Serial private static final long serialVersionUID = 210026486670031112L;

  public BachException(Throwable cause) {
    super(cause);
  }

  public BachException(String message, Object... args) {
    super(args.length == 0 ? message : String.format(message, args));
  }
}
