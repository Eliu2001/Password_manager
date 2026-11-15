package cl.germain.vault.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CliSessionApplicationTest {
  @Test
  void rejectsSessionWithoutConsoleBeforeInvokingHandler() {
    RecordingConsole console = new RecordingConsole(false);
    CliApplication app =
        new CliApplication(
            new CliArgumentParser(),
            console,
            command -> {
              throw new AssertionError("Command handler must not be invoked");
            });

    assertEquals(1, app.run("session", "--vault", "vault.pvlt"));
    assertEquals(0, console.passwordReads);
    assertTrue(console.errors.getFirst().contains("terminal interactiva"));
  }

  @Test
  void validatesSessionArgumentsBeforeConsoleGate() {
    RecordingConsole console = new RecordingConsole(false);
    CliApplication app =
        new CliApplication(
            new CliArgumentParser(),
            console,
            command -> {
              throw new AssertionError("Command handler must not be invoked");
            });

    assertEquals(1, app.run("session", "--vault", "vault.pvlt", "--timeout-minutes", "61"));
    assertEquals(0, console.passwordReads);
    assertTrue(console.errors.getFirst().contains("Argumentos inválidos"));
  }

  private static final class RecordingConsole implements CliConsole {
    private final boolean available;
    private final List<String> errors = new ArrayList<>();
    private int passwordReads;

    private RecordingConsole(boolean available) {
      this.available = available;
    }

    @Override
    public boolean isAvailable() {
      return available;
    }

    @Override
    public String readLine(String prompt) {
      return null;
    }

    @Override
    public char[] readPassword(String prompt) {
      passwordReads++;
      return null;
    }

    @Override
    public TimedLineResult readLineUntil(String prompt, long timeoutNanos) {
      return TimedLineResult.endOfInput();
    }

    @Override
    public void discardPendingLine() {}

    @Override
    public void printOutput(String message) {}

    @Override
    public void printError(String message) {
      errors.add(message);
    }

    @Override
    public void printPrivate(String message) throws IOException {
      throw new IOException("No console");
    }

    @Override
    public void printSecret(char[] secret) throws IOException {
      throw new IOException("No console");
    }
  }
}
