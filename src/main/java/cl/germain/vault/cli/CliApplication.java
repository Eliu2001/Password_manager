package cl.germain.vault.cli;

import java.io.IOException;
import java.util.Objects;

/** Parses and dispatches CLI requests while enforcing the common console gate. */
public final class CliApplication {
  private static final String HELP =
      "Uso:\n"
          + "  Main create --vault <ruta>\n"
          + "  Main add --vault <ruta>\n"
          + "  Main list --vault <ruta>\n"
          + "  Main show --vault <ruta> --id <id>\n"
          + "  Main delete --vault <ruta> --id <id>\n"
          + "  Main generate [--length <n>] (predeterminado: 20; rango: 12–128)\n"
          + "  Main set-totp --vault <ruta> --id <id>\n"
          + "  Main remove-totp --vault <ruta> --id <id>\n"
          + "  Main totp --vault <ruta> --id <id>\n"
          + "  Main upgrade --vault <ruta>\n"
          + "  Main session --vault <ruta> [--timeout-minutes <n>] (predeterminado: 5; rango: 1–60)\n"
          + "  Main --help\n"
          + "Comandos: create, add, list, show, delete, generate, set-totp, remove-totp, totp, upgrade, session";
  private static final String CONSOLE_REQUIRED =
      "Se necesita una terminal interactiva para usar este comando.";
  private static final String INPUT_CANCELLED = "Operación cancelada o lectura interrumpida.";
  private final CliArgumentParser parser;
  private final CliConsole console;
  private final CliCommandHandler handler;
  private final CliSessionRunner sessionRunner;

  public CliApplication(CliArgumentParser parser, CliConsole console) {
    this(parser, console, unavailableHandler(console), null);
  }

  public CliApplication(CliArgumentParser parser, CliConsole console, CliCommandHandler handler) {
    this(parser, console, handler, null);
  }

  public CliApplication(
      CliArgumentParser parser,
      CliConsole console,
      CliCommandHandler handler,
      CliSessionRunner sessionRunner) {
    this.parser = Objects.requireNonNull(parser);
    this.console = Objects.requireNonNull(console);
    this.handler = Objects.requireNonNull(handler);
    this.sessionRunner = sessionRunner;
  }

  public int run(String... arguments) {
    if (arguments != null && arguments.length > 0 && "session".equals(arguments[0])) {
      return runSession(arguments);
    }
    CliCommand command;
    try {
      command = parser.parse(arguments);
    } catch (CliArgumentException exception) {
      console.printError(exception.getMessage());
      return CliExitStatus.ERROR.code();
    }

    if (command.type() == CliCommand.Type.HELP) {
      console.printOutput(HELP);
      return CliExitStatus.SUCCESS.code();
    }
    if (!console.isAvailable()) {
      console.printError(CONSOLE_REQUIRED);
      return CliExitStatus.ERROR.code();
    }

    try {
      return dispatch(command);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      console.printError(INPUT_CANCELLED);
      return CliExitStatus.ERROR.code();
    } catch (IOException exception) {
      console.printError("No se pudo completar la operación.");
      return CliExitStatus.ERROR.code();
    } catch (Exception exception) {
      console.printError("No se pudo completar la operación.");
      return CliExitStatus.ERROR.code();
    }
  }

  private int runSession(String[] arguments) {
    CliSessionRequest request;
    try {
      request = parser.parseSession(arguments);
    } catch (CliArgumentException exception) {
      console.printError(exception.getMessage());
      return CliExitStatus.ERROR.code();
    }
    if (!console.isAvailable()) {
      console.printError(CONSOLE_REQUIRED);
      return CliExitStatus.ERROR.code();
    }
    if (sessionRunner == null) {
      console.printError("Este comando aún no está disponible.");
      return CliExitStatus.ERROR.code();
    }
    try {
      return sessionRunner.run(request).code();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      console.printError(INPUT_CANCELLED);
      return CliExitStatus.ERROR.code();
    } catch (Exception exception) {
      console.printError("No se pudo completar la operación.");
      return CliExitStatus.ERROR.code();
    }
  }

  private int dispatch(CliCommand command) throws Exception {
    return handler.execute(command).code();
  }

  private static CliCommandHandler unavailableHandler(CliConsole console) {
    return command -> {
      console.printError("Este comando aún no está disponible.");
      return CliExitStatus.ERROR;
    };
  }
}
