package cl.germain.vault.storage;

import cl.germain.vault.crypto.Argon2Parameters;
import cl.germain.vault.crypto.CryptoService;
import cl.germain.vault.model.Vault;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Non-sensitive deterministic-format fixtures for cross-package integration tests. */
public final class SyntheticVaultFixtures {
  private SyntheticVaultFixtures() {}

  public static byte[] writeV1(Path path, char[] master) throws Exception {
    CryptoService crypto = new CryptoService();
    Argon2Parameters parameters = new Argon2Parameters(19_456, 2, 1);
    byte[] salt = crypto.generateSalt();
    byte[] prefix = VaultHeader.createAadPrefix(parameters, salt, 1);
    byte[] id;
    byte[] payload = null, nonce = null, encrypted = null, header = null, file = null;
    try (Vault vault = new Vault()) {
      char[] password = "synthetic fixture entry".toCharArray();
      try {
        id = vault.add("fixture service", "fixture user", password, "fixture note");
      } finally {
        Arrays.fill(password, '\0');
      }
      payload = new VaultPayloadCodec().encode(vault, 1);
      try (var key = crypto.deriveKey(master, salt, parameters)) {
        var result = crypto.encrypt(key, payload, prefix);
        nonce = result.nonce();
        encrypted = result.ciphertextAndTag();
      }
      header = VaultHeader.assemble(prefix, nonce, encrypted.length);
      file = VaultStorage.join(header, encrypted);
      Files.write(path, file);
      PrivateFilePermissions.ensurePrivateFile(path);
      return id;
    } finally {
      VaultStorage.clear(salt, prefix, payload, nonce, encrypted, header, file);
    }
  }
}
