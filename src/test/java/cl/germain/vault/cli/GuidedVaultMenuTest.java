package cl.germain.vault.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cl.germain.vault.storage.VaultSession;
import cl.germain.vault.storage.VaultStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

  private static final class RecordingConsole implements CliConsole {
    private final List<String> privateOutput = new ArrayList<>();

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public String readLine(String prompt) {
      throw new AssertionError("Unexpected visible input");
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
    public void printError(String message) {}

    @Override
    public void printPrivate(String message) throws IOException {
      privateOutput.add(message);
    }

    @Override
    public void printSecret(char[] secret) {
      throw new AssertionError("Listing must not reveal secrets");
    }
  }
}
