package cl.germain.vault.cli;

import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Objects;

/** Presents an approximate password age using the entry's general updated-at timestamp. */
final class PasswordAge {
  private PasswordAge() {}

  static String describe(Instant updatedAt, Clock clock) {
    Objects.requireNonNull(updatedAt, "updatedAt");
    Objects.requireNonNull(clock, "clock");
    Instant now = clock.instant();
    var updatedDate = updatedAt.atZone(ZoneOffset.UTC).toLocalDate();
    var nowDate = now.atZone(ZoneOffset.UTC).toLocalDate();
    String age;
    if (updatedAt.isAfter(now)) {
      age = "fecha futura";
    } else {
      Period period = Period.between(updatedDate, nowDate);
      if (period.getYears() > 0) {
        age = plural(period.getYears(), "año", "años");
      } else if (period.getMonths() > 0) {
        age = plural(period.getMonths(), "mes", "meses");
      } else if (period.getDays() > 0) {
        age = plural(period.getDays(), "día", "días");
      } else {
        age = "hoy";
      }
      if (!"hoy".equals(age)) age = "hace " + age;
    }

    boolean sixMonthsOrOlder = !updatedDate.isAfter(nowDate.minusMonths(6));
    String description = "Última actualización: " + age + " (aproximado)";
    return sixMonthsOrOlder
        ? description + " — conviene actualizar la contraseña (6 meses o más)."
        : description;
  }

  private static String plural(int count, String singular, String plural) {
    return count + " " + (count == 1 ? singular : plural);
  }
}
