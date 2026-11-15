package cl.germain.vault.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class Argon2idKdfTest {
  private static final Argon2Parameters MINIMUM_COST = new Argon2Parameters(19_456, 2, 1);

  @Test
  void derivesRepeatableThirtyTwoByteKeysAndChangesForDifferentInputs() {
    char[] password = {'p', 'a', 's', 's'};
    char[] otherPassword = {'p', 'a', 's', 't'};
    byte[] salt = new byte[16];
    byte[] otherSalt = salt.clone();
    otherSalt[0] = 1;
    Argon2Parameters otherParameters = new Argon2Parameters(19_456, 3, 1);
    byte[] first = Argon2idKdf.derive(password, salt, MINIMUM_COST);
    byte[] repeated = Argon2idKdf.derive(password, salt, MINIMUM_COST);
    byte[] changedPassword = Argon2idKdf.derive(otherPassword, salt, MINIMUM_COST);
    byte[] changedSalt = Argon2idKdf.derive(password, otherSalt, MINIMUM_COST);
    byte[] changedParameters = Argon2idKdf.derive(password, salt, otherParameters);

    try {
      assertEquals(32, first.length);
      assertArrayEquals(first, repeated);
      assertFalse(Arrays.equals(first, changedPassword));
      assertFalse(Arrays.equals(first, changedSalt));
      assertFalse(Arrays.equals(first, changedParameters));
    } finally {
      Arrays.fill(first, (byte) 0);
      Arrays.fill(repeated, (byte) 0);
      Arrays.fill(changedPassword, (byte) 0);
      Arrays.fill(changedSalt, (byte) 0);
      Arrays.fill(changedParameters, (byte) 0);
      Arrays.fill(password, '\0');
      Arrays.fill(otherPassword, '\0');
    }
  }

  @Test
  void rejectsInvalidInputsBeforeDerivation() {
    char[] password = {'x'};
    assertThrows(
        NullPointerException.class, () -> Argon2idKdf.derive(null, new byte[16], MINIMUM_COST));
    assertThrows(
        NullPointerException.class, () -> Argon2idKdf.derive(password, null, MINIMUM_COST));
    assertThrows(
        NullPointerException.class, () -> Argon2idKdf.derive(password, new byte[16], null));
    assertThrows(
        IllegalArgumentException.class,
        () -> Argon2idKdf.derive(password, new byte[15], MINIMUM_COST));
    assertThrows(
        IllegalArgumentException.class,
        () -> Argon2idKdf.derive(new char[0], new byte[16], MINIMUM_COST));
  }

  @Test
  void normalizesEquivalentPasswordFormsWithoutChangingCallerArrays() {
    char[] decomposed = {'e', '\u0301'};
    char[] composed = {'\u00e9'};
    byte[] decomposedKey = Argon2idKdf.derive(decomposed, new byte[16], MINIMUM_COST);
    byte[] composedKey = Argon2idKdf.derive(composed, new byte[16], MINIMUM_COST);

    try {
      assertArrayEquals(decomposedKey, composedKey);
      assertArrayEquals(new char[] {'e', '\u0301'}, decomposed);
      assertArrayEquals(new char[] {'\u00e9'}, composed);
    } finally {
      Arrays.fill(decomposedKey, (byte) 0);
      Arrays.fill(composedKey, (byte) 0);
      Arrays.fill(decomposed, '\0');
      Arrays.fill(composed, '\0');
    }
  }
}
