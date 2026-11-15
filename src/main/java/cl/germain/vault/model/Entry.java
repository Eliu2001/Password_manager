package cl.germain.vault.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/** A vault entry. Password characters are owned and cleared by this instance. */
public final class Entry {
  static final int ID_LENGTH_BYTES = 16;

  private final byte[] id;
  private final Instant createdAt;
  private Instant updatedAt;
  private String service;
  private String username;
  private char[] password;
  private byte[] totpSecret;
  private String notes;
  private boolean closed;

  Entry(
      byte[] id,
      Instant createdAt,
      Instant updatedAt,
      String service,
      String username,
      char[] password,
      String notes) {
    Objects.requireNonNull(id, "id");
    if (id.length != ID_LENGTH_BYTES) {
      throw new IllegalArgumentException("Entry ID must be 16 bytes");
    }
    this.id = id.clone();
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    this.service = requireNonEmpty(service, "service");
    this.username = requireNonEmpty(username, "username");
    this.password = copyNonEmpty(password);
    this.notes = notes == null ? "" : notes;
  }

  /** Restores an entry with its persisted identity and timestamps. */
  public static Entry restore(
      byte[] id,
      Instant createdAt,
      Instant updatedAt,
      String service,
      String username,
      char[] password,
      String notes) {
    return new Entry(id, createdAt, updatedAt, service, username, password, notes);
  }

  public static Entry restore(
      byte[] id,
      Instant createdAt,
      Instant updatedAt,
      String service,
      String username,
      char[] password,
      String notes,
      byte[] totpSecret) {
    Entry entry = new Entry(id, createdAt, updatedAt, service, username, password, notes);
    try {
      if (totpSecret != null && totpSecret.length != 0) entry.setTotpSecret(totpSecret);
      return entry;
    } catch (RuntimeException failure) {
      entry.clearPassword();
      throw failure;
    }
  }

  public byte[] id() {
    return id.clone();
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public String service() {
    return service;
  }

  public String username() {
    return username;
  }

  /** Returns a caller-owned copy. The caller must clear it when finished. */
  public synchronized char[] password() {
    ensureOpen();
    return password.clone();
  }

  public String notes() {
    return notes;
  }

  public synchronized byte[] totpSecret() {
    ensureOpen();
    return totpSecret == null ? null : totpSecret.clone();
  }

  synchronized void setTotpSecret(byte[] secret) {
    ensureOpen();
    Objects.requireNonNull(secret, "secret");
    if (secret.length < 20 || secret.length > 64)
      throw new IllegalArgumentException("TOTP secret length is invalid");
    byte[] copy = secret.clone();
    clearTotpSecret();
    totpSecret = copy;
  }

  synchronized boolean removeTotpSecret() {
    ensureOpen();
    if (totpSecret == null) return false;
    clearTotpSecret();
    return true;
  }

  private void clearTotpSecret() {
    if (totpSecret != null) Arrays.fill(totpSecret, (byte) 0);
    totpSecret = null;
  }

  synchronized void update(
      String newService,
      String newUsername,
      char[] newPassword,
      String newNotes,
      Instant modifiedAt) {
    ensureOpen();
    String validatedService = requireNonEmpty(newService, "service");
    String validatedUsername = requireNonEmpty(newUsername, "username");
    String validatedNotes = newNotes == null ? "" : newNotes;
    Objects.requireNonNull(modifiedAt, "modifiedAt");
    char[] replacementCopy = copyNonEmpty(newPassword);

    Arrays.fill(password, '\0');
    password = replacementCopy;
    service = validatedService;
    username = validatedUsername;
    notes = validatedNotes;
    updatedAt = modifiedAt;
  }

  synchronized void clearPassword() {
    if (!closed) {
      Arrays.fill(password, '\0');
      clearTotpSecret();
      closed = true;
    }
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("Entry is closed");
    }
  }

  private static String requireNonEmpty(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    return value;
  }

  private static char[] copyNonEmpty(char[] value) {
    Objects.requireNonNull(value, "password");
    if (value.length == 0) {
      throw new IllegalArgumentException("password must not be empty");
    }
    return value.clone();
  }
}
