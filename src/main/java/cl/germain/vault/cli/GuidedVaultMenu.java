package cl.germain.vault.cli;

import cl.germain.vault.crypto.DecryptionException;
import cl.germain.vault.model.Entry;
import cl.germain.vault.storage.VaultSession;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Numbered menu and ephemeral entry selectors for an authenticated vault. */
final class GuidedVaultMenu {
  private final CliConsole console;
  private final VaultCliCommands commands;
  private final Path vaultPath;
  private final ConsoleTheme theme;
  private final Clock clock;
  private boolean firstDisplay = true;

  GuidedVaultMenu(CliConsole console, VaultCliCommands commands, Path vaultPath) {
    this(console, commands, vaultPath, ConsoleTheme.system(), Clock.systemUTC());
  }

  GuidedVaultMenu(
      CliConsole console, VaultCliCommands commands, Path vaultPath, ConsoleTheme theme) {
    this(console, commands, vaultPath, theme, Clock.systemUTC());
  }

  GuidedVaultMenu(
      CliConsole console,
      VaultCliCommands commands,
      Path vaultPath,
      ConsoleTheme theme,
      Clock clock) {
    this.console = console;
    this.commands = commands;
    this.vaultPath = vaultPath;
    this.theme = theme;
    this.clock = clock;
  }

  /** Returns true when the user chooses to exit. */
  void show() {
    String vaultName =
        vaultPath.getFileName() == null ? vaultPath.toString() : vaultPath.getFileName().toString();
    String header = firstDisplay ? theme.header(vaultName) + System.lineSeparator() : "";
    firstDisplay = false;
    console.printOutput(header + theme.menu());
  }

  boolean step(VaultSession session, String choice)
      throws IOException, InterruptedException, DecryptionException {
    if (choice == null || "0".equals(choice)) return true;
    switch (choice) {
      case "1" -> run(new CliCommand(CliCommand.Type.ADD, vaultPath, null), session);
      case "2" -> list(session.vault().list());
      case "3" -> search(session);
      case "4", "5", "6", "7", "8", "9" -> {
        Entry entry = select(session, session.vault()::list);
        if (entry != null) {
          if ("5".equals(choice)) {
            commands.updatePasswordInSession(entry.id(), session);
          } else {
            CliCommand.Type type =
                switch (choice) {
                  case "4" -> CliCommand.Type.SHOW;
                  case "6" -> CliCommand.Type.DELETE;
                  case "7" -> CliCommand.Type.SET_TOTP;
                  case "8" -> CliCommand.Type.REMOVE_TOTP;
                  default -> CliCommand.Type.TOTP;
                };
            run(new CliCommand(type, vaultPath, entry.id()), session);
          }
        }
      }
      case "10" -> run(new CliCommand(CliCommand.Type.UPGRADE, vaultPath, null), session);
      case "11" -> commands.execute(new CliCommand(CliCommand.Type.GENERATE, null, null));
      case "12" ->
          console.printOutput(
              "Seleccione una opción. Las posiciones solo valen para la lista mostrada.");
      default -> console.printError("Opción inválida.");
    }
    return false;
  }

  private void search(VaultSession session)
      throws IOException, InterruptedException, DecryptionException {
    String query = console.readLine("Buscar servicio (0 para volver): ");
    if (query == null || "0".equals(query)) return;
    String normalized = query.toLowerCase(Locale.ROOT);
    List<Entry> matches = matchingEntries(session, normalized);
    if (matches.isEmpty()) {
      console.printOutput("No se encontraron entradas.");
      return;
    }
    Entry selected = select(session, () -> matchingEntries(session, normalized));
    if (selected == null) return;
    while (true) {
      String action =
          console.readLine(
              "1. Ver  2. Actualizar contraseña  3. Borrar  4. TOTP  5. Volver al menú: ");
      if (action == null || "5".equals(action)) return;
      switch (action) {
        case "1" -> {
          run(new CliCommand(CliCommand.Type.SHOW, vaultPath, selected.id()), session);
          return;
        }
        case "2" -> {
          commands.updatePasswordInSession(selected.id(), session);
          return;
        }
        case "3" -> {
          run(new CliCommand(CliCommand.Type.DELETE, vaultPath, selected.id()), session);
          return;
        }
        case "4" -> {
          run(new CliCommand(CliCommand.Type.TOTP, vaultPath, selected.id()), session);
          return;
        }
        default -> console.printError("Opción inválida.");
      }
    }
  }

  private Entry select(VaultSession session, Supplier<List<Entry>> entriesSupplier)
      throws IOException, InterruptedException {
    while (true) {
      List<Entry> entries = entriesSupplier.get();
      list(entries);
      if (entries.isEmpty()) return null;
      String value = console.readLine("Número de entrada (0 para volver): ");
      if (value == null || "0".equals(value)) return null;
      try {
        int index = Integer.parseInt(value);
        if (index < 1 || index > entries.size()) throw new NumberFormatException();
        Entry selected = entries.get(index - 1);
        Entry current = session.vault().get(selected.id()).orElse(null);
        if (current == null) {
          console.printError("La entrada ya no está disponible. Seleccione nuevamente.");
          continue;
        }
        return current;
      } catch (NumberFormatException exception) {
        console.printError("Número de entrada inválido.");
      }
    }
  }

  private static List<Entry> matchingEntries(VaultSession session, String normalizedQuery) {
    return session.vault().list().stream()
        .filter(entry -> entry.service().toLowerCase(Locale.ROOT).contains(normalizedQuery))
        .toList();
  }

  private void list(List<Entry> entries) throws IOException {
    if (entries.isEmpty()) {
      console.printPrivate("La bóveda está vacía.");
      return;
    }
    List<String[]> rows = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      Entry entry = entries.get(i);
      rows.add(
          new String[] {
            Integer.toString(i + 1),
            entry.service(),
            entry.username(),
            PasswordAge.describe(entry.updatedAt(), clock)
          });
    }
    console.printPrivate(theme.entryTable(rows));
  }

  private void run(CliCommand command, VaultSession session)
      throws IOException, InterruptedException, DecryptionException {
    commands.executeInGuidedSession(command, session);
  }
}
