package cl.germain.vault.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class StrictUtf8Test {
  @Test
  void preservesUnicodeExactlyWithoutNormalization() {
    String decomposed = "e\u0301";
    byte[] encoded = StrictUtf8.encode(decomposed);
    try {
      assertArrayEquals(new byte[] {'e', (byte) 0xcc, (byte) 0x81}, encoded);
      assertEquals(decomposed, StrictUtf8.decodeString(encoded, true));
    } finally {
      Arrays.fill(encoded, (byte) 0);
    }
  }

  @Test
  void rejectsMalformedUtf8AndUnpairedUtf16() {
    byte[] malformed = {(byte) 0xc0, (byte) 0xaf};
    char[] unpairedSurrogate = {'\uD800'};
    try {
      assertThrows(IllegalArgumentException.class, () -> StrictUtf8.decodeString(malformed, false));
      assertThrows(IllegalArgumentException.class, () -> StrictUtf8.encode(unpairedSurrogate));
    } finally {
      Arrays.fill(malformed, (byte) 0);
      Arrays.fill(unpairedSurrogate, '\0');
    }
  }

  @Test
  void distinguishesRequiredAndOptionalEmptyFields() {
    byte[] empty = new byte[0];
    char[] decoded = StrictUtf8.decodeChars(empty, false);
    try {
      assertEquals(0, decoded.length);
      assertThrows(IllegalArgumentException.class, () -> StrictUtf8.decodeChars(empty, true));
      assertThrows(IllegalArgumentException.class, () -> StrictUtf8.decodeString(empty, true));
      assertEquals("", StrictUtf8.decodeString(empty, false));
    } finally {
      Arrays.fill(decoded, '\0');
      Arrays.fill(empty, (byte) 0);
    }
  }
}
