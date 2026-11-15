package cl.germain.vault.storage;

import cl.germain.vault.model.Entry;
import cl.germain.vault.model.Vault;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Encodes the complete in-memory vault as the v1 payload. */
final class VaultPayloadCodec {
  private static final long UINT32_MAX = 0xffff_ffffL;
  private static final int ID_LENGTH_BYTES = 16;
  private static final int HEADER_LENGTH_BYTES = 50;
  private static final int GCM_TAG_LENGTH_BYTES = 16;
  private static final int MAX_FILE_LENGTH_BYTES = 16 * 1024 * 1024;
  private static final int MAX_PAYLOAD_LENGTH_BYTES =
      MAX_FILE_LENGTH_BYTES - HEADER_LENGTH_BYTES - GCM_TAG_LENGTH_BYTES;

  byte[] encode(Vault vault) {
    return encode(vault, 1);
  }

  byte[] encode(Vault vault, int version) {
    if (version != 1 && version != 2)
      throw new IllegalArgumentException("Unsupported payload version");
    Objects.requireNonNull(vault, "vault");
    List<Entry> entries = vault.list();
    if ((long) entries.size() > UINT32_MAX) {
      throw new IllegalArgumentException("Entry count exceeds payload format limit");
    }

    List<EncodedEntry> encodedEntries = new ArrayList<>(entries.size());
    byte[] payload = null;
    try {
      long totalLength = Integer.BYTES;
      for (Entry entry : entries) {
        EncodedEntry encoded = encodeEntry(entry, version);
        encodedEntries.add(encoded);
        totalLength = Math.addExact(totalLength, encoded.encodedLength());
        if (totalLength > MAX_PAYLOAD_LENGTH_BYTES) {
          throw new IllegalArgumentException("Vault payload exceeds maximum file size");
        }
      }

      payload = new byte[Math.toIntExact(totalLength)];
      ByteBuffer buffer = ByteBuffer.wrap(payload);
      buffer.putInt(entries.size());
      for (EncodedEntry entry : encodedEntries) {
        buffer.put(entry.id);
        buffer.putLong(entry.createdAtMillis);
        buffer.putLong(entry.updatedAtMillis);
        putField(buffer, entry.service);
        putField(buffer, entry.username);
        putField(buffer, entry.password);
        putField(buffer, entry.notes);
        if (version == 2) {
          buffer.putInt(entry.totpSecret.length);
          buffer.put(entry.totpSecret);
        }
      }
      return payload;
    } catch (ArithmeticException exception) {
      if (payload != null) {
        Arrays.fill(payload, (byte) 0);
      }
      throw new IllegalArgumentException("Vault payload length is invalid");
    } catch (RuntimeException exception) {
      if (payload != null) {
        Arrays.fill(payload, (byte) 0);
      }
      throw exception;
    } finally {
      encodedEntries.forEach(EncodedEntry::clear);
    }
  }

  private static EncodedEntry encodeEntry(Entry entry, int version) {
    long createdAtMillis = entry.createdAt().toEpochMilli();
    long updatedAtMillis = entry.updatedAt().toEpochMilli();
    char[] passwordChars = entry.password();
    byte[] id = null;
    byte[] service = null;
    byte[] username = null;
    byte[] password = null;
    byte[] notes = null;
    byte[] totpSecret = null;
    try {
      id = entry.id();
      service = encodeUtf8(entry.service());
      username = encodeUtf8(entry.username());
      password = encodeUtf8(passwordChars);
      notes = encodeUtf8(entry.notes());
      totpSecret = version == 2 ? entry.totpSecret() : new byte[0];
      EncodedEntry encodedEntry =
          new EncodedEntry(
              id,
              createdAtMillis,
              updatedAtMillis,
              service,
              username,
              password,
              notes,
              totpSecret,
              version == 2);
      id = null;
      service = null;
      username = null;
      password = null;
      notes = null;
      totpSecret = null;
      return encodedEntry;
    } finally {
      Arrays.fill(passwordChars, '\0');
      clear(id);
      clear(service);
      clear(username);
      clear(password);
      clear(notes);
      clear(totpSecret);
    }
  }

  private static void putField(ByteBuffer buffer, byte[] field) {
    buffer.putInt(field.length);
    buffer.put(field);
  }

  private static byte[] encodeUtf8(String value) {
    return StrictUtf8.encode(value);
  }

  private static byte[] encodeUtf8(char[] value) {
    return StrictUtf8.encode(value);
  }

  private static void clear(byte[] bytes) {
    if (bytes != null) {
      Arrays.fill(bytes, (byte) 0);
    }
  }

  private static final class EncodedEntry {
    private final byte[] id;
    private final long createdAtMillis;
    private final long updatedAtMillis;
    private final byte[] service;
    private final byte[] username;
    private final byte[] password;
    private final byte[] notes;
    private final byte[] totpSecret;
    private final boolean version2;

    private EncodedEntry(
        byte[] id,
        long createdAtMillis,
        long updatedAtMillis,
        byte[] service,
        byte[] username,
        byte[] password,
        byte[] notes,
        byte[] totpSecret,
        boolean version2) {
      this.id = id;
      this.createdAtMillis = createdAtMillis;
      this.updatedAtMillis = updatedAtMillis;
      this.service = service;
      this.username = username;
      this.password = password;
      this.notes = notes;
      this.totpSecret = totpSecret == null ? new byte[0] : totpSecret;
      this.version2 = version2;
    }

    private long encodedLength() {
      long fixed = 2L * Long.BYTES + ID_LENGTH_BYTES + 4L * Integer.BYTES;
      long fields = (long) service.length + username.length + password.length + notes.length;
      return Math.addExact(fixed + fields, version2 ? Integer.BYTES + totpSecret.length : 0);
    }

    private void clear() {
      Arrays.fill(id, (byte) 0);
      Arrays.fill(service, (byte) 0);
      Arrays.fill(username, (byte) 0);
      Arrays.fill(password, (byte) 0);
      Arrays.fill(notes, (byte) 0);
      Arrays.fill(totpSecret, (byte) 0);
    }
  }
}
