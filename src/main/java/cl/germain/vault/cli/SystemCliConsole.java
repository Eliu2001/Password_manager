package cl.germain.vault.cli;

import java.io.Console;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

/** Production console adapter. Secret input/output never falls back to standard streams. */
public final class SystemCliConsole implements CliConsole {
    private final Console console;

    public SystemCliConsole() {
        this(System.console());
    }

    SystemCliConsole(Console console) {
        this.console = console;
    }

    @Override
    public boolean isAvailable() {
        return console != null;
    }

    @Override
    public String readLine(String prompt) throws IOException {
        requireConsole();
        try {
            String result = console.readLine("%s", prompt);
            if (result == null) {
                return null;
            }
            return result;
        } catch (java.io.IOError error) {
            throw new IOException("Console input failed");
        }
    }

    @Override
    public char[] readPassword(String prompt) throws IOException {
        requireConsole();
        try {
            return console.readPassword("%s", prompt);
        } catch (java.io.IOError error) {
            throw new IOException("Console input failed");
        }
    }

    @Override
    public void printOutput(String message) {
        System.out.println(Objects.requireNonNull(message));
    }

    @Override
    public void printError(String message) {
        System.err.println(Objects.requireNonNull(message));
    }

    @Override
    public void printSecret(char[] secret) throws IOException {
        requireConsole();
        PrintWriter writer = console.writer();
        writer.write(secret);
        writer.println();
        writer.flush();
        if (writer.checkError()) {
            throw new IOException("Console output failed");
        }
    }

    private void requireConsole() throws IOException {
        if (console == null) {
            throw new IOException("Interactive console is unavailable");
        }
    }
}
