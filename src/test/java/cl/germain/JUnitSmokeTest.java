package cl.germain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JUnitSmokeTest {

  @Test
  void runsOnJava21() {
    assertEquals(21, Runtime.version().feature());
  }
}
