package cl.germain.vault.cli;

import cl.germain.vault.crypto.DecryptionException;
import cl.germain.vault.generator.PasswordGenerator;
import cl.germain.vault.model.Entry;
import cl.germain.vault.storage.VaultSession;
import cl.germain.vault.storage.VaultStorage;
import cl.germain.vault.totp.Base32;
import cl.germain.vault.totp.TotpGenerator;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;

/** Interactive command implementations backed by encrypted vault storage. */
public final class VaultCliCommands implements CliCommandHandler {
  private static final String CANCELLED = "Operación cancelada.";
  private final CliConsole console;
  private final VaultStorage storage;
  private final IntFunction<char[]> passwordGenerator;
  private final Clock clock;
  private boolean guidedOutput;

  public VaultCliCommands(CliConsole console, VaultStorage storage) {
    this(console, storage, new PasswordGenerator());
  }

  public VaultCliCommands(
      CliConsole console, VaultStorage storage, PasswordGenerator passwordGenerator) {
    this(console, storage, Objects.requireNonNull(passwordGenerator)::generate);
  }

  VaultCliCommands(
      CliConsole console, VaultStorage storage, IntFunction<char[]> passwordGenerator) {
    this(console, storage, passwordGenerator, Clock.systemUTC());
  }

  VaultCliCommands(
      CliConsole console,
      VaultStorage storage,
      IntFunction<char[]> passwordGenerator,
      Clock clock) {
    this.console = Objects.requireNonNull(console);
    this.storage = Objects.requireNonNull(storage);
    this.passwordGenerator = Objects.requireNonNull(passwordGenerator);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public CliExitStatus execute(CliCommand command)
      throws IOException, DecryptionException, InterruptedException {
    return switch (command.type()) {
      case CREATE -> create(command);
      case ADD -> runWithSession(command, null, session -> add(command, session));
      case LIST -> runWithSession(command, null, session -> list(session));
      case SHOW -> runWithSession(command, null, session -> show(command, session));
      case DELETE -> runWithSession(command, null, session -> delete(command, session, false));
      case GENERATE -> generate(command);
      case SET_TOTP -> runWithSession(command, null, session -> setTotp(command, session));
      case REMOVE_TOTP -> runWithSession(command, null, session -> removeTotp(command, session));
      case TOTP -> runWithSession(command, null, session -> totp(command, session));
      case UPGRADE -> runWithSession(command, null, session -> upgrade(session));
      case HELP -> CliExitStatus.SUCCESS;
    };
  }

  /** Executes a vault command using a session owned by the caller. */
  public CliExitStatus executeInSession(CliCommand command, VaultSession session)
      throws IOException, DecryptionException, InterruptedException {
    Objects.requireNonNull(session, "session");
    return switch (command.type()) {
      case ADD -> runWithSession(command, session, active -> add(command, active));
      case LIST -> runWithSession(command, session, this::list);
      case SHOW -> runWithSession(command, session, active -> show(command, active));
      case DELETE -> runWithSession(command, session, active -> delete(command, active, true));
      case SET_TOTP -> runWithSession(command, session, active -> setTotp(command, active));
      case REMOVE_TOTP -> runWithSession(command, session, active -> removeTotp(command, active));
      case TOTP -> runWithSession(command, session, active -> totp(command, active));
      case UPGRADE -> runWithSession(command, session, this::upgrade);
      default -> {
        console.printError("El comando no está disponible dentro de una sesión.");
        yield CliExitStatus.ERROR;
      }
    };
  }

  CliExitStatus executeInGuidedSession(CliCommand command, VaultSession session)
      throws IOException, DecryptionException, InterruptedException {
    guidedOutput = true;
    try {
      return executeInSession(command, session);
    } finally {
      guidedOutput = false;
    }
  }

  void updatePasswordInSession(byte[] entryId, VaultSession session)
      throws IOException, InterruptedException {
    char[] first = null;
    char[] second = null;
    try {
      Entry entry = session.vault().get(entryId).orElse(null);
      if (entry == null) {
        console.printError("No se encontró la entrada solicitada.");
        return;
      }
      first = console.readPassword("Nueva contraseña de la entrada: ");
      if (first == null) {
        console.printOutput("Operación cancelada.");
        return;
      }
      second = console.readPassword("Repita la nueva contraseña: ");
      if (second == null) {
        console.printOutput("Operación cancelada.");
        return;
      }
      if (!Arrays.equals(first, second)) {
        console.printError("Las contraseñas no coinciden; no se realizaron cambios.");
        return;
      }
      session.vault().update(entryId, entry.service(), entry.username(), first, entry.notes());
      session.save();
      console.printPrivate("Contraseña actualizada correctamente.");
    } finally {
      clear(first, second);
    }
  }

  private CliExitStatus runWithSession(
      CliCommand command, VaultSession activeSession, SessionOperation operation)
      throws IOException, DecryptionException, InterruptedException {
    CommandResult result = null;
    if (activeSession != null) {
      result = operation.execute(activeSession);
      return emit(result, true);
    }

    char[] masterPassword = null;
    boolean emitted = false;
    try {
      masterPassword = console.readPassword("Contraseña maestra: ");
      if (masterPassword == null) return inputCancelled();
      try (VaultSession session = storage.open(command.vaultPath(), masterPassword)) {
        result = operation.execute(session);
      }
      CliExitStatus status = emit(result, false);
      emitted = true;
      return status;
    } finally {
      clear(masterPassword);
      if (!emitted && result != null) clear(result.secret());
    }
  }

  private CliExitStatus emit(CommandResult result, boolean privateOutput) throws IOException {
    try {
      if (result.secretFirst() && result.secret() != null) console.printSecret(result.secret());
      for (String line : result.output()) {
        if (privateOutput) console.printPrivate(line);
        else console.printOutput(line);
      }
      if (!result.secretFirst() && result.secret() != null) console.printSecret(result.secret());
      return result.status();
    } finally {
      clear(result.secret());
    }
  }

  private CommandResult setTotp(CliCommand command, VaultSession session)
      throws IOException, InterruptedException {
    char[] first = null, second = null;
    byte[] decoded = null;
    try {
      if (session.version() != 2) {
        console.printError("Ejecute upgrade antes de gestionar TOTP.");
        return CommandResult.status(CliExitStatus.ERROR);
      }
      if (session.vault().get(command.entryId()).isEmpty()) {
        console.printError("No se encontró la entrada solicitada.");
        return CommandResult.status(CliExitStatus.ERROR);
      }
      first = console.readPassword("Secreto TOTP Base32: ");
      if (first == null) return inputCancelledResult();
      second = console.readPassword("Repita el secreto TOTP: ");
      if (second == null) return inputCancelledResult();
      if (!Arrays.equals(first, second)) {
        console.printError("Los secretos no coinciden.");
        return CommandResult.status(CliExitStatus.ERROR);
      }
      decoded = Base32.decode(first);
      byte[] old = session.vault().totpSecret(command.entryId()).orElse(null);
      if (old != null) {
        clear(old);
        if (!Confirmation.isAffirmative(
            console.readLine("¿Reemplazar el secreto TOTP? Escriba sí o si para confirmar: ")))
          return CommandResult.status(CliExitStatus.CANCELLED);
      }
      session.vault().setTotpSecret(command.entryId(), decoded);
      session.save();
      return CommandResult.output(CliExitStatus.SUCCESS, "Secreto TOTP guardado.");
    } finally {
      clear(first, second);
      clear(decoded);
    }
  }

  private CommandResult removeTotp(CliCommand command, VaultSession session)
      throws IOException, InterruptedException {
    byte[] secret = null;
    try {
      if (session.version() != 2) {
        console.printError("Ejecute upgrade antes de gestionar TOTP.");
        return CommandResult.status(CliExitStatus.ERROR);
      }
      secret = session.vault().totpSecret(command.entryId()).orElse(null);
      if (secret == null) {
        console.printError("La entrada no tiene secreto TOTP.");
        return CommandResult.status(CliExitStatus.ERROR);
      }
      if (!Confirmation.isAffirmative(
          console.readLine("¿Retirar el secreto TOTP? Escriba sí o si para confirmar: ")))
        return CommandResult.status(CliExitStatus.CANCELLED);
      session.vault().removeTotpSecret(command.entryId());
      session.save();
      return CommandResult.output(CliExitStatus.SUCCESS, "Secreto TOTP retirado.");
    } finally {
      clear(secret);
    }
  }

  private CommandResult totp(CliCommand command, VaultSession session) throws IOException {
    byte[] secret = null;
    try {
      if (session.version() != 2) {
        console.printError("Ejecute upgrade antes de consultar TOTP.");
        return CommandResult.status(CliExitStatus.ERROR);
      }
      secret = session.vault().totpSecret(command.entryId()).orElse(null);
      if (secret == null) {
        console.printError("La entrada no tiene secreto TOTP.");
        return CommandResult.status(CliExitStatus.ERROR);
      }
      TotpGenerator.Result result = new TotpGenerator(clock).generateCurrent(secret);
      char[] code = result.code();
      return CommandResult.secret(
          code, "Tiempo restante: " + result.secondsRemaining() + " segundos.");
    } finally {
      clear(secret);
    }
  }

  private CommandResult upgrade(VaultSession session) throws IOException, InterruptedException {
    if (session.version() != 1) {
      console.printError("La bóveda ya está en formato v2.");
      return CommandResult.status(CliExitStatus.ERROR);
    }
    if (!Confirmation.isAffirmative(
        console.readLine("¿Convertir la bóveda a v2? Escriba sí o si para confirmar: ")))
      return CommandResult.status(CliExitStatus.CANCELLED);
    session.upgradeToV2();
    return CommandResult.output(CliExitStatus.SUCCESS, "Bóveda actualizada a formato v2.");
  }

  private CliExitStatus generate(CliCommand command) throws IOException {
    char[] password = null;
    try {
      password = passwordGenerator.apply(command.generationLength());
      console.printSecret(password);
      console.printOutput("Contraseña generada. No se guarda automáticamente.");
      return CliExitStatus.SUCCESS;
    } finally {
      clear(password);
    }
  }

  private CliExitStatus create(CliCommand command) throws IOException, InterruptedException {
    char[] first = null;
    char[] confirmation = null;
    try {
      first = console.readPassword("Contraseña maestra: ");
      if (first == null) return inputCancelled();
      confirmation = console.readPassword("Repita la contraseña maestra: ");
      if (confirmation == null) return inputCancelled();
      if (!Arrays.equals(first, confirmation)) {
        console.printError("Las contraseñas no coinciden; no se creó la bóveda.");
        return CliExitStatus.ERROR;
      }
      try (VaultSession ignored = storage.create(command.vaultPath(), first)) {
        // Creation is not reported as successful until the session closes cleanly.
      }
      console.printOutput("Bóveda creada correctamente.");
      return CliExitStatus.SUCCESS;
    } finally {
      clear(first, confirmation);
    }
  }

  private CommandResult add(CliCommand command, VaultSession session)
      throws IOException, InterruptedException {
    char[] entryPassword = null;
    try {
      String service = console.readLine("Servicio: ");
      if (service == null) return inputCancelledResult();
      String username = console.readLine("Usuario: ");
      if (username == null) return inputCancelledResult();
      entryPassword = console.readPassword("Contraseña de la entrada: ");
      if (entryPassword == null) return inputCancelledResult();
      String notes = console.readLine("Notas (opcional): ");
      if (notes == null) return inputCancelledResult();
      if (service.isEmpty() || username.isEmpty() || entryPassword.length == 0) {
        console.printError("Servicio, usuario y contraseña son obligatorios.");
        return CommandResult.status(CliExitStatus.ERROR);
      }

      byte[] id = session.vault().add(service, username, entryPassword, notes);
      try {
        session.save();
        return CommandResult.output(
            CliExitStatus.SUCCESS,
            guidedOutput
                ? "Entrada guardada correctamente."
                : "Entrada guardada. ID: " + CliArgumentParser.formatId(id));
      } finally {
        Arrays.fill(id, (byte) 0);
      }
    } finally {
      clear(entryPassword);
    }
  }

  private CommandResult list(VaultSession session) {
    List<Entry> entries = session.vault().list();
    List<String> lines = new ArrayList<>();
    if (entries.isEmpty()) {
      lines.add("La bóveda está vacía.");
    } else {
      for (Entry entry : entries) {
        lines.add(
            (guidedOutput ? "" : CliArgumentParser.formatId(entry.id()) + " | ")
                + entry.service()
                + " | "
                + entry.username());
      }
    }
    return CommandResult.output(CliExitStatus.SUCCESS, lines);
  }

  private CommandResult show(CliCommand command, VaultSession session)
      throws IOException, InterruptedException {
    char[] entryPassword = null;
    try {
      Optional<Entry> found = session.vault().get(command.entryId());
      if (found.isEmpty()) {
        console.printError("No se encontró la entrada solicitada.");
        return CommandResult.status(CliExitStatus.ERROR);
      }
      Entry entry = found.orElseThrow();
      String answer = console.readLine("¿Revelar la contraseña? Escriba sí o si para confirmar: ");
      boolean reveal = Confirmation.isAffirmative(answer);
      if (reveal) entryPassword = entry.password();
      List<String> output = new ArrayList<>();
      if (!guidedOutput) output.add("ID: " + CliArgumentParser.formatId(entry.id()));
      output.add("Servicio: " + entry.service());
      output.add("Usuario: " + entry.username());
      output.add("Notas: " + entry.notes());
      if (guidedOutput && reveal) output.add("Contraseña:");
      char[] secret = entryPassword;
      entryPassword = null;
      return CommandResult.output(
          reveal ? CliExitStatus.SUCCESS : CliExitStatus.CANCELLED, output, secret);
    } finally {
      clear(entryPassword);
    }
  }

  private CommandResult delete(CliCommand command, VaultSession session, boolean privateOutput)
      throws IOException, InterruptedException {
    Optional<Entry> found = session.vault().get(command.entryId());
    if (found.isEmpty()) {
      console.printError("No se encontró la entrada solicitada.");
      return CommandResult.status(CliExitStatus.ERROR);
    }
    Entry entry = found.orElseThrow();
    if (!guidedOutput)
      printVaultContent("ID: " + CliArgumentParser.formatId(entry.id()), privateOutput);
    printVaultContent("Servicio: " + entry.service(), privateOutput);
    printVaultContent("Usuario: " + entry.username(), privateOutput);
    String answer = console.readLine("¿Borrar esta entrada? Escriba sí o si para confirmar: ");
    if (!Confirmation.isAffirmative(answer)) return CommandResult.status(CliExitStatus.CANCELLED);
    session.vault().remove(command.entryId());
    session.save();
    return CommandResult.output(CliExitStatus.SUCCESS, "Entrada borrada correctamente.");
  }

  private CliExitStatus inputCancelled() {
    console.printError(CANCELLED);
    return CliExitStatus.ERROR;
  }

  private CommandResult inputCancelledResult() {
    return CommandResult.status(inputCancelled());
  }

  private void printVaultContent(String line, boolean privateOutput) throws IOException {
    if (privateOutput) console.printPrivate(line);
    else console.printOutput(line);
  }

  private static void clear(char[]... arrays) {
    for (char[] array : arrays) {
      if (array != null) Arrays.fill(array, '\0');
    }
  }

  private static void clear(byte[] bytes) {
    if (bytes != null) Arrays.fill(bytes, (byte) 0);
  }

  @FunctionalInterface
  private interface SessionOperation {
    CommandResult execute(VaultSession session) throws IOException, InterruptedException;
  }

  private record CommandResult(
      CliExitStatus status, List<String> output, char[] secret, boolean secretFirst) {
    private CommandResult {
      Objects.requireNonNull(status);
      output = List.copyOf(output);
    }

    static CommandResult status(CliExitStatus status) {
      return new CommandResult(status, List.of(), null, false);
    }

    static CommandResult output(CliExitStatus status, String line) {
      return new CommandResult(status, List.of(line), null, false);
    }

    static CommandResult output(CliExitStatus status, List<String> lines) {
      return new CommandResult(status, lines, null, false);
    }

    static CommandResult output(CliExitStatus status, List<String> lines, char[] secret) {
      return new CommandResult(status, lines, secret, false);
    }

    static CommandResult secret(char[] secret, String line) {
      return new CommandResult(CliExitStatus.SUCCESS, List.of(line), secret, true);
    }
  }
}
