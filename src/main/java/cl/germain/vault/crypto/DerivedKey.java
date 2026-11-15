package cl.germain.vault.crypto;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/** Owns and clears the 256-bit key produced by the KDF. */
public final class DerivedKey implements AutoCloseable {
  private final byte[] keyBytes;
  private boolean closed;

  /** Takes ownership of the supplied key array; callers must not retain it. */
  DerivedKey(byte[] keyBytes) {
    Objects.requireNonNull(keyBytes, "keyBytes");
    if (keyBytes.length != Argon2idKdf.KEY_LENGTH_BYTES) {
      throw new IllegalArgumentException("Derived key must be 32 bytes");
    }
    this.keyBytes = keyBytes;
  }

  /**
   * Provides package-internal operations temporary access to the key. The function must not retain
   * the supplied array.
   */
  synchronized <T> T useBytes(Function<byte[], T> operation) {
    Objects.requireNonNull(operation, "operation");
    if (closed) {
      throw new IllegalStateException("Derived key is closed");
    }
    return operation.apply(keyBytes);
  }

  @Override
  public synchronized void close() {
    if (!closed) {
      Arrays.fill(keyBytes, (byte) 0);
      closed = true;
    }
  }
}
