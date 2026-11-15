package cl.germain.vault.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PasswordEncodingTest {

  @Test
  void passesUtf8BytesWithoutModifyingCallerCharactersAndClearsBytesAfterUse() {
    char[] password = {'v', 'a', 'u', 'l', 't'};
    byte[][] observedBytes = new byte[1][];

    PasswordEncoding.withUtf8Password(
        password,
        bytes -> {
          observedBytes[0] = bytes;
          assertArrayEquals("vault".getBytes(StandardCharsets.UTF_8), bytes);
          return null;
        });

    assertArrayEquals(new char[] {'v', 'a', 'u', 'l', 't'}, password);
    assertTrue(allZero(observedBytes[0]));
  }

  @Test
  void normalizesBeforeEncodingAndClearsBytesWhenConsumerFails() {
    char[] password = {'e', '\u0301'};
    byte[][] observedBytes = new byte[1][];

    assertThrows(
        IllegalStateException.class,
        () ->
            PasswordEncoding.withUtf8Password(
                password,
                bytes -> {
                  observedBytes[0] = bytes;
                  assertArrayEquals("\u00e9".getBytes(StandardCharsets.UTF_8), bytes);
                  throw new IllegalStateException("test failure");
                }));

    assertArrayEquals(new char[] {'e', '\u0301'}, password);
    assertTrue(allZero(observedBytes[0]));
  }

  @Test
  void rejectsMalformedUtf16InsteadOfReplacingIt() {
    char[] password = {'\ud800'};

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> PasswordEncoding.withUtf8Password(password, bytes -> bytes.length));
    assertEquals("Password contains malformed Unicode", failure.getMessage());
    assertTrue(failure.getCause() instanceof java.nio.charset.CharacterCodingException);
    assertArrayEquals(new char[] {'\ud800'}, password);
  }

  private static boolean allZero(byte[] bytes) {
    return Arrays.equals(bytes, new byte[bytes.length]);
  }
}
