package cl.germain.vault.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EntryTest {
  @Test
  void preservesIdentityAndTimestampsWithDefensiveIdCopy() {
    byte[] id = new byte[16];
    id[0] = 7;
    Instant created = Instant.parse("2026-01-02T03:04:05Z");
    Instant updated = created.plusSeconds(10);
    char[] password = {'s', 'a', 'm', 'p', 'l', 'e'};

    Entry entry = Entry.restore(id, created, updated, "service", "user", password, "note");
    Arrays.fill(id, (byte) 0);

    try {
      assertEquals(7, entry.id()[0]);
      byte[] exposedId = entry.id();
      Arrays.fill(exposedId, (byte) 0);
      assertEquals(7, entry.id()[0]);
      assertEquals(created, entry.createdAt());
      assertEquals(updated, entry.updatedAt());
      char[] passwordCopy = entry.password();
      try {
        assertArrayEquals(password, passwordCopy);
      } finally {
        Arrays.fill(passwordCopy, '\0');
      }
    } finally {
      entry.clearPassword();
      Arrays.fill(password, '\0');
    }
  }

  @Test
  void vaultPreservesOrderAndUpdatesTimestampById() {
    Instant rawFirstMoment = Instant.parse("2026-02-03T04:05:06.789456123Z");
    Instant firstMoment = Instant.ofEpochMilli(rawFirstMoment.toEpochMilli());
    AtomicReference<Instant> now = new AtomicReference<>(rawFirstMoment);
    Clock clock =
        new Clock() {
          @Override
          public ZoneId getZone() {
            return ZoneId.of("UTC");
          }

          @Override
          public Clock withZone(ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return now.get();
          }
        };

    try (Vault vault = new Vault(new SecureRandom(), clock)) {
      char[] firstPassword = {'o', 'n', 'e'};
      char[] secondPassword = {'t', 'w', 'o'};
      byte[] firstId = vault.add("service", "user", firstPassword, null);
      byte[] secondId = vault.add("service", "user", secondPassword, "");
      Arrays.fill(firstPassword, '\0');
      Arrays.fill(secondPassword, '\0');

      assertEquals(16, firstId.length);
      assertNotEquals(Arrays.toString(firstId), Arrays.toString(secondId));
      assertEquals(2, vault.list().size());
      assertTrue(vault.get(firstId).isPresent());
      assertArrayEquals(firstId, vault.list().get(0).id());
      assertEquals(firstMoment, vault.get(firstId).orElseThrow().createdAt());
      assertEquals(firstMoment, vault.get(firstId).orElseThrow().updatedAt());

      Instant rawModified = rawFirstMoment.plusSeconds(90).plusNanos(987_654);
      Instant modified = Instant.ofEpochMilli(rawModified.toEpochMilli());
      now.set(rawModified);
      char[] replacement = {'n', 'e', 'w'};
      vault.update(firstId, "renamed", "updated-user", replacement, "updated");
      Arrays.fill(replacement, '\0');

      Entry updated = vault.get(firstId).orElseThrow();
      assertEquals("renamed", updated.service());
      assertEquals("updated-user", updated.username());
      assertEquals(firstMoment, updated.createdAt());
      assertEquals(modified, updated.updatedAt());
      assertArrayEquals(firstId, vault.list().get(0).id());
      assertTrue(vault.remove(secondId));
      assertFalse(vault.remove(secondId));
      char[] unusedPassword = {'x'};
      try {
        assertThrows(
            NoSuchElementException.class,
            () -> vault.update(secondId, "service", "user", unusedPassword, ""));
      } finally {
        Arrays.fill(unusedPassword, '\0');
      }
    }
  }

  @Test
  void vaultOwnsPasswordCopiesAndClearsThemOnReplaceRemoveAndClose() throws Exception {
    try (Vault vault = new Vault()) {
      char[] callerPassword = {'f', 'i', 'r', 's', 't'};
      byte[] id = vault.add("service", "user", callerPassword, "");
      Entry entry = vault.get(id).orElseThrow();
      char[] storedAfterAdd = internalPassword(entry);

      Arrays.fill(callerPassword, 'x');
      char[] exposed = entry.password();
      try {
        assertArrayEquals(new char[] {'f', 'i', 'r', 's', 't'}, exposed);
        exposed[0] = 'z';
      } finally {
        Arrays.fill(exposed, '\0');
        Arrays.fill(callerPassword, '\0');
      }

      char[] replacement = {'s', 'e', 'c', 'o', 'n', 'd'};
      vault.update(id, "service", "user", replacement, "");
      assertArrayEquals(new char[storedAfterAdd.length], storedAfterAdd);
      Arrays.fill(replacement, '\0');
      char[] storedAfterUpdate = internalPassword(entry);

      assertTrue(vault.remove(id));
      assertArrayEquals(new char[storedAfterUpdate.length], storedAfterUpdate);
      assertThrows(IllegalStateException.class, entry::password);

      char[] finalPassword = {'c', 'l', 'o', 's', 'e'};
      byte[] finalId = vault.add("service", "another-user", finalPassword, "");
      Entry finalEntry = vault.get(finalId).orElseThrow();
      char[] storedBeforeClose = internalPassword(finalEntry);
      Arrays.fill(finalPassword, '\0');
      vault.close();
      assertArrayEquals(new char[storedBeforeClose.length], storedBeforeClose);
      assertThrows(IllegalStateException.class, finalEntry::password);
    }
  }

  @Test
  void vaultOwnsTotpCopiesAndClearsThemOnReplaceRemoveDeleteAndClose() throws Exception {
    byte[] first = new byte[20];
    Arrays.fill(first, (byte) 7);
    byte[] replacement = new byte[32];
    Arrays.fill(replacement, (byte) 9);
    byte[] id;
    Vault vault = new Vault();
    char[] password = {'p'};
    id = vault.add("service", "user", password, "");
    Arrays.fill(password, '\0');
    Entry entry = vault.get(id).orElseThrow();
    vault.setTotpSecret(id, first);
    byte[] stored = internalTotp(entry);
    Arrays.fill(first, (byte) 0);
    byte[] copy = vault.totpSecret(id).orElseThrow();
    assertEquals(20, copy.length);
    copy[0] = 0;
    assertEquals(7, stored[0]);
    vault.setTotpSecret(id, replacement);
    assertArrayEquals(new byte[20], stored);
    Arrays.fill(replacement, (byte) 0);
    byte[] nextStored = internalTotp(entry);
    assertTrue(vault.removeTotpSecret(id));
    assertArrayEquals(new byte[32], nextStored);
    assertFalse(vault.removeTotpSecret(id));
    vault.setTotpSecret(id, new byte[20]);
    byte[] beforeDelete = internalTotp(entry);
    assertTrue(vault.remove(id));
    assertArrayEquals(new byte[20], beforeDelete);
    assertThrows(IllegalStateException.class, entry::totpSecret);
    char[] finalPassword = {'q'};
    byte[] finalId = vault.add("service", "other", finalPassword, "");
    Arrays.fill(finalPassword, '\0');
    Entry finalEntry = vault.get(finalId).orElseThrow();
    byte[] closeSecret = new byte[20];
    Arrays.fill(closeSecret, (byte) 3);
    vault.setTotpSecret(finalId, closeSecret);
    byte[] storedOnClose = internalTotp(finalEntry);
    Arrays.fill(closeSecret, (byte) 0);
    vault.close();
    assertArrayEquals(new byte[20], storedOnClose);
    Arrays.fill(copy, (byte) 0);
    Arrays.fill(finalId, (byte) 0);
  }

  @Test
  void restorePreservesIdAndRejectsDuplicateIds() {
    Instant created = Instant.ofEpochMilli(1_800_000_000_123L);
    Instant updated = Instant.ofEpochMilli(1_800_000_100_456L);
    byte[] id = new byte[16];
    id[15] = 42;
    char[] firstPassword = {'a'};
    char[] duplicatePassword = {'b'};

    try (Vault vault = new Vault()) {
      vault.restoreEntry(id, created, updated, "service", "user", firstPassword, "");
      Arrays.fill(firstPassword, '\0');

      Entry restored = vault.get(id).orElseThrow();
      assertArrayEquals(id, restored.id());
      assertEquals(created, restored.createdAt());
      assertEquals(updated, restored.updatedAt());
      assertThrows(
          IllegalArgumentException.class,
          () -> vault.restoreEntry(id, created, updated, "other", "user", duplicatePassword, ""));
      assertEquals(1, vault.list().size());
    } finally {
      Arrays.fill(firstPassword, '\0');
      Arrays.fill(duplicatePassword, '\0');
    }
  }

  @Test
  void vaultRejectsEmptyRequiredFieldsWithoutAddingEntry() {
    try (Vault vault = new Vault()) {
      char[] password = {'x'};
      try {
        assertThrows(IllegalArgumentException.class, () -> vault.add("", "user", password, ""));
        assertThrows(IllegalArgumentException.class, () -> vault.add("service", "", password, ""));
        assertThrows(
            IllegalArgumentException.class, () -> vault.add("service", "user", new char[0], ""));
        assertTrue(vault.list().isEmpty());
      } finally {
        Arrays.fill(password, '\0');
      }
    }
  }

  private static char[] internalPassword(Entry entry) throws Exception {
    Field passwordField = Entry.class.getDeclaredField("password");
    passwordField.setAccessible(true);
    return (char[]) passwordField.get(entry);
  }

  private static byte[] internalTotp(Entry entry) throws Exception {
    Field field = Entry.class.getDeclaredField("totpSecret");
    field.setAccessible(true);
    return (byte[]) field.get(entry);
  }
}
