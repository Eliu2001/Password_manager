package cl.germain.vault.cli;

import java.io.IOException;

/** Console boundary used by the CLI and replaceable in tests. */
public interface CliConsole {
    boolean isAvailable();

    /** Returns null when the user cancels or the input ends. */
    String readLine(String prompt) throws IOException, InterruptedException;

    /** Returns null when the user cancels or the input ends. */
    char[] readPassword(String prompt) throws IOException, InterruptedException;

    void printOutput(String message);

    void printError(String message);

    /** Writes secret characters only to the interactive console. */
    void printSecret(char[] secret) throws IOException;
}
