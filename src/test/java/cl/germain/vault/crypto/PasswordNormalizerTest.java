package cl.germain.vault.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class PasswordNormalizerTest {

    @Test
    void returnsOriginalArrayForAlreadyNormalizedInput() {
        char[] password = {'v', 'a', 'u', 'l', 't'};

        char[] result = PasswordNormalizer.normalizeIfNeeded(password);

        assertSame(password, result);
    }

    @Test
    void normalizesNonNfcInputIntoSeparateCharacterArray() {
        char[] password = {'e', '\u0301'};

        char[] result = PasswordNormalizer.normalizeIfNeeded(password);

        assertNotSame(password, result);
        assertArrayEquals(new char[]{'\u00e9'}, result);
        assertArrayEquals(new char[]{'e', '\u0301'}, password);
        java.util.Arrays.fill(result, '\0');
    }
}
