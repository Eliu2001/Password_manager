package cl.germain.vault.storage;

import cl.germain.vault.crypto.Argon2Parameters;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Immutable parser/encoder for the v1 50-byte file header. */
final class VaultHeader {
  static final int HEADER_LENGTH_BYTES = 50;
  static final int AAD_LENGTH_BYTES = 34;
  static final int SALT_LENGTH_BYTES = 16;
  static final int NONCE_LENGTH_BYTES = 12;
  static final int MIN_CIPHERTEXT_LENGTH_BYTES = 16;
  static final int VERSION = 1;
  private static final byte[] MAGIC = "PVLT".getBytes(StandardCharsets.US_ASCII);
  private static final long UINT32_MAX = 0xffff_ffffL;

  private final byte[] encoded;
  private final Argon2Parameters parameters;
  private final byte[] salt;
  private final byte[] nonce;
  private final long ciphertextLength;

  private VaultHeader(
      byte[] encoded,
      Argon2Parameters parameters,
      byte[] salt,
      byte[] nonce,
      long ciphertextLength) {
    this.encoded = encoded.clone();
    this.parameters = parameters;
    this.salt = salt.clone();
    this.nonce = nonce.clone();
    this.ciphertextLength = ciphertextLength;
  }

  static byte[] createAadPrefix(Argon2Parameters parameters, byte[] salt) {
    return createAadPrefix(parameters, salt, 1);
  }

  static byte[] createAadPrefix(Argon2Parameters parameters, byte[] salt, int version) {
    Objects.requireNonNull(parameters, "parameters");
    Objects.requireNonNull(salt, "salt");
    if (salt.length != SALT_LENGTH_BYTES) {
      throw new IllegalArgumentException("Salt must be 16 bytes");
    }
    ByteBuffer prefix = ByteBuffer.allocate(AAD_LENGTH_BYTES).order(ByteOrder.BIG_ENDIAN);
    prefix.put(MAGIC);
    if (version != 1 && version != 2)
      throw new IllegalArgumentException("Unsupported vault version");
    prefix.putShort((short) version);
    prefix.putInt(parameters.memoryKiB());
    prefix.putInt(parameters.iterations());
    prefix.putInt(parameters.parallelism());
    prefix.put(salt);
    return prefix.array();
  }

  static byte[] assemble(byte[] aadPrefix, byte[] nonce, long ciphertextLength) {
    Objects.requireNonNull(aadPrefix, "aadPrefix");
    Objects.requireNonNull(nonce, "nonce");
    if (aadPrefix.length != AAD_LENGTH_BYTES || !hasSupportedPrefix(aadPrefix)) {
      throw new IllegalArgumentException("Header AAD prefix is invalid");
    }
    if (nonce.length != NONCE_LENGTH_BYTES) {
      throw new IllegalArgumentException("Nonce must be 12 bytes");
    }
    if (ciphertextLength < MIN_CIPHERTEXT_LENGTH_BYTES || ciphertextLength > UINT32_MAX) {
      throw new IllegalArgumentException("Ciphertext length is outside the format range");
    }
    ByteBuffer header = ByteBuffer.allocate(HEADER_LENGTH_BYTES).order(ByteOrder.BIG_ENDIAN);
    header.put(aadPrefix);
    header.put(nonce);
    header.putInt((int) ciphertextLength);
    return header.array();
  }

  static VaultHeader parse(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    if (bytes.length != HEADER_LENGTH_BYTES) {
      throw new IllegalArgumentException("Vault header must be 50 bytes");
    }
    byte[] encoded = bytes.clone();
    ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
    byte[] magic = new byte[MAGIC.length];
    buffer.get(magic);
    if (!Arrays.equals(magic, MAGIC)) {
      throw new IllegalArgumentException("Vault header magic is invalid");
    }

    int version = Short.toUnsignedInt(buffer.getShort());
    if (version != 1 && version != 2) {
      throw new UnsupportedVaultFormatException();
    }

    long memoryKiB = Integer.toUnsignedLong(buffer.getInt());
    long iterations = Integer.toUnsignedLong(buffer.getInt());
    long parallelism = Integer.toUnsignedLong(buffer.getInt());
    byte[] salt = new byte[SALT_LENGTH_BYTES];
    byte[] nonce = new byte[NONCE_LENGTH_BYTES];
    buffer.get(salt);
    buffer.get(nonce);
    long ciphertextLength = Integer.toUnsignedLong(buffer.getInt());
    if (ciphertextLength < MIN_CIPHERTEXT_LENGTH_BYTES) {
      clear(salt);
      clear(nonce);
      throw new IllegalArgumentException("Ciphertext length is invalid");
    }

    try {
      if (memoryKiB > Integer.MAX_VALUE
          || iterations > Integer.MAX_VALUE
          || parallelism > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("KDF parameter exceeds supported range");
      }
      Argon2Parameters parameters =
          new Argon2Parameters((int) memoryKiB, (int) iterations, (int) parallelism);
      return new VaultHeader(encoded, parameters, salt, nonce, ciphertextLength);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Vault header KDF parameters are invalid");
    } finally {
      clear(salt);
      clear(nonce);
      clear(magic);
      clear(encoded);
    }
  }

  byte[] encoded() {
    return encoded.clone();
  }

  byte[] aad() {
    return Arrays.copyOf(encoded, AAD_LENGTH_BYTES);
  }

  Argon2Parameters parameters() {
    return parameters;
  }

  byte[] salt() {
    return salt.clone();
  }

  byte[] nonce() {
    return nonce.clone();
  }

  long ciphertextLength() {
    return ciphertextLength;
  }

  int version() {
    return Short.toUnsignedInt(ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN).getShort(4));
  }

  void validateFileLength(long completeFileLength) {
    if (completeFileLength > 16L * 1024 * 1024
        || completeFileLength != HEADER_LENGTH_BYTES + ciphertextLength) {
      throw new IllegalArgumentException("Vault file length is invalid");
    }
  }

  private static boolean hasSupportedPrefix(byte[] prefix) {
    ByteBuffer buffer = ByteBuffer.wrap(prefix).order(ByteOrder.BIG_ENDIAN);
    byte[] magic = new byte[MAGIC.length];
    buffer.get(magic);
    int version = Short.toUnsignedInt(buffer.getShort());
    boolean valid = Arrays.equals(magic, MAGIC) && (version == 1 || version == 2);
    clear(magic);
    return valid;
  }

  private static void clear(byte[] bytes) {
    Arrays.fill(bytes, (byte) 0);
  }
}
