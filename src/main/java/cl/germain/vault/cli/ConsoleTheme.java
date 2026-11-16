package cl.germain.vault.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Small, dependency-free renderer for the guided console. */
final class ConsoleTheme {
  private static final String RESET = "\u001b[0m";
  private static final int MIN_WIDTH = 44;
  private static final int MAX_WIDTH = 100;
  private static final Set<String> SUCCESS_MESSAGES =
      Set.of(
          "Entrada guardada correctamente.",
          "Contraseña actualizada correctamente.",
          "Secreto TOTP guardado.",
          "Secreto TOTP retirado.",
          "Entrada borrada correctamente.",
          "Bóveda actualizada a formato v2.",
          "Contraseña generada. No se guarda automáticamente.");
  private static final Set<String> ERROR_MESSAGES =
      Set.of(
          "Opción inválida.",
          "Número de entrada inválido.",
          "No se encontró la entrada solicitada.",
          "No se encontraron entradas.",
          "No se pudo abrir la bóveda.",
          "No se pudo completar la operación.",
          "Las contraseñas no coinciden; no se realizaron cambios.",
          "Las contraseñas no coinciden.",
          "Los secretos no coinciden.",
          "Ejecute upgrade antes de gestionar TOTP.",
          "La entrada no tiene secreto TOTP.");
  private final boolean ansi;
  private final int width;

  ConsoleTheme(boolean ansi) {
    this(ansi, terminalWidth(System.getenv()));
  }

  ConsoleTheme(boolean ansi, int width) {
    this.ansi = ansi;
    this.width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
  }

  static ConsoleTheme system() {
    Map<String, String> environment = System.getenv();
    return new ConsoleTheme(supportsAnsi(environment), terminalWidth(environment));
  }

  static boolean supportsAnsi(Map<String, String> environment) {
    if (environment.containsKey("NO_COLOR")) return false;
    String termProgram = environment.get("TERM_PROGRAM");
    if (termProgram != null) {
      String normalizedProgram = termProgram.toLowerCase(Locale.ROOT);
      if (normalizedProgram.contains("warp")) return true;
    }
    String colorDepth = environment.get("COLORTERM");
    if (colorDepth != null
        && (colorDepth.equalsIgnoreCase("truecolor") || colorDepth.equalsIgnoreCase("24bit"))) {
      return true;
    }
    String term = environment.get("TERM");
    boolean ansi = environment.containsKey("WT_SESSION");
    if (term != null) {
      String normalized = term.toLowerCase(Locale.ROOT);
      ansi |=
          normalized.contains("color")
              || normalized.contains("xterm")
              || normalized.contains("screen");
    }
    return ansi;
  }

  static int terminalWidth(Map<String, String> environment) {
    String columns = environment.get("COLUMNS");
    if (columns == null) return 80;
    try {
      return Math.max(MIN_WIDTH, Integer.parseInt(columns));
    } catch (NumberFormatException exception) {
      return 80;
    }
  }

  String header(String vaultName) {
    String title = "PASSWORD VAULT";
    String subtitle = "Gestor local de contrasenas";
    String credit = "Creado por Eliu2001";
    String top = border('+', '-', '+');
    String vault = "|" + fit(" Boveda: " + safeCell(vaultName), width - 2) + "|";
    String state = "|" + fit(" Estado: Desbloqueada", width - 2) + "|";
    return color("\u001b[38;5;110m", top)
        + System.lineSeparator()
        + color("\u001b[1;38;5;110m", "|" + center(title, width - 2) + "|")
        + System.lineSeparator()
        + color("\u001b[38;5;250m", "|" + center(subtitle, width - 2) + "|")
        + System.lineSeparator()
        + color("\u001b[38;5;250m", "|" + center(credit, width - 2) + "|")
        + System.lineSeparator()
        + color("\u001b[38;5;110m", top)
        + System.lineSeparator()
        + color("\u001b[38;5;250m", vault)
        + System.lineSeparator()
        + color("\u001b[38;5;250m", state)
        + System.lineSeparator()
        + color("\u001b[38;5;110m", top);
  }

  String wizard() {
    String frame = border('+', '-', '+');
    return color("\u001b[38;5;110m", frame)
        + System.lineSeparator()
        + color("\u001b[1;38;5;110m", "|" + center("CONFIGURACION INICIAL", width - 2) + "|")
        + System.lineSeparator()
        + color("\u001b[38;5;110m", frame);
  }

  String colorizeWizardOptions(String text) {
    if (!ansi) return text;
    return text.replace("[1] Crear", "\u001b[38;5;110m[1] Crear\u001b[0m")
        .replace("[2] Abrir", "\u001b[38;5;110m[2] Abrir\u001b[0m")
        .replace("[0] Salir", "\u001b[38;5;250m[0] Salir\u001b[0m");
  }

  String successMessage(String message) {
    return SUCCESS_MESSAGES.contains(message) ? color("\u001b[38;5;114m", message) : message;
  }

  String errorMessage(String message) {
    return ERROR_MESSAGES.contains(message) ? color("\u001b[38;5;167m", message) : message;
  }

  String menu() {
    List<String> entries =
        List.of(
            "[1] Agregar entrada",
            "[2] Listar entradas",
            "[3] Buscar servicio",
            "[4] Ver entrada",
            "[5] Actualizar contrasena",
            "[6] Borrar entrada",
            "[7] Configurar TOTP",
            "[8] Retirar TOTP",
            "[9] Consultar TOTP",
            "[10] Actualizar v1 a v2",
            "[11] Generar contrasena",
            "[12] Ayuda",
            "[0] Salir");
    List<String> rows = new ArrayList<>();
    if (width >= 88) {
      rows.add("ENTRADAS" + " ".repeat(Math.max(1, width - 31)) + "AUTENTICACION TOTP");
      rows.add("[1] Agregar entrada" + " ".repeat(Math.max(1, width - 39)) + "[7] Configurar");
      rows.add("[2] Listar entradas" + " ".repeat(Math.max(1, width - 39)) + "[8] Retirar");
      rows.add("[3] Buscar servicio" + " ".repeat(Math.max(1, width - 39)) + "[9] Consultar");
      rows.add("[4] Ver entrada");
      rows.add("[5] Actualizar contrasena");
      rows.add("[6] Borrar entrada");
      rows.add("");
      rows.add("HERRAMIENTAS");
      rows.add("[10] Actualizar v1 a v2");
      rows.add("[11] Generar contrasena");
      rows.add("[12] Ayuda");
    } else {
      rows.add("ENTRADAS");
      rows.addAll(entries.subList(0, 6));
      rows.add("");
      rows.add("AUTENTICACION TOTP");
      rows.addAll(entries.subList(6, 9));
      rows.add("");
      rows.add("HERRAMIENTAS");
      rows.addAll(entries.subList(9, 12));
    }
    rows.add("");
    rows.add("[0] Salir");

    String frame = border('+', '-', '+');
    StringBuilder rendered = new StringBuilder(color("\u001b[38;5;110m", frame));
    for (String row : rows) {
      rendered.append(System.lineSeparator());
      if (row.isEmpty()) {
        rendered.append('|').append(" ".repeat(width - 2)).append('|');
      } else if (row.startsWith("ENTRADAS") && row.contains("AUTENTICACION TOTP")) {
        int split = row.indexOf("AUTENTICACION TOTP");
        String left = row.substring(0, split);
        String right = row.substring(split);
        rendered
            .append("| ")
            .append(color("\u001b[1;38;5;110m", left.trim()))
            .append(" ".repeat(Math.max(1, width - 4 - left.trim().length() - right.length())))
            .append(color("\u001b[1;38;5;141m", right))
            .append(" |");
      } else {
        String padded = "| " + fit(row, width - 4) + " |";
        String shade =
            row.startsWith("ENTRADAS")
                ? "\u001b[1;38;5;110m"
                : row.startsWith("AUTENTICACION TOTP")
                    ? "\u001b[1;38;5;141m"
                    : row.startsWith("HERRAMIENTAS")
                        ? "\u001b[1;38;5;222m"
                        : row.startsWith("[0]") ? "\u001b[38;5;250m" : "\u001b[38;5;250m";
        rendered.append(color(shade, padded));
      }
    }
    rendered.append(System.lineSeparator()).append(color("\u001b[38;5;110m", frame));
    return rendered.toString();
  }

  String entryTable(List<String[]> rows) {
    StringBuilder output = new StringBuilder();
    String line = "-".repeat(width);
    output.append(color("\u001b[1;38;5;110m", "ENTRADAS GUARDADAS")).append(System.lineSeparator());
    output.append(color("\u001b[38;5;110m", line)).append(System.lineSeparator());
    output.append("N.  Servicio | Usuario | Ultima actualizacion aproximada");
    output.append(System.lineSeparator()).append(line);
    for (String[] row : rows) {
      output
          .append(System.lineSeparator())
          .append(row[0])
          .append(".  ")
          .append(safeCell(row[1]))
          .append(" | ")
          .append(safeCell(row[2]))
          .append(" | ")
          .append(safeCell(row[3]));
    }
    output.append(System.lineSeparator()).append(System.lineSeparator());
    output.append(
        color(
            "\u001b[38;5;222m",
            "[!] La antiguedad es aproximada; se recomienda revisar desde 6 meses."));
    return output.toString();
  }

  private String border(char left, char fill, char right) {
    return left + String.valueOf(fill).repeat(width - 2) + right;
  }

  private String color(String code, String text) {
    return ansi ? code + text + RESET : text;
  }

  private static String center(String text, int length) {
    int left = Math.max(0, (length - text.length()) / 2);
    return " ".repeat(left) + fit(text, length - left);
  }

  private static String fit(String text, int length) {
    if (text.length() >= length) return text.substring(0, length);
    return text + " ".repeat(length - text.length());
  }

  private static String safeCell(String value) {
    return value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').replace("|", "\\|");
  }
}
