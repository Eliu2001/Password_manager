package cl.germain.vault.totp;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TotpGeneratorTest {
  private static final byte[] SECRET =
      "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

  @Test
  void matchesRfc6238Sha1VectorsAndSixDigitOutput() {
    long[] seconds = {59, 1_111_111_109L, 1_111_111_111L, 1_234_567_890L, 2_000_000_000L};
    String[] expected = {"287082", "081804", "050471", "005924", "279037"};
    for (int i = 0; i < seconds.length; i++) {
      char[] code = TotpGenerator.generate(SECRET, seconds[i]);
      assertEquals(expected[i], new String(code));
      assertEquals(6, code.length);
      Arrays.fill(code, '\0');
    }
  }

  @Test
  void injectedClockAndRemainingTimeUseOneInstant() {
    TotpGenerator generator =
        new TotpGenerator(Clock.fixed(Instant.ofEpochSecond(60), ZoneOffset.UTC));
    TotpGenerator.Result result = generator.generateCurrent(SECRET);
    assertEquals("359152", new String(result.code()));
    assertEquals(30, result.secondsRemaining());
    Arrays.fill(result.code(), '\0');
  }

  @Test
  void rejectsPreEpochWithoutReturningPartialCode() {
    assertThrows(IllegalArgumentException.class, () -> TotpGenerator.generate(SECRET, -1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TotpGenerator(Clock.fixed(Instant.ofEpochSecond(-1), ZoneOffset.UTC))
                .generate(SECRET));
  }

  @Test
  void secondsRemainingUsesFloorModuloAtPeriodBoundaries() {
    for (long second : new long[] {0, 29, 30, 59, 60}) {
      TotpGenerator generator =
          new TotpGenerator(Clock.fixed(Instant.ofEpochSecond(second), ZoneOffset.UTC));
      int expected = second % 30 == 29 ? 1 : 30 - (int) (second % 30);
      assertEquals(expected, generator.secondsRemaining());
    }
  }
}
