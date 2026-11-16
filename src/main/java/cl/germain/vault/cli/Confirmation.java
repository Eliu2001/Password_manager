package cl.germain.vault.cli;

/** Shared exact affirmative responses for visible confirmation prompts. */
final class Confirmation {
  private Confirmation() {}

  static boolean isAffirmative(String response) {
    return "sí".equals(response) || "si".equals(response);
  }
}
