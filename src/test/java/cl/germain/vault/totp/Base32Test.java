package cl.germain.vault.totp;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class Base32Test {
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test void acceptsCaseAndOptionalCorrectPadding() {
        byte[] expected = "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertArrayEquals(expected, Base32.decode(RFC_SECRET.toCharArray()));
        assertArrayEquals(expected, Base32.decode(RFC_SECRET.toLowerCase().toCharArray()));
        Arrays.fill(expected, (byte) 0);
    }

    @Test void acceptsCorrectOptionalPaddingForNonFiveByteMultiple() {
        byte[] expected = "123456789012345678901".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        char[] padded = encode(expected, true);
        char[] unpadded = encode(expected, false);
        byte[] decodedPadded = Base32.decode(padded);
        byte[] decodedUnpadded = Base32.decode(unpadded);
        assertArrayEquals(expected, decodedPadded);
        assertArrayEquals(expected, decodedUnpadded);
        Arrays.fill(expected, (byte) 0);
        Arrays.fill(padded, '\0'); Arrays.fill(unpadded, '\0');
        Arrays.fill(decodedPadded, (byte) 0); Arrays.fill(decodedUnpadded, (byte) 0);
    }

    @Test void enforcesCanonicalTailAndDecodedBounds() {
        byte[] secret = new byte[21]; Arrays.fill(secret, (byte) 0x31);
        char[] encoded = encode(secret, false);
        int finalValue = indexOf(encoded[encoded.length - 1]);
        encoded[encoded.length - 1] = ALPHABET[(finalValue & 24) | ((finalValue + 1) & 3)];
        assertThrows(IllegalArgumentException.class, () -> Base32.decode(encoded));
        Arrays.fill(secret, (byte) 0); Arrays.fill(encoded, '\0');

        for (int size : new int[] {19, 20, 64, 65}) {
            byte[] value = new byte[size];
            char[] text = encode(value, false);
            if (size == 19 || size == 65) assertThrows(IllegalArgumentException.class, () -> Base32.decode(text));
            else assertEquals(size, Base32.decode(text).length);
            Arrays.fill(value, (byte) 0); Arrays.fill(text, '\0');
        }
    }

    @Test void rejectsMalformedPaddingCharactersNonCanonicalBitsAndLengths() {
        for (String value : new String[] {"", "A".repeat(31), RFC_SECRET + "=", "=" + RFC_SECRET,
                "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJ!"}) {
            assertThrows(IllegalArgumentException.class, () -> Base32.decode(value.toCharArray()));
        }
        assertThrows(IllegalArgumentException.class, () -> Base32.decode("A".repeat(104).toCharArray()));
    }

    private static char[] encode(byte[] bytes, boolean padding) {
        int chars = (bytes.length * 8 + 4) / 5;
        int outputLength = padding ? ((chars + 7) / 8) * 8 : chars;
        char[] out = new char[outputLength];
        int accumulator = 0, bits = 0, at = 0;
        for (byte value : bytes) {
            accumulator = (accumulator << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) { bits -= 5; out[at++] = ALPHABET[(accumulator >> bits) & 31]; }
        }
        if (bits > 0) out[at++] = ALPHABET[(accumulator << (5 - bits)) & 31];
        while (at < out.length) out[at++] = '=';
        return out;
    }

    private static int indexOf(char character) {
        for (int i = 0; i < ALPHABET.length; i++) if (ALPHABET[i] == character) return i;
        throw new AssertionError();
    }
}
