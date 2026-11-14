package cl.germain.vault.cli;

import java.nio.file.Path;
import java.util.Objects;

/** A validated CLI operation and its non-secret arguments. */
public record CliCommand(Type type, Path vaultPath, byte[] entryId) {
    public enum Type {
        HELP,
        CREATE,
        ADD,
        LIST,
        SHOW,
        DELETE
    }

    public CliCommand {
        Objects.requireNonNull(type, "type");
        entryId = entryId == null ? null : entryId.clone();

        if (type == Type.HELP) {
            if (vaultPath != null || entryId != null) {
                throw new IllegalArgumentException("Help command cannot have operation arguments");
            }
        } else {
            Objects.requireNonNull(vaultPath, "vaultPath");
            boolean needsId = type == Type.SHOW || type == Type.DELETE;
            if (needsId != (entryId != null)) {
                throw new IllegalArgumentException("Entry ID does not match command");
            }
        }
    }

    @Override
    public byte[] entryId() {
        return entryId == null ? null : entryId.clone();
    }
}
