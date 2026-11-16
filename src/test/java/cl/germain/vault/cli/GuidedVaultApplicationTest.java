package cl.germain.vault.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cl.germain.vault.generator.PasswordGenerator;
import cl.germain.vault.storage.VaultSession;
import cl.germain.vault.storage.VaultStorage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuidedVaultApplicationTest {
  @TempDir Path directory;

  @Test
  void createWizardPersistsOnlyPathAndTransfersCreatedSessionWithoutReauthentication()
      throws Exception {
    Path vaultPath = directory.resolve("created vault.pvlt");
    VaultStorage storage = new VaultStorage();
    TestConsole console = new TestConsole();
    console.lines.addAll(List.of("1", vaultPath.toString()));
    console.passwords.add("synthetic-master".toCharArray());
    console.passwords.add("synthetic-master".toCharArray());
    console.timedLines.add(TimedLineResult.line("0"));
    CliSessionRunner runner = runner(console, storage);
    GuidedVaultApplication application =
        new GuidedVaultApplication(console, storage, runner, new VaultPathConfig(directory));

    assertEquals(0, application.run());

    assertTrue(Files.isRegularFile(vaultPath));
    Path configPath = directory.resolve("PasswordVault/vault-path.txt");
    assertEquals(
        vaultPath.toString() + System.lineSeparator(),
        Files.readString(configPath, StandardCharsets.UTF_8));
    assertEquals(2, console.passwordPrompts);
    assertTrue(console.passwordInputs.stream().allMatch(GuidedVaultApplicationTest::isCleared));
    assertTrue(console.output.stream().anyMatch(line -> line.contains("PASSWORD VAULT")));
  }

  @Test
  void openWizardAuthenticatesOnceAndRemembersPathOnlyAfterSuccessfulOpen() throws Exception {
    Path vaultPath = directory.resolve("existing.pvlt");
    VaultStorage storage = new VaultStorage();
    char[] master = "synthetic-master".toCharArray();
    try (VaultSession ignored = storage.create(vaultPath, master)) {
      // Creation fixture only.
    } finally {
      Arrays.fill(master, '\0');
    }

    TestConsole console = new TestConsole();
    console.lines.addAll(List.of("2", vaultPath.toString()));
    console.passwords.add("synthetic-master".toCharArray());
    console.timedLines.add(TimedLineResult.line("0"));
    GuidedVaultApplication application =
        new GuidedVaultApplication(
            console, storage, runner(console, storage), new VaultPathConfig(directory));

    assertEquals(0, application.run());

    assertEquals(1, console.passwordPrompts);
    assertEquals(
        vaultPath.toString() + System.lineSeparator(),
        Files.readString(directory.resolve("PasswordVault/vault-path.txt")));
    assertTrue(console.passwordInputs.stream().allMatch(GuidedVaultApplicationTest::isCleared));
  }

  @Test
  void invalidOpenPathCanBeCancelledWithoutCreatingConfigurationOrReadingSecrets()
      throws Exception {
    Path missingVault = directory.resolve("missing.pvlt");
    VaultStorage storage = new VaultStorage();
    TestConsole console = new TestConsole();
    console.lines.addAll(List.of("2", missingVault.toString(), "0"));
    GuidedVaultApplication application =
        new GuidedVaultApplication(
            console, storage, runner(console, storage), new VaultPathConfig(directory));

    assertEquals(CliExitStatus.CANCELLED.code(), application.run());

    assertEquals(0, console.passwordPrompts);
    assertFalse(Files.exists(missingVault));
    assertFalse(Files.exists(directory.resolve("PasswordVault/vault-path.txt")));
    assertTrue(console.errors.stream().anyMatch(line -> line.contains("requisitos")));
  }

  private static CliSessionRunner runner(TestConsole console, VaultStorage storage) {
    CliArgumentParser parser = new CliArgumentParser();
    VaultCliCommands commands = new VaultCliCommands(console, storage, new PasswordGenerator());
    return new CliSessionRunner(console, storage, commands, parser, () -> 0L);
  }

  private static boolean isCleared(char[] value) {
    for (char character : value) {
      if (character != '\0') return false;
    }
    return true;
  }

  private static final class TestConsole implements CliConsole {
    private final Queue<String> lines = new ArrayDeque<>();
    private final Queue<TimedLineResult> timedLines = new ArrayDeque<>();
    private final Queue<char[]> passwords = new ArrayDeque<>();
    private final List<char[]> passwordInputs = new ArrayList<>();
    private final List<String> output = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private int passwordPrompts;

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public String readLine(String prompt) {
      if (lines.isEmpty()) throw new AssertionError("Unexpected line prompt: " + prompt);
      return lines.remove();
    }

    @Override
    public TimedLineResult readLineUntil(String prompt, long timeoutNanos) {
      if (timedLines.isEmpty()) throw new AssertionError("Unexpected timed prompt: " + prompt);
      return timedLines.remove();
    }

    @Override
    public void discardPendingLine() {}

    @Override
    public char[] readPassword(String prompt) {
      passwordPrompts++;
      if (passwords.isEmpty()) throw new AssertionError("Unexpected password prompt: " + prompt);
      char[] value = passwords.remove();
      passwordInputs.add(value);
      return value;
    }

    @Override
    public void printOutput(String message) {
      output.add(message);
    }

    @Override
    public void printError(String message) {
      errors.add(message);
    }

    @Override
    public void printPrivate(String message) {
      output.add(message);
    }

    @Override
    public void printSecret(char[] secret) throws IOException {
      output.add("[secret output]");
    }
  }
}
