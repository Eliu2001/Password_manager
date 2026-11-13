package cl.germain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JUnitSmokeTest {

    @Test
    void runsOnJava21() {
        assertEquals(21, Runtime.version().feature());
    }
}
