package cl.germain.vault.cli;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CliConsoleContractTest {
    @Test
    void replaceableConsoleKeepsOutputErrorAndSecretChannelsSeparate() throws Exception {
        RecordingConsole console = new RecordingConsole();
        console.printOutput("normal");
        console.printError("failure");
        char[] secret = {'s', 'y', 'n'};
        console.printSecret(secret);

        assertEquals("normal", console.output);
        assertEquals("failure", console.error);
        assertArrayEquals(secret, console.secret);
    }

    @Test
    void replaceableConsoleCanRepresentEndOfInputAndInterruption() throws Exception {
        RecordingConsole console = new RecordingConsole();
        assertNull(console.readLine("prompt"));
        assertNull(console.readPassword("password"));
        console.interrupted = true;
        assertThrows(InterruptedException.class, () -> console.readLine("prompt"));
        assertThrows(InterruptedException.class, () -> console.readPassword("password"));
    }

    private static final class RecordingConsole implements CliConsole {
        private String output;
        private String error;
        private char[] secret;
        private boolean interrupted;

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String readLine(String prompt) throws InterruptedException {
            if (interrupted) throw new InterruptedException();
            return null;
        }

        @Override
        public char[] readPassword(String prompt) throws InterruptedException {
            if (interrupted) throw new InterruptedException();
            return null;
        }

        @Override
        public void printOutput(String message) {
            output = message;
        }

        @Override
        public void printError(String message) {
            error = message;
        }

        @Override
        public void printSecret(char[] value) throws IOException {
            secret = value.clone();
        }
    }
}
