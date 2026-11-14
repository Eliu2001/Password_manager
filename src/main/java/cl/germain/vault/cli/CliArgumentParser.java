package cl.germain.vault.cli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Objects;

/** Parses and validates CLI arguments without accessing the filesystem. */
public final class CliArgumentParser {
    private static final String USAGE_ERROR = "Argumentos inválidos. Use --help para ver la ayuda.";
    private static final HexFormat HEX = HexFormat.of();

    public CliCommand parse(String... arguments) throws CliArgumentException {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length == 1 && "--help".equals(arguments[0])) {
            return new CliCommand(CliCommand.Type.HELP, null, null);
        }
        if (arguments.length == 0 || arguments[0] == null) {
            throw usageError();
        }

        CliCommand.Type type = commandType(arguments[0]);
        String vaultValue = null;
        String idValue = null;
        boolean sawVault = false;
        boolean sawId = false;

        for (int index = 1; index < arguments.length; index++) {
            String option = arguments[index];
            if ("--vault".equals(option)) {
                if (sawVault || index + 1 >= arguments.length || isOption(arguments[index + 1])) {
                    throw usageError();
                }
                sawVault = true;
                vaultValue = arguments[++index];
            } else if ("--id".equals(option)) {
                if (sawId || index + 1 >= arguments.length || isOption(arguments[index + 1])) {
                    throw usageError();
                }
                sawId = true;
                idValue = arguments[++index];
            } else {
                throw usageError();
            }
        }

        boolean needsId = type == CliCommand.Type.SHOW || type == CliCommand.Type.DELETE;
        if (!sawVault || sawId != needsId) {
            throw usageError();
        }

        Path path;
        try {
            path = Path.of(vaultValue);
            if (vaultValue.isEmpty()) {
                throw usageError();
            }
        } catch (InvalidPathException exception) {
            throw usageError();
        }

        byte[] id = needsId ? parseId(idValue) : null;
        return new CliCommand(type, path, id);
    }

    public static byte[] parseId(String value) throws CliArgumentException {
        if (value == null || value.length() != 32) {
            throw usageError();
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9') || (character >= 'a' && character <= 'f'))) {
                throw usageError();
            }
        }
        return HEX.parseHex(value);
    }

    public static String formatId(byte[] id) {
        if (id == null || id.length != 16) {
            throw new IllegalArgumentException("Entry ID must be 16 bytes");
        }
        return HEX.formatHex(id);
    }

    private static CliCommand.Type commandType(String command) throws CliArgumentException {
        return switch (command) {
            case "create" -> CliCommand.Type.CREATE;
            case "add" -> CliCommand.Type.ADD;
            case "list" -> CliCommand.Type.LIST;
            case "show" -> CliCommand.Type.SHOW;
            case "delete" -> CliCommand.Type.DELETE;
            default -> throw usageError();
        };
    }

    private static boolean isOption(String value) {
        return value == null || value.startsWith("--");
    }

    private static CliArgumentException usageError() {
        return new CliArgumentException(USAGE_ERROR);
    }
}
