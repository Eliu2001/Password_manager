package cl.germain.vault.crypto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Argon2ParametersTest {

  @Test
  void defaultsUseApprovedNominalParameters() {
    assertEquals(new Argon2Parameters(64 * 1024, 3, 1), Argon2Parameters.defaults());
  }

  @Test
  void acceptsMinimumAndMaximumForEveryParameter() {
    assertDoesNotThrow(() -> new Argon2Parameters(Argon2Parameters.MIN_MEMORY_KIB, 2, 1));
    assertDoesNotThrow(() -> new Argon2Parameters(Argon2Parameters.MAX_MEMORY_KIB, 2, 1));
    assertDoesNotThrow(() -> new Argon2Parameters(65_536, Argon2Parameters.MIN_ITERATIONS, 1));
    assertDoesNotThrow(() -> new Argon2Parameters(65_536, Argon2Parameters.MAX_ITERATIONS, 1));
    assertDoesNotThrow(() -> new Argon2Parameters(65_536, 3, Argon2Parameters.MIN_PARALLELISM));
    assertDoesNotThrow(() -> new Argon2Parameters(65_536, 3, Argon2Parameters.MAX_PARALLELISM));
  }

  @Test
  void rejectsValuesImmediatelyOutsideEveryParameterRange() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Argon2Parameters(Argon2Parameters.MIN_MEMORY_KIB - 1, 2, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Argon2Parameters(Argon2Parameters.MAX_MEMORY_KIB + 1, 2, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Argon2Parameters(Argon2Parameters.MIN_MEMORY_KIB, 1, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Argon2Parameters(Argon2Parameters.MIN_MEMORY_KIB, 11, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Argon2Parameters(Argon2Parameters.MIN_MEMORY_KIB, 2, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Argon2Parameters(Argon2Parameters.MIN_MEMORY_KIB, 2, 5));
  }
}
