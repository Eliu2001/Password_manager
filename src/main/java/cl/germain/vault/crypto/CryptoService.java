package cl.germain.vault.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** High-level cryptographic operations backed by module-owned randomness. */
public final class CryptoService {
  static final int SALT_LENGTH_BYTES = 16;

  private final SecureRandom secureRandom = new SecureRandom();

  /** Generates a new 128-bit Argon2 salt. */
  public byte[] generateSalt() {
    byte[] salt = new byte[SALT_LENGTH_BYTES];
    secureRandom.nextBytes(salt);
    return salt;
  }

  /** Derives an owned AES-256 key from the master password and salt. */
  public DerivedKey deriveKey(char[] password, byte[] salt, Argon2Parameters parameters) {
    return new DerivedKey(Argon2idKdf.derive(password, salt, parameters));
  }

  /** Encrypts with AES-256-GCM and a fresh module-generated 96-bit nonce. */
  public EncryptedData encrypt(DerivedKey key, byte[] plaintext, byte[] associatedData) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(plaintext, "plaintext");
    Objects.requireNonNull(associatedData, "associatedData");

    byte[] nonce = new byte[EncryptedData.NONCE_LENGTH_BYTES];
    secureRandom.nextBytes(nonce);
    return key.useBytes(keyBytes -> {
      byte[] ciphertextAndTag = null;
      try {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
            Cipher.ENCRYPT_MODE,
            new SecretKeySpec(keyBytes, "AES"),
            new GCMParameterSpec(EncryptedData.GCM_TAG_LENGTH_BYTES * 8, nonce));
        cipher.updateAAD(associatedData);
        ciphertextAndTag = cipher.doFinal(plaintext);
        return new EncryptedData(nonce, ciphertextAndTag);
      } catch (GeneralSecurityException e) {
        throw new IllegalStateException("Encryption failed");
      } finally {
        if (ciphertextAndTag != null) {
          Arrays.fill(ciphertextAndTag, (byte) 0);
        }
      }
    });
  }

  /** Decrypts only after AES-GCM authenticates the complete ciphertext. */
  public byte[] decrypt(DerivedKey key, EncryptedData encryptedData, byte[] associatedData)
      throws DecryptionException {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(encryptedData, "encryptedData");
    Objects.requireNonNull(associatedData, "associatedData");

    byte[] nonce = encryptedData.nonce();
    byte[] ciphertextAndTag = encryptedData.ciphertextAndTag();
    try {
      return key.useBytes(keyBytes -> {
        try {
          Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
          cipher.init(
              Cipher.DECRYPT_MODE,
              new SecretKeySpec(keyBytes, "AES"),
              new GCMParameterSpec(EncryptedData.GCM_TAG_LENGTH_BYTES * 8, nonce));
          cipher.updateAAD(associatedData);
          return cipher.doFinal(ciphertextAndTag);
        } catch (GeneralSecurityException e) {
          throw new DecryptionFailure();
        }
      });
    } catch (DecryptionFailure failure) {
      throw new DecryptionException();
    } finally {
      Arrays.fill(nonce, (byte) 0);
      Arrays.fill(ciphertextAndTag, (byte) 0);
    }
  }

  private static final class DecryptionFailure extends RuntimeException {}
}
