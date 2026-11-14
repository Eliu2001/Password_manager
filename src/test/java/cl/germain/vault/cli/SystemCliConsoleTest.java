package cl.germain.vault.cli;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SystemCliConsoleTest {
    @Test
    void absentSystemConsoleNeverFallsBackForInputOrSecretOutput() {
        SystemCliConsole console = new SystemCliConsole(null);

        assertFalse(console.isAvailable());
        assertThrows(IOException.class, () -> console.readLine("prompt"));
        assertThrows(IOException.class, () -> console.readPassword("password"));
        assertThrows(IOException.class, () -> console.printSecret(new char[]{'x'}));
    }
}
