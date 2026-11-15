package cl.germain.vault.cli;

import cl.germain.vault.crypto.DecryptionException;
import cl.germain.vault.storage.VaultSession;
import cl.germain.vault.storage.VaultStorage;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

/** Owns the authenticated storage session and enforces idle locking. */
public final class CliSessionRunner {
  private static final String SESSION_HELP =
      "Comandos de sesión: add, list, show --id <id>, delete --id <id>, "
          + "set-totp --id <id>, remove-totp --id <id>, totp --id <id>, upgrade, help, exit";
  private static final String LOCKED = "Bóveda bloqueada por inactividad.";
  private final CliConsole console;
  private final VaultStorage storage;
  private final VaultCliCommands commands;
  private final CliArgumentParser parser;
  private final MonotonicTicker ticker;

  public CliSessionRunner(
      CliConsole console,
      VaultStorage storage,
      VaultCliCommands commands,
      CliArgumentParser parser,
      MonotonicTicker ticker) {
    this.console = Objects.requireNonNull(console);
    this.storage = Objects.requireNonNull(storage);
    this.commands = Objects.requireNonNull(commands);
    this.parser = Objects.requireNonNull(parser);
    this.ticker = Objects.requireNonNull(ticker);
  }

  public CliExitStatus run(CliSessionRequest request) throws IOException, InterruptedException {
    long timeoutNanos = Duration.ofMinutes(request.timeoutMinutes()).toNanos();
    VaultSession session = null;
    try {
      session = authenticate(request);
      if (session == null) return CliExitStatus.CANCELLED;
      long intervalStarted = ticker.read();
      while (true) {
        long remaining = timeoutNanos - (ticker.read() - intervalStarted);
        if (remaining <= 0) {
          session.close();
          session = null;
          console.discardPendingLine();
          console.printOutput(LOCKED);
          session = authenticate(request);
          if (session == null) return CliExitStatus.CANCELLED;
          intervalStarted = ticker.read();
          continue;
        }

        TimedLineResult input = console.readLineUntil("vault> ", remaining);
        if (input.kind() == TimedLineResult.Kind.TIMED_OUT) {
          session.close();
          session = null;
          console.printOutput(LOCKED + " Pulse Enter para descartar la entrada pendiente.");
          console.discardPendingLine();
          session = authenticate(request);
          if (session == null) return CliExitStatus.CANCELLED;
          intervalStarted = ticker.read();
          continue;
        }
        if (input.kind() == TimedLineResult.Kind.END_OF_INPUT) return CliExitStatus.CANCELLED;

        if (timeoutNanos - (ticker.read() - intervalStarted) <= 0) {
          session.close();
          session = null;
          console.printOutput(LOCKED + " Se descartó la entrada recibida.");
          session = authenticate(request);
          if (session == null) return CliExitStatus.CANCELLED;
          intervalStarted = ticker.read();
          continue;
        }

        CliSessionCommand command;
        try {
          command = parser.parseSessionCommand(input.line(), request.vaultPath());
        } catch (CliArgumentException exception) {
          console.printError(exception.getMessage());
          intervalStarted = ticker.read();
          continue;
        }

        if (command.kind() == CliSessionCommand.Kind.HELP) {
          console.printOutput(SESSION_HELP);
          intervalStarted = ticker.read();
          continue;
        }
        if (command.kind() == CliSessionCommand.Kind.EXIT) return CliExitStatus.SUCCESS;

        try {
          commands.executeInSession(command.vaultCommand(), session);
        } catch (IllegalArgumentException exception) {
          console.printError("Los datos introducidos no son válidos.");
          intervalStarted = ticker.read();
        } catch (IOException | RuntimeException exception) {
          console.printError("No se pudo completar la operación.");
          return CliExitStatus.ERROR;
        } catch (DecryptionException exception) {
          console.printError("No se pudo completar la operación.");
          return CliExitStatus.ERROR;
        }
        long commandFinished = ticker.read();
        if (timeoutNanos - (commandFinished - intervalStarted) <= 0) {
          session.close();
          session = null;
          console.printOutput(LOCKED);
          session = authenticate(request);
          if (session == null) return CliExitStatus.CANCELLED;
          intervalStarted = ticker.read();
        } else {
          intervalStarted = commandFinished;
        }
      }
    } finally {
      try {
        if (session != null) session.close();
      } finally {
        console.close();
      }
    }
  }

  private VaultSession authenticate(CliSessionRequest request)
      throws IOException, InterruptedException {
    while (true) {
      char[] masterPassword = null;
      try {
        masterPassword = console.readPassword("Contraseña maestra: ");
        if (masterPassword == null) return null;
        try {
          return storage.open(request.vaultPath(), masterPassword);
        } catch (DecryptionException | IOException exception) {
          console.printError("No se pudo abrir la bóveda.");
          if (!"sí".equals(console.readLine("¿Reintentar? Escriba sí para confirmar: "))) {
            return null;
          }
        }
      } finally {
        if (masterPassword != null) java.util.Arrays.fill(masterPassword, '\0');
      }
    }
  }
}
