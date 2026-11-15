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
      case ADD -> add(command);
      case LIST -> list(command);
      case SHOW -> show(command);
      case DELETE -> delete(command);
      case GENERATE -> generate(command);
      case SET_TOTP -> setTotp(command);
      case REMOVE_TOTP -> removeTotp(command);
      case TOTP -> totp(command);
      case UPGRADE -> upgrade(command);
      case HELP -> CliExitStatus.SUCCESS;
    };
  }

  private CliExitStatus setTotp(CliCommand command)
      throws IOException, DecryptionException, InterruptedException {
    char[] master = null, first = null, second = null;
    byte[] decoded = null;
    try {
      master = console.readPassword("Contraseña maestra: ");
      if (master == null) return inputCancelled();
      try (VaultSession session = storage.open(command.vaultPath(), master)) {
        if (session.version() != 2) {
          console.printError("Ejecute upgrade antes de gestionar TOTP.");
          return CliExitStatus.ERROR;
        }
        var entry = session.vault().get(command.entryId());
        if (entry.isEmpty()) {
          console.printError("No se encontró la entrada solicitada.");
          return CliExitStatus.ERROR;
        }
        first = console.readPassword("Secreto TOTP Base32: ");
        if (first == null) return inputCancelled();
        second = console.readPassword("Repita el secreto TOTP: ");
        if (second == null) return inputCancelled();
        if (!Arrays.equals(first, second)) {
          console.printError("Los secretos no coinciden.");
          return CliExitStatus.ERROR;
        }
        decoded = Base32.decode(first);
        byte[] old = session.vault().totpSecret(command.entryId()).orElse(null);
        if (old != null) {
          clear(old);
          if (!"sí"
              .equals(console.readLine("¿Reemplazar el secreto TOTP? Escriba sí para confirmar: ")))
            return CliExitStatus.CANCELLED;
        }
        session.vault().setTotpSecret(command.entryId(), decoded);
        session.save();
      }
      console.printOutput("Secreto TOTP guardado.");
      return CliExitStatus.SUCCESS;
    } finally {
      clear(master, first, second);
      clear(decoded);
    }
  }

  private CliExitStatus removeTotp(CliCommand command)
      throws IOException, DecryptionException, InterruptedException {
    char[] master = null;
    byte[] secret = null;
    try {
      master = console.readPassword("Contraseña maestra: ");
      if (master == null) return inputCancelled();
      try (VaultSession session = storage.open(command.vaultPath(), master)) {
        if (session.version() != 2) {
          console.printError("Ejecute upgrade antes de gestionar TOTP.");
          return CliExitStatus.ERROR;
        }
        secret = session.vault().totpSecret(command.entryId()).orElse(null);
        if (secret == null) {
          console.printError("La entrada no tiene secreto TOTP.");
          return CliExitStatus.ERROR;
        }
        if (!"sí".equals(console.readLine("¿Retirar el secreto TOTP? Escriba sí para confirmar: ")))
          return CliExitStatus.CANCELLED;
        session.vault().removeTotpSecret(command.entryId());
        session.save();
      }
      console.printOutput("Secreto TOTP retirado.");
      return CliExitStatus.SUCCESS;
    } finally {
      clear(master);
      clear(secret);
    }
  }

  private CliExitStatus totp(CliCommand command)
      throws IOException, DecryptionException, InterruptedException {
    char[] master = null, code = null;
    byte[] secret = null;
    try {
      master = console.readPassword("Contraseña maestra: ");
      if (master == null) return inputCancelled();
      try (VaultSession session = storage.open(command.vaultPath(), master)) {
        if (session.version() != 2) {
          console.printError("Ejecute upgrade antes de consultar TOTP.");
          return CliExitStatus.ERROR;
        }
        secret = session.vault().totpSecret(command.entryId()).orElse(null);
        if (secret == null) {
          console.printError("La entrada no tiene secreto TOTP.");
          return CliExitStatus.ERROR;
        }
        TotpGenerator.Result result = new TotpGenerator(clock).generateCurrent(secret);
        int remaining = result.secondsRemaining();
        code = result.code();
        console.printSecret(code);
        console.printOutput("Tiempo restante: " + remaining + " segundos.");
      }
      return CliExitStatus.SUCCESS;
    } finally {
      clear(master, code);
      clear(secret);
    }
  }

  private CliExitStatus upgrade(CliCommand command)
      throws IOException, DecryptionException, InterruptedException {
    char[] master = null;
    try {
      master = console.readPassword("Contraseña maestra: ");
      if (master == null) return inputCancelled();
      try (VaultSession session = storage.open(command.vaultPath(), master)) {
        if (session.version() != 1) {
          console.printError("La bóveda ya está en formato v2.");
          return CliExitStatus.ERROR;
        }
        if (!"sí"
            .equals(console.readLine("¿Convertir la bóveda a v2? Escriba sí para confirmar: ")))
          return CliExitStatus.CANCELLED;
        session.upgradeToV2();
      }
      console.printOutput("Bóveda actualizada a formato v2.");
      return CliExitStatus.SUCCESS;
    } finally {
      clear(master);
    }
  }

  private CliExitStatus generate(CliCommand command) throws IOException {
    char[] password = null;
    try {
      password = passwordGenerator.apply(command.generationLength());
      console.printSecret(password);
      console.printOutput("Contraseña generada.");
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

  private CliExitStatus add(CliCommand command)
      throws IOException, DecryptionException, InterruptedException {
    char[] masterPassword = null;
    char[] entryPassword = null;
    try {
      masterPassword = console.readPassword("Contraseña maestra: ");
      if (masterPassword == null) return inputCancelled();
      String formattedId;
      try (VaultSession session = storage.open(command.vaultPath(), masterPassword)) {
        String service = console.readLine("Servicio: ");
        if (service == null) return inputCancelled();
        String username = console.readLine("Usuario: ");
        if (username == null) return inputCancelled();
        entryPassword = console.readPassword("Contraseña de la entrada: ");
        if (entryPassword == null) return inputCancelled();
        String notes = console.readLine("Notas (opcional): ");
        if (notes == null) return inputCancelled();
        if (service.isEmpty() || username.isEmpty() || entryPassword.length == 0) {
          console.printError("Servicio, usuario y contraseña son obligatorios.");
          return CliExitStatus.ERROR;
        }

        byte[] id = session.vault().add(service, username, entryPassword, notes);
        try {
          session.save();
          formattedId = CliArgumentParser.formatId(id);
        } finally {
          Arrays.fill(id, (byte) 0);
        }
      }
      console.printOutput("Entrada guardada. ID: " + formattedId);
      return CliExitStatus.SUCCESS;
    } finally {
      clear(masterPassword, entryPassword);
    }
  }

  private CliExitStatus list(CliCommand command)
      throws IOException, DecryptionException, InterruptedException {
    char[] masterPassword = null;
    try {
      masterPassword = console.readPassword("Contraseña maestra: ");
      if (masterPassword == null) return inputCancelled();
      List<String> lines = new java.util.ArrayList<>();
      try (VaultSession session = storage.open(command.vaultPath(), masterPassword)) {
        List<Entry> entries = session.vault().list();
        if (entries.isEmpty()) {
          lines.add("La bóveda está vacía.");
        } else {
          for (Entry entry : entries) {
            lines.add(
                CliArgumentParser.formatId(entry.id())
                    + " | "
                    + entry.service()
                    + " | "
                    + entry.username());
          }
        }
      }
      lines.forEach(console::printOutput);
      return CliExitStatus.SUCCESS;
    } finally {
      clear(masterPassword);
    }
  }

  private CliExitStatus show(CliCommand command)
      throws IOException, DecryptionException, InterruptedException {
    char[] masterPassword = null;
    char[] entryPassword = null;
    try {
      masterPassword = console.readPassword("Contraseña maestra: ");
      if (masterPassword == null) return inputCancelled();
      String idText;
      String service;
      String username;
      String notes;
      boolean reveal;
      try (VaultSession session = storage.open(command.vaultPath(), masterPassword)) {
        Optional<Entry> found = session.vault().get(command.entryId());
        if (found.isEmpty()) {
          console.printError("No se encontró la entrada solicitada.");
          return CliExitStatus.ERROR;
        }
        Entry entry = found.orElseThrow();
        idText = CliArgumentParser.formatId(entry.id());
        service = entry.service();
        username = entry.username();
        notes = entry.notes();
        String answer = console.readLine("¿Revelar la contraseña? Escriba sí para confirmar: ");
        reveal = "sí".equals(answer);
        if (reveal) entryPassword = entry.password();
      }
      console.printOutput("ID: " + idText);
      console.printOutput("Servicio: " + service);
      console.printOutput("Usuario: " + username);
      console.printOutput("Notas: " + notes);
      if (reveal) console.printSecret(entryPassword);
      else return CliExitStatus.CANCELLED;
      return CliExitStatus.SUCCESS;
    } finally {
      clear(masterPassword, entryPassword);
    }
  }

  private CliExitStatus delete(CliCommand command)
      throws IOException, DecryptionException, InterruptedException {
    char[] masterPassword = null;
    try {
      masterPassword = console.readPassword("Contraseña maestra: ");
      if (masterPassword == null) return inputCancelled();
      try (VaultSession session = storage.open(command.vaultPath(), masterPassword)) {
        Optional<Entry> found = session.vault().get(command.entryId());
        if (found.isEmpty()) {
          console.printError("No se encontró la entrada solicitada.");
          return CliExitStatus.ERROR;
        }
        Entry entry = found.orElseThrow();
        console.printOutput("ID: " + CliArgumentParser.formatId(entry.id()));
        console.printOutput("Servicio: " + entry.service());
        console.printOutput("Usuario: " + entry.username());
        String answer = console.readLine("¿Borrar esta entrada? Escriba sí para confirmar: ");
        if (!"sí".equals(answer)) return CliExitStatus.CANCELLED;
        session.vault().remove(command.entryId());
        session.save();
      }
      console.printOutput("Entrada borrada correctamente.");
      return CliExitStatus.SUCCESS;
    } finally {
      clear(masterPassword);
    }
  }

  private CliExitStatus inputCancelled() {
    console.printError(CANCELLED);
    return CliExitStatus.ERROR;
  }

  private static void clear(char[]... arrays) {
    for (char[] array : arrays) {
      if (array != null) Arrays.fill(array, '\0');
    }
  }

  private static void clear(byte[] bytes) {
    if (bytes != null) Arrays.fill(bytes, (byte) 0);
  }
}
