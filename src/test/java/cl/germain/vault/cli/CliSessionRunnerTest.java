package cl.germain.vault.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cl.germain.vault.generator.PasswordGenerator;
import cl.germain.vault.storage.SyntheticVaultFixtures;
import cl.germain.vault.storage.VaultSession;
import cl.germain.vault.storage.VaultStorage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliSessionRunnerTest {
  @TempDir Path directory;

  @Test
  void executesSeveralCommandsInOneOpenedSessionAndExitsCleanly() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vault = directory.resolve("session.pvlt");
    char[] master = "synthetic-master".toCharArray();
    try (VaultSession ignored = storage.create(vault, master)) {
      Arrays.fill(master, '\0');
    }

    ScriptedConsole console = new ScriptedConsole();
    console.passwords.add("synthetic-master".toCharArray());
    char[] entryPassword = "synthetic-entry".toCharArray();
    console.passwords.add(entryPassword);
    console.visibleLines.add("example-service");
    console.visibleLines.add("example-user");
    console.visibleLines.add("");
    console.lines.add(TimedLineResult.line("add"));
    console.lines.add(TimedLineResult.line("list"));
    console.lines.add(TimedLineResult.line("exit"));
    VaultCliCommands commands = new VaultCliCommands(console, storage, new PasswordGenerator());
    CliSessionRunner runner =
        new CliSessionRunner(
            console, storage, commands, new CliArgumentParser(), () -> console.ticks += 1);

    assertEquals(
        CliExitStatus.SUCCESS,
        runner.run(new CliSessionRequest(vault, CliSessionRequest.DEFAULT_TIMEOUT_MINUTES)));
    assertEquals(2, console.passwordPrompts);
    assertTrue(console.privateOutput.stream().anyMatch(line -> line.contains("Entrada guardada")));
    assertTrue(console.privateOutput.stream().anyMatch(line -> line.contains("example-service")));
    assertFalse(console.output.stream().anyMatch(line -> line.contains("example-service")));
    assertTrue(Arrays.equals(new char[entryPassword.length], entryPassword));

    char[] reopenPassword = "synthetic-master".toCharArray();
    try (VaultSession reopened = storage.open(vault, reopenPassword)) {
      assertEquals(1, reopened.vault().list().size());
      assertEquals("example-service", reopened.vault().list().getFirst().service());
    } finally {
      Arrays.fill(reopenPassword, '\0');
    }
  }

  @Test
  void timeoutClosesVaultBeforeDiscardAndRequiresReauthentication() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vault = directory.resolve("timeout.pvlt");
    char[] master = "synthetic-master".toCharArray();
    try (VaultSession ignored = storage.create(vault, master)) {
      Arrays.fill(master, '\0');
    }

    ScriptedConsole console = new ScriptedConsole();
    console.passwords.add("synthetic-master".toCharArray());
    console.passwords.add("synthetic-master".toCharArray());
    console.lines.add(TimedLineResult.timedOut());
    console.lines.add(TimedLineResult.line("exit"));
    console.onDiscard =
        () -> {
          char[] password = "synthetic-master".toCharArray();
          try (VaultSession ignored = storage.open(vault, password)) {
            console.vaultWasClosedBeforeDiscard = true;
          } catch (IOException | cl.germain.vault.crypto.DecryptionException exception) {
            throw new AssertionError("Vault lock remained held while discarding input", exception);
          } finally {
            Arrays.fill(password, '\0');
          }
        };

    VaultCliCommands commands = new VaultCliCommands(console, storage, new PasswordGenerator());
    CliSessionRunner runner =
        new CliSessionRunner(
            console, storage, commands, new CliArgumentParser(), () -> console.ticks++);

    assertEquals(CliExitStatus.SUCCESS, runner.run(new CliSessionRequest(vault, 1)));
    assertTrue(console.vaultWasClosedBeforeDiscard);
    assertEquals(2, console.passwordPrompts);
    assertEquals(1, console.discardCalls);
    assertFalse(console.output.isEmpty());
  }

  @Test
  void failedReauthenticationCanRetryWithoutExposingFailureCause() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vault = directory.resolve("retry.pvlt");
    char[] master = "synthetic-master".toCharArray();
    try (VaultSession ignored = storage.create(vault, master)) {
      Arrays.fill(master, '\0');
    }

    ScriptedConsole console = new ScriptedConsole();
    console.passwords.add("synthetic-master".toCharArray());
    console.passwords.add("incorrect-synthetic".toCharArray());
    console.passwords.add("synthetic-master".toCharArray());
    console.visibleLines.add("si");
    console.lines.add(TimedLineResult.timedOut());
    console.lines.add(TimedLineResult.line("exit"));

    VaultCliCommands commands = new VaultCliCommands(console, storage, new PasswordGenerator());
    CliSessionRunner runner =
        new CliSessionRunner(
            console, storage, commands, new CliArgumentParser(), () -> console.ticks++);

    assertEquals(CliExitStatus.SUCCESS, runner.run(new CliSessionRequest(vault, 1)));
    assertEquals(3, console.passwordPrompts);
    assertTrue(console.output.contains("No se pudo abrir la bóveda."));
    assertFalse(console.output.stream().anyMatch(line -> line.contains("Decryption")));
    assertEquals(1, console.discardCalls);
  }

  @Test
  void existingTotpCommandsReuseSessionAndKeepVaultContentPrivate() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vault = directory.resolve("totp-session.pvlt");
    char[] master = "synthetic-master".toCharArray();
    byte[] id;
    try (VaultSession created = storage.create(vault, master)) {
      char[] entryPassword = "synthetic-entry-password".toCharArray();
      try {
        id =
            created
                .vault()
                .add("synthetic-service", "synthetic-user", entryPassword, "synthetic-notes");
      } finally {
        Arrays.fill(entryPassword, '\0');
      }
      byte[] oldSecret = new byte[20];
      Arrays.fill(oldSecret, (byte) 7);
      try {
        created.vault().setTotpSecret(id, oldSecret);
        created.save();
      } finally {
        Arrays.fill(oldSecret, (byte) 0);
      }
      Arrays.fill(master, '\0');
    }

    String idText = CliArgumentParser.formatId(id);
    Arrays.fill(id, (byte) 0);
    String encodedSecret = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
    ScriptedConsole console = new ScriptedConsole();
    console.passwords.add("synthetic-master".toCharArray());
    console.passwords.add(encodedSecret.toCharArray());
    console.passwords.add(encodedSecret.toCharArray());
    console.visibleLines.add("sí");
    console.visibleLines.add("sí");
    console.visibleLines.add("sí");
    console.lines.add(TimedLineResult.line("set-totp --id " + idText));
    console.lines.add(TimedLineResult.line("totp --id " + idText));
    console.lines.add(TimedLineResult.line("show --id " + idText));
    console.lines.add(TimedLineResult.line("remove-totp --id " + idText));
    console.lines.add(TimedLineResult.line("exit"));

    VaultCliCommands commands = new VaultCliCommands(console, storage, new PasswordGenerator());
    CliSessionRunner runner =
        new CliSessionRunner(
            console, storage, commands, new CliArgumentParser(), () -> console.ticks++);
    assertEquals(CliExitStatus.SUCCESS, runner.run(new CliSessionRequest(vault, 5)));

    assertEquals(3, console.passwordPrompts);
    assertTrue(console.privateOutput.stream().anyMatch(line -> line.contains("synthetic-service")));
    assertTrue(console.privateOutput.stream().anyMatch(line -> line.contains("synthetic-notes")));
    assertFalse(console.output.stream().anyMatch(line -> line.contains("synthetic-service")));
    assertTrue(
        console.secrets.stream()
            .anyMatch(secret -> Arrays.equals(secret, "synthetic-entry-password".toCharArray())));
    assertTrue(console.secrets.stream().anyMatch(secret -> secret.length == 6));

    char[] reopenPassword = "synthetic-master".toCharArray();
    try (VaultSession reopened = storage.open(vault, reopenPassword)) {
      assertTrue(reopened.vault().totpSecret(CliArgumentParser.parseId(idText)).isEmpty());
    } finally {
      Arrays.fill(reopenPassword, '\0');
    }
  }

  @Test
  void deleteInSessionPersistsOnlyAfterConfirmation() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vault = directory.resolve("delete-session.pvlt");
    char[] master = "synthetic-master".toCharArray();
    byte[] id;
    try (VaultSession created = storage.create(vault, master)) {
      char[] entryPassword = "synthetic-entry".toCharArray();
      try {
        id = created.vault().add("synthetic-service", "synthetic-user", entryPassword, "");
        created.save();
      } finally {
        Arrays.fill(entryPassword, '\0');
        Arrays.fill(master, '\0');
      }
    }

    String idText = CliArgumentParser.formatId(id);
    Arrays.fill(id, (byte) 0);
    ScriptedConsole console = new ScriptedConsole();
    console.passwords.add("synthetic-master".toCharArray());
    console.visibleLines.add("sí");
    console.lines.add(TimedLineResult.line("delete --id " + idText));
    console.lines.add(TimedLineResult.line("exit"));
    VaultCliCommands commands = new VaultCliCommands(console, storage, new PasswordGenerator());
    CliSessionRunner runner =
        new CliSessionRunner(
            console, storage, commands, new CliArgumentParser(), () -> console.ticks++);

    assertEquals(CliExitStatus.SUCCESS, runner.run(new CliSessionRequest(vault, 5)));
    assertTrue(console.privateOutput.stream().anyMatch(line -> line.contains("synthetic-service")));
    char[] reopenPassword = "synthetic-master".toCharArray();
    try (VaultSession reopened = storage.open(vault, reopenPassword)) {
      assertTrue(reopened.vault().list().isEmpty());
    } finally {
      Arrays.fill(reopenPassword, '\0');
    }
  }

  @Test
  void upgradeInSessionUsesExistingExplicitConfirmationAndPersists() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vault = directory.resolve("upgrade-session.pvlt");
    char[] master = "synthetic-master".toCharArray();
    try {
      SyntheticVaultFixtures.writeV1(vault, master);
    } finally {
      Arrays.fill(master, '\0');
    }

    ScriptedConsole console = new ScriptedConsole();
    console.passwords.add("synthetic-master".toCharArray());
    console.visibleLines.add("sí");
    console.lines.add(TimedLineResult.line("upgrade"));
    console.lines.add(TimedLineResult.line("exit"));
    VaultCliCommands commands = new VaultCliCommands(console, storage, new PasswordGenerator());
    CliSessionRunner runner =
        new CliSessionRunner(
            console, storage, commands, new CliArgumentParser(), () -> console.ticks++);

    assertEquals(CliExitStatus.SUCCESS, runner.run(new CliSessionRequest(vault, 5)));
    char[] reopenPassword = "synthetic-master".toCharArray();
    try (VaultSession reopened = storage.open(vault, reopenPassword)) {
      assertEquals(2, reopened.version());
    } finally {
      Arrays.fill(reopenPassword, '\0');
    }
  }

  @Test
  void invalidTotpInputIsRecoverableAndDoesNotLeakTheInput() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vault = directory.resolve("recoverable-error.pvlt");
    char[] master = "synthetic-master".toCharArray();
    byte[] id;
    try (VaultSession created = storage.create(vault, master)) {
      char[] password = "synthetic-entry".toCharArray();
      try {
        id = created.vault().add("service", "user", password, "");
        created.save();
      } finally {
        Arrays.fill(password, '\0');
        Arrays.fill(master, '\0');
      }
    }

    String idText = CliArgumentParser.formatId(id);
    Arrays.fill(id, (byte) 0);
    char[] invalidSecret = "SYNTHETIC-INVALID".toCharArray();
    ScriptedConsole console = new ScriptedConsole();
    console.passwords.add("synthetic-master".toCharArray());
    console.passwords.add(invalidSecret);
    console.passwords.add("SYNTHETIC-INVALID".toCharArray());
    console.lines.add(TimedLineResult.line("set-totp --id " + idText));
    console.lines.add(TimedLineResult.line("exit"));
    VaultCliCommands commands = new VaultCliCommands(console, storage, new PasswordGenerator());
    CliSessionRunner runner =
        new CliSessionRunner(
            console, storage, commands, new CliArgumentParser(), () -> console.ticks++);

    assertEquals(CliExitStatus.SUCCESS, runner.run(new CliSessionRequest(vault, 5)));
    assertTrue(console.output.contains("Los datos introducidos no son válidos."));
    assertFalse(console.output.stream().anyMatch(line -> line.contains("SYNTHETIC-INVALID")));
    assertTrue(Arrays.equals(new char[invalidSecret.length], invalidSecret));
  }

  @Test
  void commandThatCrossesDeadlineCompletesThenLocksBeforeNextCommand() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vault = directory.resolve("active-command-timeout.pvlt");
    char[] master = "synthetic-master".toCharArray();
    try (VaultSession ignored = storage.create(vault, master)) {
      Arrays.fill(master, '\0');
    }

    long timeout = java.time.Duration.ofMinutes(1).toNanos();
    long[] ticks = {0, 0, 0, timeout + 1, timeout + 1, timeout + 1, timeout + 1};
    AtomicInteger tickIndex = new AtomicInteger();
    ScriptedConsole console = new ScriptedConsole();
    console.passwords.add("synthetic-master".toCharArray());
    console.passwords.add("synthetic-master".toCharArray());
    console.lines.add(TimedLineResult.line("list"));
    console.lines.add(TimedLineResult.line("exit"));
    VaultCliCommands commands = new VaultCliCommands(console, storage, new PasswordGenerator());
    CliSessionRunner runner =
        new CliSessionRunner(
            console,
            storage,
            commands,
            new CliArgumentParser(),
            () -> ticks[Math.min(tickIndex.getAndIncrement(), ticks.length - 1)]);

    assertEquals(CliExitStatus.SUCCESS, runner.run(new CliSessionRequest(vault, 1)));
    assertEquals(2, console.passwordPrompts);
    assertTrue(console.privateOutput.contains("La bóveda está vacía."));
    assertTrue(console.output.contains("Bóveda bloqueada por inactividad."));
  }

  @Test
  void completedCommandResetsTheFullIdleInterval() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vault = directory.resolve("reset-timeout.pvlt");
    char[] master = "synthetic-master".toCharArray();
    try (VaultSession ignored = storage.create(vault, master)) {
      Arrays.fill(master, '\0');
    }

    long timeout = java.time.Duration.ofMinutes(2).toNanos();
    long[] now = {0};
    ScriptedConsole console = new ScriptedConsole();
    console.passwords.add("synthetic-master".toCharArray());
    console.lines.add(TimedLineResult.line("help"));
    console.lines.add(TimedLineResult.line("exit"));
    console.beforeTimedLine.add(() -> now[0] = timeout / 2);
    VaultCliCommands commands = new VaultCliCommands(console, storage, new PasswordGenerator());
    CliSessionRunner runner =
        new CliSessionRunner(console, storage, commands, new CliArgumentParser(), () -> now[0]);

    assertEquals(CliExitStatus.SUCCESS, runner.run(new CliSessionRequest(vault, 2)));
    assertEquals(List.of(timeout, timeout), console.requestedTimeouts);
    assertTrue(console.output.stream().anyMatch(line -> line.contains("Comandos de sesión")));
  }

  @Test
  void endOfInputAndInterruptionCloseTheSessionAndConsole() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vault = directory.resolve("terminal-end.pvlt");
    char[] master = "synthetic-master".toCharArray();
    try (VaultSession ignored = storage.create(vault, master)) {
      Arrays.fill(master, '\0');
    }

    ScriptedConsole eofConsole = new ScriptedConsole();
    eofConsole.passwords.add("synthetic-master".toCharArray());
    eofConsole.lines.add(TimedLineResult.endOfInput());
    CliSessionRunner eofRunner = newRunner(eofConsole, storage);
    assertEquals(CliExitStatus.CANCELLED, eofRunner.run(new CliSessionRequest(vault, 5)));
    assertTrue(eofConsole.closed);
    assertCanOpen(storage, vault);

    ScriptedConsole interruptedConsole = new ScriptedConsole();
    interruptedConsole.passwords.add("synthetic-master".toCharArray());
    interruptedConsole.interruptTimedRead = true;
    CliSessionRunner interruptedRunner = newRunner(interruptedConsole, storage);
    assertThrows(
        InterruptedException.class, () -> interruptedRunner.run(new CliSessionRequest(vault, 5)));
    assertTrue(interruptedConsole.closed);
    assertCanOpen(storage, vault);
  }

  private CliSessionRunner newRunner(ScriptedConsole console, VaultStorage storage) {
    VaultCliCommands commands = new VaultCliCommands(console, storage, new PasswordGenerator());
    return new CliSessionRunner(
        console, storage, commands, new CliArgumentParser(), () -> console.ticks++);
  }

  private static void assertCanOpen(VaultStorage storage, Path vault) throws Exception {
    char[] password = "synthetic-master".toCharArray();
    try (VaultSession ignored = storage.open(vault, password)) {
    } finally {
      Arrays.fill(password, '\0');
    }
  }

  private static final class ScriptedConsole implements CliConsole {
    private final Queue<char[]> passwords = new ArrayDeque<>();
    private final Queue<String> visibleLines = new ArrayDeque<>();
    private final Queue<TimedLineResult> lines = new ArrayDeque<>();
    private final Queue<Runnable> beforeTimedLine = new ArrayDeque<>();
    private final List<String> output = new ArrayList<>();
    private final List<String> privateOutput = new ArrayList<>();
    private final List<char[]> secrets = new ArrayList<>();
    private final List<Long> requestedTimeouts = new ArrayList<>();
    private int passwordPrompts;
    private int discardCalls;
    private long ticks;
    private boolean vaultWasClosedBeforeDiscard;
    private boolean interruptTimedRead;
    private boolean closed;
    private Runnable onDiscard = () -> {};

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public String readLine(String prompt) {
      return visibleLines.remove();
    }

    @Override
    public TimedLineResult readLineUntil(String prompt, long timeoutNanos)
        throws InterruptedException {
      if (interruptTimedRead) throw new InterruptedException();
      requestedTimeouts.add(timeoutNanos);
      if (!beforeTimedLine.isEmpty()) beforeTimedLine.remove().run();
      return lines.remove();
    }

    @Override
    public void discardPendingLine() {
      discardCalls++;
      onDiscard.run();
    }

    @Override
    public char[] readPassword(String prompt) {
      passwordPrompts++;
      return passwords.remove();
    }

    @Override
    public void printOutput(String message) {
      output.add(message);
    }

    @Override
    public void printError(String message) {
      output.add(message);
    }

    @Override
    public void printPrivate(String message) {
      privateOutput.add(message);
    }

    @Override
    public void printSecret(char[] secret) {
      secrets.add(secret.clone());
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
