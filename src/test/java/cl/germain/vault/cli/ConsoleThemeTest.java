package cl.germain.vault.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConsoleThemeTest {
  @Test
  void rendersProjectTitleCreditAndVaultContextWithoutAnsiWhenDisabled() {
    ConsoleTheme theme = new ConsoleTheme(false, 80);
    String banner = theme.header("sample.pvlt");
    assertTrue(banner.contains("PASSWORD VAULT"));
    assertTrue(banner.contains("Creado por Eliu2001"));
    assertTrue(banner.contains("sample.pvlt"));
    assertTrue(banner.contains("Desbloqueada"));
    assertFalse(banner.contains("\u001b["));
    String menu = theme.menu();
    assertTrue(menu.contains("ENTRADAS"));
    assertTrue(menu.contains("AUTENTICACION TOTP"));
    assertTrue(menu.contains("HERRAMIENTAS"));
    for (int option = 1; option <= 12; option++) {
      assertTrue(menu.contains("[" + option + "]"));
    }
    assertTrue(menu.contains("[0] Salir"));
    assertFalse(menu.contains("\u001b["));
    String wizard = theme.wizard() + theme.colorizeWizardOptions("[1] Crear\n[2] Abrir\n[0] Salir");
    assertFalse(wizard.contains("\u001b["));
    assertFalse(wizard.contains("\u2500"));
  }

  @Test
  void usesBlueAndCyanAnsiAccentsWhenEnabled() {
    ConsoleTheme theme = new ConsoleTheme(true, 100);
    String banner = theme.header("sample.pvlt");
    assertTrue(banner.contains("\u001b[38;5;110m"));
    assertTrue(banner.contains("\u001b[1;38;5;110m"));
    assertTrue(banner.contains("PASSWORD VAULT"));
    String menu = theme.menu();
    assertTrue(menu.contains("\u001b[1;38;5;141m"));
    assertTrue(menu.contains("\u001b[1;38;5;222m"));
    assertTrue(menu.contains("[10] Actualizar v1 a v2"));
    assertTrue(theme.wizard().contains("\u001b[38;5;110m"));
  }

  @Test
  void detectsWarpAndHonorsNoColorOverride() {
    assertTrue(ConsoleTheme.supportsAnsi(Map.of("TERM_PROGRAM", "WarpTerminal")));
    assertTrue(ConsoleTheme.supportsAnsi(Map.of("TERM", "xterm-256color")));
    assertFalse(ConsoleTheme.supportsAnsi(Map.of("TERM_PROGRAM", "WarpTerminal", "NO_COLOR", "1")));
  }

  @Test
  void usesVerticalMenuWhenTerminalIsNarrowAndBoundsReportedWidth() {
    ConsoleTheme narrow = new ConsoleTheme(false, 60);
    String menu = narrow.menu();
    assertTrue(menu.indexOf("ENTRADAS") < menu.indexOf("AUTENTICACION TOTP"));
    assertTrue(menu.indexOf("AUTENTICACION TOTP") < menu.indexOf("HERRAMIENTAS"));
    assertTrue(ConsoleTheme.terminalWidth(Map.of("COLUMNS", "42")) == 44);
    assertTrue(ConsoleTheme.terminalWidth(Map.of("COLUMNS", "garbage")) == 80);
  }

  @Test
  void colorsOnlyKnownStatusMessagesAndLeavesDynamicContentUntouched() {
    ConsoleTheme colored = new ConsoleTheme(true, 80);
    assertTrue(colored.successMessage("Entrada guardada correctamente.").contains("38;5;114"));
    assertTrue(colored.errorMessage("Opción inválida.").contains("38;5;167"));
    assertFalse(colored.successMessage("Servicio: cuenta importante").contains("\u001b["));
    assertFalse(colored.errorMessage("Error: contenido externo").contains("\u001b["));
    assertFalse(
        new ConsoleTheme(false, 80)
            .successMessage("Entrada guardada correctamente.")
            .contains("\u001b["));
  }
}
