package cl.germain.vault.storage;

import cl.germain.vault.model.Vault;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;

/** Parses a complete v1 payload without exposing partially restored entries. */
final class VaultPayloadParser {
  private static final int ID_LENGTH_BYTES = 16;
  private static final int FIXED_ENTRY_LENGTH_BYTES =
      ID_LENGTH_BYTES + 2 * Long.BYTES + 4 * Integer.BYTES;
  private static final int MAX_FILE_LENGTH_BYTES = 16 * 1024 * 1024;
  private static final int MAX_PAYLOAD_LENGTH_BYTES = MAX_FILE_LENGTH_BYTES - 50 - 16;

  Vault parse(byte[] payload) {
    return parse(payload, 1);
  }

  Vault parse(byte[] payload, int version) {
    if (version != 1 && version != 2) throw invalidPayload();
    if (payload == null
        || payload.length < Integer.BYTES
        || payload.length > MAX_PAYLOAD_LENGTH_BYTES) {
      throw invalidPayload();
    }

    ByteBuffer buffer = ByteBuffer.wrap(payload);
    long count = Integer.toUnsignedLong(buffer.getInt());
    if (count > buffer.remaining() / FIXED_ENTRY_LENGTH_BYTES) {
      throw invalidPayload();
    }

    Vault vault = new Vault();
    boolean parsed = false;
    try {
      for (long index = 0; index < count; index++) {
        byte[] id = new byte[ID_LENGTH_BYTES];
        char[] password = null;
        byte[] serviceBytes = null;
        byte[] usernameBytes = null;
        byte[] passwordBytes = null;
        byte[] notesBytes = null;
        byte[] totpSecret = null;
        try {
          buffer.get(id);
          Instant createdAt = Instant.ofEpochMilli(buffer.getLong());
          Instant updatedAt = Instant.ofEpochMilli(buffer.getLong());

          serviceBytes = readField(buffer, true);
          usernameBytes = readField(buffer, true);
          passwordBytes = readField(buffer, true);
          notesBytes = readField(buffer, false);
          if (version == 2) {
            if (buffer.remaining() < Integer.BYTES) throw invalidPayload();
            long length = Integer.toUnsignedLong(buffer.getInt());
            if (length != 0 && (length < 20 || length > 64)) throw invalidPayload();
            if (length > buffer.remaining()) throw invalidPayload();
            totpSecret = new byte[(int) length];
            buffer.get(totpSecret);
          }

          String service = StrictUtf8.decodeString(serviceBytes, true);
          String username = StrictUtf8.decodeString(usernameBytes, true);
          password = StrictUtf8.decodeChars(passwordBytes, true);
          String notes = StrictUtf8.decodeString(notesBytes, false);
          vault.restoreEntry(
              id, createdAt, updatedAt, service, username, password, notes, totpSecret);
        } finally {
          Arrays.fill(id, (byte) 0);
          clear(password);
          clear(serviceBytes);
          clear(usernameBytes);
          clear(passwordBytes);
          clear(notesBytes);
          clear(totpSecret);
        }
      }
      if (buffer.hasRemaining()) {
        throw invalidPayload();
      }
      parsed = true;
      return vault;
    } catch (RuntimeException exception) {
      throw invalidPayload();
    } finally {
      if (!parsed) {
        vault.close();
      }
    }
  }

  private static byte[] readField(ByteBuffer buffer, boolean required) {
    if (buffer.remaining() < Integer.BYTES) {
      throw invalidPayload();
    }
    long fieldLength = Integer.toUnsignedLong(buffer.getInt());
    if ((required && fieldLength == 0)
        || fieldLength > buffer.remaining()
        || fieldLength > MAX_PAYLOAD_LENGTH_BYTES) {
      throw invalidPayload();
    }
    byte[] field = new byte[(int) fieldLength];
    buffer.get(field);
    return field;
  }

  private static void clear(byte[] bytes) {
    if (bytes != null) {
      Arrays.fill(bytes, (byte) 0);
    }
  }

  private static void clear(char[] characters) {
    if (characters != null) {
      Arrays.fill(characters, '\0');
    }
  }

  private static IllegalArgumentException invalidPayload() {
    return new IllegalArgumentException("Vault payload is invalid");
  }
}
