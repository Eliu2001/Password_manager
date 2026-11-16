package cl.germain.vault.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfirmationTest {
  @Test
  void acceptsOnlyTheTwoExactLowercaseAffirmativeResponses() {
    assertTrue(Confirmation.isAffirmative("sí"));
    assertTrue(Confirmation.isAffirmative("si"));

    assertFalse(Confirmation.isAffirmative(null));
    assertFalse(Confirmation.isAffirmative(""));
    assertFalse(Confirmation.isAffirmative("SI"));
    assertFalse(Confirmation.isAffirmative("Sí"));
    assertFalse(Confirmation.isAffirmative(" si"));
    assertFalse(Confirmation.isAffirmative("si "));
    assertFalse(Confirmation.isAffirmative("no"));
  }
}
