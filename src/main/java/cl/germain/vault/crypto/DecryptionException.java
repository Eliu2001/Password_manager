package cl.germain.vault.crypto;

/** Generic, cause-free failure for authenticated decryption errors. */
public final class DecryptionException extends Exception {
  DecryptionException() {
    super("Decryption failed");
  }
}
