package cl.germain.vault.crypto;

import java.nio.CharBuffer;
import java.text.Normalizer;
import java.util.Objects;

/** NFC normalization helpers for master-password characters. */
final class PasswordNormalizer {

  private PasswordNormalizer() {}

  /**
   * Returns {@code password} itself when it is already NFC. Otherwise, returns a new array
   * containing the NFC form; the caller must clear that array.
   */
  static char[] normalizeIfNeeded(char[] password) {
    Objects.requireNonNull(password, "password");
    if (Normalizer.isNormalized(CharBuffer.wrap(password), Normalizer.Form.NFC)) {
      return password;
    }

    String normalized = Normalizer.normalize(CharBuffer.wrap(password), Normalizer.Form.NFC);
    return normalized.toCharArray();
  }
}
