package cl.germain.vault.cli;

import cl.germain.vault.crypto.DecryptionException;
import cl.germain.vault.storage.VaultSession;
import cl.germain.vault.storage.VaultStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** First-use wizard that opens a vault and transfers the authenticated session to the runner. */
public final class GuidedVaultApplication {
  private final CliConsole console;
  private final VaultStorage storage;
  private final CliSessionRunner runner;
  private final VaultPathConfig config;
  private final ConsoleTheme theme;

  public GuidedVaultApplication(
      CliConsole console, VaultStorage storage, CliSessionRunner runner, VaultPathConfig config) {
    this.console = Objects.requireNonNull(console);
    this.storage = Objects.requireNonNull(storage);
    this.runner = Objects.requireNonNull(runner);
    this.config = Objects.requireNonNull(config);
    this.theme = ConsoleTheme.system();
  }

  public int run() {
    if (!console.isAvailable()) {
      console.printError("Se necesita una terminal interactiva para usar este comando.");
      return CliExitStatus.ERROR.code();
    }
    VaultSession session = null;
    try {
      Optional<Path> configured;
      try {
        configured = config.read();
      } catch (IOException exception) {
        console.printError(
            "La configuración local no es válida; seleccione una bóveda explícitamente.");
        configured = Optional.empty();
      } catch (IllegalStateException exception) {
        console.printError(exception.getMessage());
        return CliExitStatus.ERROR.code();
      }
      Path selected;
      boolean create = false;
      if (configured.isPresent() && Files.isRegularFile(configured.get())) {
        selected = configured.get();
      } else {
        if (configured.isPresent()) {
          console.printOutput("La bóveda configurada no está disponible; seleccione una ruta.");
        }
        selected = null;
        boolean chooseAction = true;
        while (selected == null) {
          if (chooseAction) {
            console.printOutput(
                theme.wizard()
                    + System.lineSeparator()
                    + "Seleccione una acción:\n"
                    + theme.colorizeWizardOptions(
                        "[1] Crear una bóveda nueva\n[2] Abrir una bóveda existente\n[0] Salir"));
            String choice = console.readLine("Seleccione una opción: ");
            if (choice == null || "0".equals(choice)) return CliExitStatus.CANCELLED.code();
            if (!"1".equals(choice) && !"2".equals(choice)) {
              console.printError("Opción inválida.");
              continue;
            }
            create = "1".equals(choice);
            chooseAction = false;
          }

          String value = console.readLine("Ruta completa de la bóveda (0 para volver): ");
          if (value == null) return CliExitStatus.CANCELLED.code();
          if ("0".equals(value)) {
            chooseAction = true;
            continue;
          }
          try {
            selected = Path.of(value);
          } catch (RuntimeException exception) {
            console.printError("La ruta no es válida.");
            continue;
          }
          if (!selected.isAbsolute()
              || Files.isDirectory(selected)
              || (create && Files.exists(selected))
              || (!create && !Files.isRegularFile(selected))) {
            console.printError("La ruta no cumple los requisitos para esa opción.");
            selected = null;
          }
        }
      }
      session = create ? create(selected) : open(selected);
      if (session == null) return CliExitStatus.CANCELLED.code();
      config.write(selected);
      CliSessionRequest request =
          new CliSessionRequest(selected, CliSessionRequest.DEFAULT_TIMEOUT_MINUTES);
      VaultSession transferred = session;
      session = null;
      return runner.runGuided(request, transferred).code();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      console.printError("Operación cancelada o lectura interrumpida.");
      return CliExitStatus.ERROR.code();
    } catch (IOException | DecryptionException | RuntimeException exception) {
      console.printError("No se pudo completar la operación o guardar la configuración local.");
      return CliExitStatus.ERROR.code();
    } finally {
      if (session != null) {
        try {
          session.close();
        } catch (IOException ignored) {
          // Best-effort cleanup after failed setup.
        }
      }
    }
  }

  private VaultSession create(Path path) throws IOException, InterruptedException {
    char[] first = null;
    char[] second = null;
    try {
      first = console.readPassword("Contraseña maestra: ");
      if (first == null) return null;
      second = console.readPassword("Repita la contraseña maestra: ");
      if (second == null) return null;
      if (!Arrays.equals(first, second)) {
        console.printError("Las contraseñas no coinciden.");
        return null;
      }
      return storage.create(path, first);
    } finally {
      if (first != null) Arrays.fill(first, '\0');
      if (second != null) Arrays.fill(second, '\0');
    }
  }

  private VaultSession open(Path path)
      throws IOException, InterruptedException, DecryptionException {
    while (true) {
      char[] password = null;
      try {
        password = console.readPassword("Contraseña maestra: ");
        if (password == null) return null;
        try {
          return storage.open(path, password);
        } catch (DecryptionException exception) {
          console.printError("No se pudo abrir la bóveda.");
          if (!Confirmation.isAffirmative(
              console.readLine("¿Reintentar? Escriba sí o si para confirmar: "))) return null;
        }
      } finally {
        if (password != null) Arrays.fill(password, '\0');
      }
    }
  }
}
