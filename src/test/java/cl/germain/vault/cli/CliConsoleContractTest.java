package cl.germain.vault.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class CliConsoleContractTest {
  @Test
  void replaceableConsoleKeepsOutputErrorAndSecretChannelsSeparate() throws Exception {
    RecordingConsole console = new RecordingConsole();
    console.printOutput("normal");
    console.printPrivate("vault content");
    console.printError("failure");
    char[] secret = {'s', 'y', 'n'};
    console.printSecret(secret);

    assertEquals("normal", console.output);
    assertEquals("vault content", console.privateOutput);
    assertEquals("failure", console.error);
    assertArrayEquals(secret, console.secret);
  }

  @Test
  void replaceableConsoleCanRepresentEndOfInputAndInterruption() throws Exception {
    RecordingConsole console = new RecordingConsole();
    assertNull(console.readLine("prompt"));
    assertNull(console.readPassword("password"));
    console.interrupted = true;
    assertThrows(InterruptedException.class, () -> console.readLine("prompt"));
    assertThrows(InterruptedException.class, () -> console.readPassword("password"));
  }

  private static final class RecordingConsole implements CliConsole {
    private String output;
    private String privateOutput;
    private String error;
    private char[] secret;
    private boolean interrupted;

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public String readLine(String prompt) throws InterruptedException {
      if (interrupted) throw new InterruptedException();
      return null;
    }

    @Override
    public char[] readPassword(String prompt) throws InterruptedException {
      if (interrupted) throw new InterruptedException();
      return null;
    }

    @Override
    public TimedLineResult readLineUntil(String prompt, long timeoutNanos) {
      return TimedLineResult.endOfInput();
    }

    @Override
    public void discardPendingLine() {}

    @Override
    public void printOutput(String message) {
      output = message;
    }

    @Override
    public void printError(String message) {
      error = message;
    }

    @Override
    public void printPrivate(String message) {
      privateOutput = message;
    }

    @Override
    public void printSecret(char[] value) throws IOException {
      secret = value.clone();
    }
  }
}
