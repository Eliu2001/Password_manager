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
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Stores only the selected vault path in the current user's local application data. */
public final class VaultPathConfig {
  private final Path file;
  private final boolean linux;

  public VaultPathConfig() {
    this.file = null;
    this.linux = isLinux(System.getProperty("os.name", ""));
  }

  public VaultPathConfig(Path localAppData) {
    Objects.requireNonNull(localAppData, "localAppData");
    file = localAppData.resolve("PasswordVault").resolve("vault-path.txt");
    linux = false;
  }

  VaultPathConfig(String osName, Map<String, String> environment) {
    this.file = resolveConfigFile(osName, environment);
    this.linux = isLinux(osName);
  }

  private static boolean isLinux(String osName) {
    return osName.toLowerCase(java.util.Locale.ROOT).contains("linux");
  }

  static Path resolveConfigFile(String osName, Map<String, String> environment) {
    Objects.requireNonNull(osName, "osName");
    Objects.requireNonNull(environment, "environment");
    if (isLinux(osName)) {
      String xdgConfigHome = environment.get("XDG_CONFIG_HOME");
      if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
        Path xdgPath = Path.of(xdgConfigHome);
        if (xdgPath.isAbsolute())
          return xdgPath.resolve("password-vault").resolve("vault-path.txt");
      }
      String home = environment.get("HOME");
      if (home == null || home.isBlank() || !Path.of(home).isAbsolute()) {
        throw new IllegalStateException(
            "No se pudo determinar la carpeta de configuración de Linux.");
      }
      return Path.of(home).resolve(".config").resolve("password-vault").resolve("vault-path.txt");
    }
    if (osName.toLowerCase(java.util.Locale.ROOT).contains("windows")) {
      String value = environment.get("LOCALAPPDATA");
      if (value == null || value.isBlank()) {
        throw new IllegalStateException("No está disponible la configuración local de Windows.");
      }
      return Path.of(value).resolve("PasswordVault").resolve("vault-path.txt");
    }
    throw new IllegalStateException("El asistente guiado solo está disponible en Windows y Linux.");
  }

  private Path resolvedFile() {
    if (file != null) return file;
    return resolveConfigFile(System.getProperty("os.name", ""), System.getenv());
  }

  private void secureLinuxPermissions(Path configFile, boolean includeFile) throws IOException {
    if (!linux) return;
    Path parent = configFile.getParent();
    Files.createDirectories(parent);
    if (Files.isSymbolicLink(parent) || (includeFile && Files.isSymbolicLink(configFile))) {
      throw new IOException("La configuración local no puede ser un enlace simbólico.");
    }
    setPermissions(parent, PosixFilePermissions.fromString("rwx------"));
    if (includeFile && Files.exists(configFile)) {
      setPermissions(configFile, PosixFilePermissions.fromString("rw-------"));
    }
  }

  private static void setPermissions(Path path, Set<PosixFilePermission> permissions)
      throws IOException {
    try {
      Files.setPosixFilePermissions(path, permissions);
    } catch (UnsupportedOperationException exception) {
      throw new IOException("El sistema de archivos no admite permisos POSIX requeridos.");
    }
  }

  public Path file() {
    return resolvedFile();
  }

  /** Returns empty if no configuration exists; malformed content fails closed. */
  public java.util.Optional<Path> read() throws IOException {
    Path configFile = resolvedFile();
    if (linux && Files.exists(configFile)) secureLinuxPermissions(configFile, true);
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
    Path configFile = resolvedFile();
    Path parent = configFile.getParent();
    Files.createDirectories(parent);
    if (linux) secureLinuxPermissions(configFile, true);
    Path temporary =
        linux
            ? Files.createTempFile(
                parent,
                ".vault-path-",
                ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            : Files.createTempFile(parent, ".vault-path-", ".tmp");
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
      if (linux) setPermissions(configFile, PosixFilePermissions.fromString("rw-------"));
    } finally {
      java.util.Arrays.fill(bytes, (byte) 0);
      Files.deleteIfExists(temporary);
    }
  }
}
