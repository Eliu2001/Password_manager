package cl.germain.vault.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
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

  @Test
  void resolvesLinuxConfigUsingXdgWhenAbsolute() {
    Path expected = directory.resolve("xdg").resolve("password-vault").resolve("vault-path.txt");
    assertEquals(
        expected,
        VaultPathConfig.resolveConfigFile(
            "Linux",
            Map.of(
                "XDG_CONFIG_HOME",
                directory.resolve("xdg").toString(),
                "HOME",
                directory.toString())));
  }

  @Test
  void fallsBackToHomeConfigWhenXdgIsMissingOrRelative() {
    Path expected = directory.resolve(".config/password-vault/vault-path.txt");
    assertEquals(
        expected, VaultPathConfig.resolveConfigFile("Linux", Map.of("HOME", directory.toString())));
    assertEquals(
        expected,
        VaultPathConfig.resolveConfigFile(
            "Linux", Map.of("XDG_CONFIG_HOME", "relative", "HOME", directory.toString())));
  }

  @Test
  void rejectsLinuxResolutionWithoutUsableHome() {
    assertThrows(
        IllegalStateException.class, () -> VaultPathConfig.resolveConfigFile("Linux", Map.of()));
    assertThrows(
        IllegalStateException.class,
        () -> VaultPathConfig.resolveConfigFile("Linux", Map.of("HOME", "relative")));
  }

  @Test
  void preservesWindowsConfigLocation() {
    assertEquals(
        Path.of("C:/Users/example/AppData/Local/PasswordVault/vault-path.txt"),
        VaultPathConfig.resolveConfigFile(
            "Windows 11", Map.of("LOCALAPPDATA", "C:/Users/example/AppData/Local")));
  }

  @Test
  void linuxConfigAndFileHavePrivatePosixPermissions() throws Exception {
    Assumptions.assumeTrue(
        directory.getFileSystem().supportedFileAttributeViews().contains("posix"));
    Path home = directory.resolve("home");
    Files.createDirectories(home);
    VaultPathConfig config = new VaultPathConfig("Linux", Map.of("HOME", home.toString()));
    Path expectedVault = home.resolve("vault.pvlt");
    config.write(expectedVault);

    assertEquals(
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE),
        Files.getPosixFilePermissions(config.file().getParent()));
    assertEquals(
        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        Files.getPosixFilePermissions(config.file()));
    assertTrue(config.read().isPresent());
  }

  @Test
  void tightensPermissionsOnExistingLinuxConfig() throws Exception {
    Assumptions.assumeTrue(
        directory.getFileSystem().supportedFileAttributeViews().contains("posix"));
    Path home = directory.resolve("home");
    Path configDirectory = home.resolve(".config/password-vault");
    Path configFile = configDirectory.resolve("vault-path.txt");
    Files.createDirectories(configDirectory);
    Files.writeString(configFile, home.resolve("vault.pvlt") + System.lineSeparator());
    Files.setPosixFilePermissions(
        configDirectory,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ));
    Files.setPosixFilePermissions(
        configFile,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ));
    VaultPathConfig config = new VaultPathConfig("Linux", Map.of("HOME", home.toString()));

    assertTrue(config.read().isPresent());
    assertEquals(
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE),
        Files.getPosixFilePermissions(configDirectory));
    assertEquals(
        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        Files.getPosixFilePermissions(configFile));
  }

  @Test
  void rejectsSymbolicLinkAsLinuxConfigFile() throws Exception {
    Assumptions.assumeTrue(
        directory.getFileSystem().supportedFileAttributeViews().contains("posix"));
    Path home = directory.resolve("home");
    Path configDirectory = home.resolve(".config/password-vault");
    Files.createDirectories(configDirectory);
    Path target = directory.resolve("other-config.txt");
    Files.writeString(target, home.resolve("vault.pvlt") + System.lineSeparator());
    Path configFile = configDirectory.resolve("vault-path.txt");
    Files.createSymbolicLink(configFile, target);
    VaultPathConfig config = new VaultPathConfig("Linux", Map.of("HOME", home.toString()));

    assertThrows(IOException.class, config::read);
  }
}
