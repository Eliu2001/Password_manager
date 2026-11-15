package cl.germain.vault.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PasswordGeneratorTest {
    @Test
    void generatesRequestedMinimumAndMaximumLengthsWithEveryRequiredCategory() {
        for (int length : new int[] {PasswordGenerator.MIN_LENGTH, 20, PasswordGenerator.MAX_LENGTH}) {
            char[] password = new PasswordGenerator(new LowestIndexSecureRandom()).generate(length);
            try {
                assertEquals(length, password.length);
                assertContainsRequiredGroups(password);
                assertOnlyAllowedCharacters(password);
            } finally {
                Arrays.fill(password, '\0');
            }
        }
    }

    @Test
    void rejectsLengthsBelowAndAboveSupportedRange() {
        PasswordGenerator generator = new PasswordGenerator(new LowestIndexSecureRandom());

        assertThrows(IllegalArgumentException.class, () -> generator.generate(PasswordGenerator.MIN_LENGTH - 1));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(PasswordGenerator.MAX_LENGTH + 1));
    }

    @Test
    void usesBoundedSecureRandomSelectionForRequiredCharactersFillAndShuffle() {
        LowestIndexSecureRandom random = new LowestIndexSecureRandom();
        PasswordGenerator generator = new PasswordGenerator(random);

        char[] password = generator.generate(PasswordGenerator.MIN_LENGTH);
        try {
            assertEquals(PasswordGenerator.MIN_LENGTH * 2 - 1, random.calls);
            assertTrue(random.invalidBounds == 0);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static void assertContainsRequiredGroups(char[] password) {
        assertTrue(contains(password, 'A', 'Z'));
        assertTrue(contains(password, 'a', 'z'));
        assertTrue(contains(password, '0', '9'));
        boolean hasSymbol = false;
        for (char character : password) {
            hasSymbol |= isSymbol(character);
        }
        assertTrue(hasSymbol);
    }

    private static void assertOnlyAllowedCharacters(char[] password) {
        for (char character : password) {
            assertTrue(isAsciiLetterOrDigit(character) || isSymbol(character));
        }
    }

    private static boolean contains(char[] password, char first, char last) {
        for (char character : password) {
            if (character >= first && character <= last) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSymbol(int character) {
        return character >= '!' && character <= '~' && !isAsciiLetterOrDigit((char) character);
    }

    private static boolean isAsciiLetterOrDigit(char character) {
        return (character >= 'A' && character <= 'Z')
                || (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9');
    }

    private static final class LowestIndexSecureRandom extends SecureRandom {
        private int calls;
        private int invalidBounds;

        @Override
        public int nextInt(int bound) {
            calls++;
            if (bound <= 0) {
                invalidBounds++;
                throw new IllegalArgumentException("bound must be positive");
            }
            return 0;
        }
    }
}
