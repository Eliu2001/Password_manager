package cl.germain.vault.cli;

import java.io.IOException;

/** Console boundary used by the CLI and replaceable in tests. */
public interface CliConsole {
  boolean isAvailable();

  /** Returns null when the user cancels or the input ends. */
  String readLine(String prompt) throws IOException, InterruptedException;

  /** Waits for a session command until the supplied monotonic duration expires. */
  TimedLineResult readLineUntil(String prompt, long timeoutNanos)
      throws IOException, InterruptedException;

  /** Waits for and discards any line read that timed out, before another input mode begins. */
  void discardPendingLine() throws IOException, InterruptedException;

  /** Returns null when the user cancels or the input ends. */
  char[] readPassword(String prompt) throws IOException, InterruptedException;

  void printOutput(String message);

  void printError(String message);

  /** Writes non-secret vault content only to the interactive console. */
  void printPrivate(String message) throws IOException;

  /** Releases console resources owned by this adapter. */
  default void close() {}

  /** Writes secret characters only to the interactive console. */
  void printSecret(char[] secret) throws IOException;
}
