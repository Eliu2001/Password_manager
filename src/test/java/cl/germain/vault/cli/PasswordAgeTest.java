package cl.germain.vault.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PasswordAgeTest {
  private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void describesRecentAndOldEntriesAsApproximate() {
    String recent =
        PasswordAge.describe(NOW.atZone(ZoneOffset.UTC).minusMonths(2).toInstant(), CLOCK);
    assertTrue(recent.contains("Última actualización: hace 2 meses (aproximado)"));
    assertFalse(recent.contains("conviene actualizar"));

    String old = PasswordAge.describe(NOW.atZone(ZoneOffset.UTC).minusYears(1).toInstant(), CLOCK);
    assertTrue(old.contains("Última actualización: hace 1 año (aproximado)"));
    assertTrue(old.contains("conviene actualizar"));
  }

  @Test
  void appliesSixCalendarMonthThresholdAtTheBoundary() {
    Instant boundary = NOW.atZone(ZoneOffset.UTC).minusMonths(6).toInstant();
    assertFalse(
        PasswordAge.describe(boundary.plus(java.time.Duration.ofDays(1)), CLOCK)
            .contains("conviene actualizar"));
    assertTrue(PasswordAge.describe(boundary, CLOCK).contains("conviene actualizar"));
    assertTrue(
        PasswordAge.describe(boundary.minus(java.time.Duration.ofDays(1)), CLOCK)
            .contains("conviene actualizar"));
  }
}
