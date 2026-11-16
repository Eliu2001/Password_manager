package cl.germain.vault.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/** Stores only the selected vault path in the current user's local application data. */
public final class VaultPathConfig {
  private final Path file;

  public VaultPathConfig() {
    this.file = null;
  }

  public VaultPathConfig(Path localAppData) {
    Objects.requireNonNull(localAppData, "localAppData");
    file = localAppData.resolve("PasswordVault").resolve("vault-path.txt");
  }

  private static Path localAppDataPath() {
    String value = System.getenv("LOCALAPPDATA");
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("No está disponible la configuración local de Windows.");
    }
    return Path.of(value);
  }

  public Path file() {
    return file == null
        ? localAppDataPath().resolve("PasswordVault").resolve("vault-path.txt")
        : file;
  }

  /** Returns empty if no configuration exists; malformed content fails closed. */
  public java.util.Optional<Path> read() throws IOException {
    Path configFile = file();
    if (!Files.exists(configFile)) return java.util.Optional.empty();
    byte[] bytes = Files.readAllBytes(configFile);
    try {
      String text;
      try {
        text =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
      } catch (CharacterCodingException exception) {
        throw new IOException("La configuración de la bóveda no es válida.");
      }
      List<String> lines = text.lines().toList();
      if (lines.size() != 1 || lines.get(0).isBlank()) {
        throw new IOException("La configuración de la bóveda no es válida.");
      }
      Path path;
      try {
        path = Path.of(lines.get(0));
      } catch (RuntimeException exception) {
        throw new IOException("La configuración de la bóveda no es válida.");
      }
      if (!path.isAbsolute()) throw new IOException("La configuración de la bóveda no es válida.");
      return java.util.Optional.of(path);
    } finally {
      java.util.Arrays.fill(bytes, (byte) 0);
    }
  }

  public void write(Path vaultPath) throws IOException {
    Objects.requireNonNull(vaultPath, "vaultPath");
    if (!vaultPath.isAbsolute()
        || vaultPath.toString().contains("\n")
        || vaultPath.toString().contains("\r")) {
      throw new IllegalArgumentException("Vault path must be absolute and single-line");
    }
    Path configFile = file();
    Path parent = configFile.getParent();
    Files.createDirectories(parent);
    Path temporary = Files.createTempFile(parent, ".vault-path-", ".tmp");
    byte[] bytes = (vaultPath.toString() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
    try {
      Files.write(temporary, bytes);
      try {
        Files.move(
            temporary,
            configFile,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException exception) {
        Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      java.util.Arrays.fill(bytes, (byte) 0);
      Files.deleteIfExists(temporary);
    }
  }
}
