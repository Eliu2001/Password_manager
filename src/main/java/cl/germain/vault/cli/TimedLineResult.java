package cl.germain.vault.cli;

import java.util.Objects;

/** Result of waiting for a session command line until its timeout. */
public record TimedLineResult(Kind kind, String line) {
  public enum Kind {
    LINE,
    END_OF_INPUT,
    TIMED_OUT
  }

  public TimedLineResult {
    Objects.requireNonNull(kind, "kind");
    if ((kind == Kind.LINE) != (line != null)) {
      throw new IllegalArgumentException("Timed line payload does not match its kind");
    }
  }

  public static TimedLineResult line(String value) {
    return new TimedLineResult(Kind.LINE, Objects.requireNonNull(value));
  }

  public static TimedLineResult endOfInput() {
    return new TimedLineResult(Kind.END_OF_INPUT, null);
  }

  public static TimedLineResult timedOut() {
    return new TimedLineResult(Kind.TIMED_OUT, null);
  }
}
