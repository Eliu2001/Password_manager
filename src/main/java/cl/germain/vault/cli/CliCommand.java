package cl.germain.vault.cli;

import cl.germain.vault.generator.PasswordGenerator;
import java.nio.file.Path;
import java.util.Objects;

/** A validated CLI operation and its non-secret arguments. */
public record CliCommand(Type type, Path vaultPath, byte[] entryId, int generationLength) {
    public static final int DEFAULT_GENERATION_LENGTH = 20;

    public enum Type {
        HELP,
        CREATE,
        ADD,
        LIST,
        SHOW,
        DELETE,
        GENERATE
    }

    public CliCommand(Type type, Path vaultPath, byte[] entryId) {
        this(type, vaultPath, entryId, DEFAULT_GENERATION_LENGTH);
    }

    public CliCommand {
        Objects.requireNonNull(type, "type");
        entryId = entryId == null ? null : entryId.clone();

        if (type == Type.HELP || type == Type.GENERATE) {
            if (vaultPath != null || entryId != null) {
                throw new IllegalArgumentException("Command cannot have vault arguments");
            }
            if (type == Type.GENERATE
                    && (generationLength < PasswordGenerator.MIN_LENGTH
                            || generationLength > PasswordGenerator.MAX_LENGTH)) {
                throw new IllegalArgumentException("Generation length is outside the supported range");
            }
            if (type == Type.HELP && generationLength != DEFAULT_GENERATION_LENGTH) {
                throw new IllegalArgumentException("Help command cannot have operation arguments");
            }
        } else {
            Objects.requireNonNull(vaultPath, "vaultPath");
            boolean needsId = type == Type.SHOW || type == Type.DELETE;
            if (needsId != (entryId != null) || generationLength != DEFAULT_GENERATION_LENGTH) {
                throw new IllegalArgumentException("Entry ID does not match command");
            }
        }
    }

    @Override
    public byte[] entryId() {
        return entryId == null ? null : entryId.clone();
    }
}
