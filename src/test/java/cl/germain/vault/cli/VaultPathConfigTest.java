package cl.germain.vault.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultPathConfigTest {
  @TempDir Path directory;

  @Test
  void readsAndWritesOnlyAnAbsolutePath() throws Exception {
    VaultPathConfig config = new VaultPathConfig(directory);
    Path expected = directory.resolve("vault with spaces.pvlt").toAbsolutePath();
    assertFalse(config.read().isPresent());
    config.write(expected);
    assertEquals(expected, config.read().orElseThrow());
    assertEquals(expected + System.lineSeparator(), Files.readString(config.file()));
  }

  @Test
  void rejectsRelativeOrMultipleConfiguredLines() throws Exception {
    VaultPathConfig config = new VaultPathConfig(directory);
    Files.createDirectories(config.file().getParent());
    Files.writeString(config.file(), "relative.pvlt\n", StandardCharsets.UTF_8);
    assertThrows(IOException.class, config::read);
    Files.writeString(config.file(), directory.resolve("one.pvlt") + "\nextra\n");
    assertThrows(IOException.class, config::read);
  }

  @Test
  void rejectsMalformedUtf8() throws Exception {
    VaultPathConfig config = new VaultPathConfig(directory);
    Files.createDirectories(config.file().getParent());
    Files.write(config.file(), new byte[] {(byte) 0xc3, (byte) 0x28});
    assertThrows(IOException.class, config::read);
  }

  @Test
  void rejectsMultilinePathBeforeWriting() {
    VaultPathConfig config = new VaultPathConfig(directory);
    assertThrows(IllegalArgumentException.class, () -> config.write(directory.resolve("x\ny")));
  }
}
