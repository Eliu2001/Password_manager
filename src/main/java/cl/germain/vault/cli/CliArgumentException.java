package cl.germain.vault.cli;

/** An argument error safe to present to the user. */
public final class CliArgumentException extends Exception {
    public CliArgumentException(String message) {
        super(message);
    }
}
