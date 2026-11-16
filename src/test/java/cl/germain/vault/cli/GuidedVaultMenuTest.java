package cl.germain.vault.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cl.germain.vault.storage.VaultSession;
import cl.germain.vault.storage.VaultStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuidedVaultMenuTest {
  @TempDir Path directory;

  @Test
  void listingShowsApproximateAgeWithoutSavingTheVault() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vaultPath = directory.resolve("menu.pvlt");
    char[] master = "synthetic master password".toCharArray();
    char[] entryPassword = "synthetic entry password".toCharArray();
    try (VaultSession session = storage.create(vaultPath, master)) {
      session.vault().add("Synthetic Service", "synthetic-user", entryPassword, "synthetic note");
      session.save();
      byte[] before = Files.readAllBytes(vaultPath);
      RecordingConsole console = new RecordingConsole();
      GuidedVaultMenu menu =
          new GuidedVaultMenu(
              console, new VaultCliCommands(console, storage), vaultPath, new ConsoleTheme(false));

      menu.step(session, "2");

      assertTrue(
          console.privateOutput.stream().anyMatch(line -> line.contains("Última actualización:")));
      assertTrue(
          console.privateOutput.stream().anyMatch(line -> line.contains("Synthetic Service")));
      assertTrue(
          console.privateOutput.stream().anyMatch(line -> line.contains("Servicio | Usuario")));
      assertTrue(console.privateOutput.stream().noneMatch(line -> line.contains("synthetic note")));
      assertTrue(
          console.privateOutput.stream()
              .noneMatch(line -> line.contains("synthetic entry password")));
      assertArrayEquals(before, Files.readAllBytes(vaultPath));
      Arrays.fill(before, (byte) 0);
    } finally {
      Arrays.fill(master, '\0');
      Arrays.fill(entryPassword, '\0');
    }
  }

  @Test
  void selectionUsesCurrentListIndexAndRevealsOnlyTheSelectedEntryOnRequest() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vaultPath = directory.resolve("indexed.pvlt");
    char[] master = "synthetic master password".toCharArray();
    char[] firstPassword = "synthetic first entry password".toCharArray();
    char[] secondPassword = "synthetic second entry password".toCharArray();
    try (VaultSession session = storage.create(vaultPath, master)) {
      session.vault().add("First Service", "first-user", firstPassword, "first note");
      session.vault().add("Second Service", "second-user", secondPassword, "second note");
      session.save();
      RecordingConsole console = new RecordingConsole();
      console.lines.add("2");
      console.lines.add("si");
      GuidedVaultMenu menu =
          new GuidedVaultMenu(
              console, new VaultCliCommands(console, storage), vaultPath, new ConsoleTheme(false));

      menu.step(session, "4");

      assertTrue(console.privateOutput.stream().anyMatch(line -> line.contains("Second Service")));
      assertArrayEquals(secondPassword, console.revealedSecrets.getFirst());
      assertFalse(
          console.privateOutput.stream()
              .anyMatch(line -> line.contains("synthetic first entry password")));
      assertFalse(
          console.privateOutput.stream()
              .anyMatch(line -> line.contains("synthetic second entry password")));
      Arrays.fill(console.revealedSecrets.getFirst(), '\0');
    } finally {
      Arrays.fill(master, '\0');
      Arrays.fill(firstPassword, '\0');
      Arrays.fill(secondPassword, '\0');
    }
  }

  @Test
  void invalidIndexAndCancelledMultiMatchSearchDoNotMutateOrDispatch() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vaultPath = directory.resolve("selection-cancel.pvlt");
    char[] master = "synthetic master password".toCharArray();
    char[] entryPassword = "synthetic entry password".toCharArray();
    try (VaultSession session = storage.create(vaultPath, master)) {
      session.vault().add("GitHub Personal", "first-user", entryPassword, "private note");
      session.vault().add("GitHub Work", "second-user", entryPassword, "private note");
      session.save();
      byte[] before = Files.readAllBytes(vaultPath);
      RecordingConsole console = new RecordingConsole();
      console.lines.add("99");
      GuidedVaultMenu menu =
          new GuidedVaultMenu(
              console, new VaultCliCommands(console, storage), vaultPath, new ConsoleTheme(false));

      menu.step(session, "4");

      assertTrue(console.errors.stream().anyMatch(line -> line.contains("inválido")));
      assertTrue(console.revealedSecrets.isEmpty());
      assertArrayEquals(before, Files.readAllBytes(vaultPath));

      console.lines.add("GITHUB");
      console.lines.add("2");
      console.lines.add("5");
      menu.step(session, "3");

      assertTrue(console.privateOutput.stream().anyMatch(line -> line.contains("GitHub Personal")));
      assertTrue(console.privateOutput.stream().anyMatch(line -> line.contains("GitHub Work")));
      assertFalse(console.privateOutput.stream().anyMatch(line -> line.contains("private note")));
      assertArrayEquals(before, Files.readAllBytes(vaultPath));
      Arrays.fill(before, (byte) 0);
    } finally {
      Arrays.fill(master, '\0');
      Arrays.fill(entryPassword, '\0');
    }
  }

  private static final class RecordingConsole implements CliConsole {
    private final List<String> privateOutput = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<char[]> revealedSecrets = new ArrayList<>();
    private final Queue<String> lines = new ArrayDeque<>();

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public String readLine(String prompt) {
      if (lines.isEmpty()) throw new AssertionError("Unexpected visible prompt: " + prompt);
      return lines.remove();
    }

    @Override
    public TimedLineResult readLineUntil(String prompt, long timeoutNanos) {
      throw new AssertionError("Unexpected timed input");
    }

    @Override
    public void discardPendingLine() {}

    @Override
    public char[] readPassword(String prompt) {
      throw new AssertionError("Unexpected password input");
    }

    @Override
    public void printOutput(String message) {}

    @Override
    public void printError(String message) {
      errors.add(message);
    }

    @Override
    public void printPrivate(String message) throws IOException {
      privateOutput.add(message);
    }

    @Override
    public void printSecret(char[] secret) {
      revealedSecrets.add(secret.clone());
    }
  }
}
