package cl.germain.vault.cli;

/** Process exit statuses for CLI outcomes. */
public enum CliExitStatus {
    SUCCESS(0),
    CANCELLED(0),
    ERROR(1);

    private final int code;

    CliExitStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
