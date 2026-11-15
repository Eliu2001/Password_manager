package cl.germain.vault.generator;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/** Generates passwords using ASCII characters and a cryptographically secure random source. */
public final class PasswordGenerator {
  public static final int MIN_LENGTH = 12;
  public static final int MAX_LENGTH = 128;

  private static final char[] UPPERCASE = range('A', 'Z');
  private static final char[] LOWERCASE = range('a', 'z');
  private static final char[] DIGITS = range('0', '9');
  private static final char[] SYMBOLS = symbols();
  private static final char[][] REQUIRED_GROUPS = {UPPERCASE, LOWERCASE, DIGITS, SYMBOLS};
  private static final char[] ALL_CHARACTERS = combine(REQUIRED_GROUPS);

  private final SecureRandom random;

  /** Creates a generator with its own {@link SecureRandom} instance. */
  public PasswordGenerator() {
    this(new SecureRandom());
  }

  PasswordGenerator(SecureRandom random) {
    this.random = Objects.requireNonNull(random, "random");
  }

  /**
   * Generates a password with at least one character from each required group. The caller owns the
   * returned array and must clear it after use.
   *
   * @param length requested password length, from 12 through 128 inclusive
   * @return generated password characters
   */
  public char[] generate(int length) {
    if (length < MIN_LENGTH || length > MAX_LENGTH) {
      throw new IllegalArgumentException("Password length is outside the supported range");
    }

    char[] password = new char[length];
    boolean generated = false;
    try {
      int position = 0;
      for (char[] group : REQUIRED_GROUPS) {
        password[position++] = group[random.nextInt(group.length)];
      }
      while (position < password.length) {
        password[position++] = ALL_CHARACTERS[random.nextInt(ALL_CHARACTERS.length)];
      }

      for (int index = password.length - 1; index > 0; index--) {
        int swapIndex = random.nextInt(index + 1);
        char current = password[index];
        password[index] = password[swapIndex];
        password[swapIndex] = current;
      }

      generated = true;
      return password;
    } finally {
      if (!generated) {
        Arrays.fill(password, '\0');
      }
    }
  }

  private static char[] range(char first, char last) {
    char[] characters = new char[last - first + 1];
    for (int index = 0; index < characters.length; index++) {
      characters[index] = (char) (first + index);
    }
    return characters;
  }

  private static char[] symbols() {
    char[] characters = new char[32];
    int position = 0;
    for (char character = '!'; character <= '~'; character++) {
      if (!isAsciiLetterOrDigit(character)) {
        characters[position++] = character;
      }
    }
    return characters;
  }

  private static boolean isAsciiLetterOrDigit(char character) {
    return (character >= 'A' && character <= 'Z')
        || (character >= 'a' && character <= 'z')
        || (character >= '0' && character <= '9');
  }

  private static char[] combine(char[][] groups) {
    int length = 0;
    for (char[] group : groups) {
      length += group.length;
    }

    char[] characters = new char[length];
    int position = 0;
    for (char[] group : groups) {
      System.arraycopy(group, 0, characters, position, group.length);
      position += group.length;
    }
    return characters;
  }
}
