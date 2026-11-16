package cl.germain.vault.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cl.germain.vault.model.Entry;
import cl.germain.vault.storage.VaultSession;
import cl.germain.vault.storage.VaultStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuidedPasswordUpdateTest {
  @TempDir Path directory;

  @Test
  void updatesOnlyPasswordAndPersistsIt() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vaultPath = directory.resolve("update.pvlt");
    char[] master = "synthetic master password".toCharArray();
    char[] initialPassword = "synthetic old password".toCharArray();
    char[] first = "synthetic new password".toCharArray();
    char[] second = "synthetic new password".toCharArray();
    byte[] id;
    try (VaultSession session = storage.create(vaultPath, master)) {
      id =
          session
              .vault()
              .add("Synthetic Service", "synthetic-user", initialPassword, "synthetic note");
      session.save();
      ScriptedConsole console = new ScriptedConsole(first, second);
      new VaultCliCommands(console, storage).updatePasswordInSession(id, session);

      Entry entry = session.vault().get(id).orElseThrow();
      assertArrayEquals(id, entry.id());
      assertEquals("Synthetic Service", entry.service());
      assertEquals("synthetic-user", entry.username());
      assertEquals("synthetic note", entry.notes());
      assertArrayEquals("synthetic new password".toCharArray(), entry.password());
      assertTrue(console.privateOutput.contains("Contraseña actualizada correctamente."));
      assertTrue(console.output.isEmpty());
      assertFalse(
          console.output.stream().anyMatch(value -> value.contains("synthetic new password")));
      assertCleared(first);
      assertCleared(second);
    }
    try (VaultSession reopened = storage.open(vaultPath, master)) {
      Entry entry = reopened.vault().get(id).orElseThrow();
      assertArrayEquals("synthetic new password".toCharArray(), entry.password());
    } finally {
      Arrays.fill(master, '\0');
      Arrays.fill(initialPassword, '\0');
      Arrays.fill(id, (byte) 0);
    }
  }

  @Test
  void mismatchDoesNotChangeOrSaveAndClearsInputs() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vaultPath = directory.resolve("mismatch.pvlt");
    char[] master = "synthetic master password".toCharArray();
    char[] initialPassword = "synthetic old password".toCharArray();
    char[] first = "synthetic candidate one".toCharArray();
    char[] second = "synthetic candidate two".toCharArray();
    byte[] id;
    try (VaultSession session = storage.create(vaultPath, master)) {
      id =
          session
              .vault()
              .add("Synthetic Service", "synthetic-user", initialPassword, "synthetic note");
      session.save();
      byte[] before = Files.readAllBytes(vaultPath);
      ScriptedConsole console = new ScriptedConsole(first, second);
      new VaultCliCommands(console, storage).updatePasswordInSession(id, session);
      assertArrayEquals(before, Files.readAllBytes(vaultPath));
      assertArrayEquals(initialPassword, session.vault().get(id).orElseThrow().password());
      assertTrue(
          console.output.contains("Las contraseñas no coinciden; no se realizaron cambios."));
      assertTrue(console.privateOutput.isEmpty());
      assertCleared(first);
      assertCleared(second);
      Arrays.fill(before, (byte) 0);
    } finally {
      Arrays.fill(master, '\0');
      Arrays.fill(initialPassword, '\0');
    }
  }

  @Test
  void cancellationDoesNotChangeOrSaveAndClearsFirstInput() throws Exception {
    VaultStorage storage = new VaultStorage();
    Path vaultPath = directory.resolve("cancel.pvlt");
    char[] master = "synthetic master password".toCharArray();
    char[] initialPassword = "synthetic old password".toCharArray();
    char[] first = "synthetic candidate password".toCharArray();
    byte[] id;
    try (VaultSession session = storage.create(vaultPath, master)) {
      id =
          session
              .vault()
              .add("Synthetic Service", "synthetic-user", initialPassword, "synthetic note");
      session.save();
      byte[] before = Files.readAllBytes(vaultPath);
      ScriptedConsole console = new ScriptedConsole(first, null);
      new VaultCliCommands(console, storage).updatePasswordInSession(id, session);
      assertArrayEquals(before, Files.readAllBytes(vaultPath));
      assertArrayEquals(initialPassword, session.vault().get(id).orElseThrow().password());
      assertTrue(console.output.contains("Operación cancelada."));
      assertCleared(first);
      Arrays.fill(before, (byte) 0);
    } finally {
      Arrays.fill(master, '\0');
      Arrays.fill(initialPassword, '\0');
    }
  }

  private static void assertCleared(char[] value) {
    for (char character : value) assertEquals('\0', character);
  }

  private static final class ScriptedConsole implements CliConsole {
    private final Queue<char[]> passwords = new LinkedList<>();
    private final List<String> output = new ArrayList<>();
    private final List<String> privateOutput = new ArrayList<>();

    private ScriptedConsole(char[]... passwords) {
      this.passwords.addAll(Arrays.asList(passwords));
    }

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
      throw new AssertionError("Password update must not reveal the new password");
    }
  }
}
