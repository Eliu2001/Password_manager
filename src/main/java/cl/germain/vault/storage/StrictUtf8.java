package cl.germain.vault.storage;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Strict UTF-8 conversion helpers that do not create Strings for passwords. */
final class StrictUtf8 {
  private StrictUtf8() {}

  static byte[] encode(String value) {
    Objects.requireNonNull(value, "value");
    return encode(CharBuffer.wrap(value));
  }

  static byte[] encode(char[] value) {
    Objects.requireNonNull(value, "value");
    return encode(CharBuffer.wrap(value));
  }

  static String decodeString(byte[] bytes, boolean required) {
    char[] characters = decodeChars(bytes, required);
    try {
      return new String(characters);
    } finally {
      Arrays.fill(characters, '\0');
    }
  }

  /** Returns a caller-owned char array; the caller must clear it. */
  static char[] decodeChars(byte[] bytes, boolean required) {
    Objects.requireNonNull(bytes, "bytes");
    CharBuffer decoded = null;
    try {
      decoded = strictDecoder().decode(ByteBuffer.wrap(bytes));
      if (required && !decoded.hasRemaining()) {
        throw new IllegalArgumentException("Required text field is empty");
      }
      char[] characters = new char[decoded.remaining()];
      decoded.get(characters);
      return characters;
    } catch (CharacterCodingException exception) {
      throw new IllegalArgumentException("Field is not valid UTF-8");
    } finally {
      clear(decoded);
    }
  }

  private static byte[] encode(CharBuffer characters) {
    ByteBuffer encoded = null;
    try {
      encoded = strictEncoder().encode(characters);
      byte[] bytes = new byte[encoded.remaining()];
      encoded.get(bytes);
      return bytes;
    } catch (CharacterCodingException exception) {
      throw new IllegalArgumentException("Text is not valid Unicode");
    } finally {
      clear(encoded);
    }
  }

  private static CharsetEncoder strictEncoder() {
    return StandardCharsets.UTF_8
        .newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
  }

  private static CharsetDecoder strictDecoder() {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
  }

  private static void clear(ByteBuffer buffer) {
    if (buffer != null && buffer.hasArray()) {
      Arrays.fill(buffer.array(), (byte) 0);
    }
  }

  private static void clear(CharBuffer buffer) {
    if (buffer != null && buffer.hasArray()) {
      Arrays.fill(buffer.array(), '\0');
    }
  }
}
