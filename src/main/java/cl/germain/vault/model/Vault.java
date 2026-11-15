package cl.germain.vault.model;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.NoSuchElementException;

/** Ordered in-memory collection of vault entries. */
public final class Vault implements AutoCloseable {
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final List<Entry> entries = new ArrayList<>();
    private boolean closed;

    public Vault() {
        this(new SecureRandom(), Clock.systemUTC());
    }

    public Vault(SecureRandom secureRandom, Clock clock) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Adds an entry at the end of the ordered vault and returns its ID. */
    public synchronized byte[] add(String service, String username, char[] password, String notes) {
        ensureOpen();
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        if (service.isEmpty() || username.isEmpty() || password.length == 0) {
            throw new IllegalArgumentException("Required entry fields must not be empty");
        }

        byte[] id = new byte[Entry.ID_LENGTH_BYTES];
        do {
            secureRandom.nextBytes(id);
        } while (containsId(id));

        Instant createdAt = Instant.ofEpochMilli(clock.instant().toEpochMilli());
        Entry entry = new Entry(id, createdAt, createdAt, service, username, password, notes);
        entries.add(entry);
        return id.clone();
    }

    /** Restores a persisted entry without changing its ID, order, or timestamps. */
    public synchronized void restoreEntry(byte[] id, Instant createdAt, Instant updatedAt,
                                          String service, String username, char[] password,
                                          String notes) {
        ensureOpen();
        validateId(id);
        if (containsId(id)) {
            throw new IllegalArgumentException("Duplicate entry ID");
        }
        entries.add(new Entry(id, createdAt, updatedAt, service, username, password, notes));
    }

    public synchronized void restoreEntry(byte[] id, Instant createdAt, Instant updatedAt,
                                          String service, String username, char[] password,
                                          String notes, byte[] totpSecret) {
        ensureOpen();
        validateId(id);
        if (containsId(id)) throw new IllegalArgumentException("Duplicate entry ID");
        entries.add(Entry.restore(id, createdAt, updatedAt, service, username, password, notes, totpSecret));
    }

    /** Returns the matching entry, if present. The entry exposes no mutators. */
    public synchronized Optional<Entry> get(byte[] id) {
        ensureOpen();
        validateId(id);
        return entries.stream().filter(entry -> Arrays.equals(entry.id(), id)).findFirst();
    }

    /** Returns an immutable snapshot of entries in insertion order. */
    public synchronized List<Entry> list() {
        ensureOpen();
        return List.copyOf(entries);
    }

    /** Updates an entry without changing its position or identity. */
    public synchronized void update(byte[] id, String service, String username,
                                    char[] password, String notes) {
        ensureOpen();
        Entry entry = get(id).orElseThrow(() -> new NoSuchElementException("Entry not found"));
        entry.update(service, username, password, notes,
                Instant.ofEpochMilli(clock.instant().toEpochMilli()));
    }

    public synchronized void setTotpSecret(byte[] id, byte[] secret) {
        ensureOpen();
        Entry entry = get(id).orElseThrow(() -> new NoSuchElementException("Entry not found"));
        entry.setTotpSecret(secret);
    }

    public synchronized Optional<byte[]> totpSecret(byte[] id) {
        ensureOpen();
        Entry entry = get(id).orElseThrow(() -> new NoSuchElementException("Entry not found"));
        byte[] secret = entry.totpSecret();
        return Optional.ofNullable(secret);
    }

    public synchronized boolean removeTotpSecret(byte[] id) {
        ensureOpen();
        Entry entry = get(id).orElseThrow(() -> new NoSuchElementException("Entry not found"));
        return entry.removeTotpSecret();
    }

    /** Removes an entry by ID and clears its owned password copy. */
    public synchronized boolean remove(byte[] id) {
        ensureOpen();
        validateId(id);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (Arrays.equals(entry.id(), id)) {
                entries.remove(i);
                entry.clearPassword();
                return true;
            }
        }
        return false;
    }

    private boolean containsId(byte[] id) {
        return entries.stream().anyMatch(entry -> Arrays.equals(entry.id(), id));
    }

    private static void validateId(byte[] id) {
        Objects.requireNonNull(id, "id");
        if (id.length != Entry.ID_LENGTH_BYTES) {
            throw new IllegalArgumentException("Entry ID must be 16 bytes");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Vault is closed");
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            entries.forEach(Entry::clearPassword);
            entries.clear();
            closed = true;
        }
    }
}
