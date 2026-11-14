package cl.germain.vault.cli;

import cl.germain.vault.crypto.DecryptionException;
import cl.germain.vault.model.Entry;
import cl.germain.vault.model.Vault;
import cl.germain.vault.storage.VaultSession;
import cl.germain.vault.storage.VaultStorage;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Interactive command implementations backed by encrypted vault storage. */
public final class VaultCliCommands implements CliCommandHandler {
    private static final String CANCELLED = "Operación cancelada.";
    private final CliConsole console;
    private final VaultStorage storage;

    public VaultCliCommands(CliConsole console, VaultStorage storage) {
        this.console = Objects.requireNonNull(console);
        this.storage = Objects.requireNonNull(storage);
    }

    @Override
    public CliExitStatus execute(CliCommand command) throws IOException, DecryptionException, InterruptedException {
        return switch (command.type()) {
            case CREATE -> create(command);
            case ADD -> add(command);
            case LIST -> list(command);
            case SHOW -> show(command);
            case DELETE -> delete(command);
            case HELP -> CliExitStatus.SUCCESS;
        };
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

    private CliExitStatus add(CliCommand command) throws IOException, DecryptionException, InterruptedException {
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

    private CliExitStatus list(CliCommand command) throws IOException, DecryptionException, InterruptedException {
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
                        lines.add(CliArgumentParser.formatId(entry.id()) + " | "
                                + entry.service() + " | " + entry.username());
                    }
                }
            }
            lines.forEach(console::printOutput);
            return CliExitStatus.SUCCESS;
        } finally {
            clear(masterPassword);
        }
    }

    private CliExitStatus show(CliCommand command) throws IOException, DecryptionException, InterruptedException {
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

    private CliExitStatus delete(CliCommand command) throws IOException, DecryptionException, InterruptedException {
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
}
