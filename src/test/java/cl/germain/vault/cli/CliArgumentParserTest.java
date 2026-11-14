package cl.germain.vault.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliArgumentParserTest {
    private final CliArgumentParser parser = new CliArgumentParser();

    @Test
    void parsesEveryOperationAndHelpWithoutTouchingFiles() throws Exception {
        assertEquals(CliCommand.Type.HELP, parser.parse("--help").type());
        assertEquals(CliCommand.Type.CREATE, parser.parse("create", "--vault", "safe.pvlt").type());
        assertEquals(CliCommand.Type.ADD, parser.parse("add", "--vault", "safe.pvlt").type());
        assertEquals(CliCommand.Type.LIST, parser.parse("list", "--vault", "safe.pvlt").type());
        assertEquals(CliCommand.Type.SHOW,
                parser.parse("show", "--vault", "safe.pvlt", "--id", "00112233445566778899aabbccddeeff").type());
        assertEquals(CliCommand.Type.DELETE,
                parser.parse("delete", "--id", "00112233445566778899aabbccddeeff", "--vault", "safe.pvlt").type());
        assertEquals(Path.of("safe.pvlt"), parser.parse("list", "--vault", "safe.pvlt").vaultPath());
    }

    @Test
    void rejectsMissingUnknownRepeatedAndMisplacedArguments() {
        assertInvalid();
        assertInvalid("unknown", "--vault", "x");
        assertInvalid("list");
        assertInvalid("list", "--vault");
        assertInvalid("list", "--vault", "x", "--vault", "y");
        assertInvalid("list", "--vault", "x", "--id", "00112233445566778899aabbccddeeff");
        assertInvalid("show", "--vault", "x");
        assertInvalid("show", "--vault", "x", "--id");
        assertInvalid("show", "--vault", "x", "--id", "00112233445566778899aabbccddeeff",
                "--id", "00112233445566778899aabbccddeeff");
        assertInvalid("list", "--vault", "x", "--unknown");
        assertInvalid("--help", "list", "--vault", "x");
        assertInvalid("list", "--vault", "");
    }

    @Test
    void parsesAndFormatsOnlyLowercaseThirtyTwoCharacterHexIds() throws Exception {
        String encoded = "00112233445566778899aabbccddeeff";
        byte[] decoded = CliArgumentParser.parseId(encoded);
        assertEquals(16, decoded.length);
        assertEquals(encoded, CliArgumentParser.formatId(decoded));

        byte[] returned = parser.parse("show", "--vault", "vault", "--id", encoded).entryId();
        assertArrayEquals(decoded, returned);
        assertNotSame(returned, parser.parse("show", "--vault", "vault", "--id", encoded).entryId());
        Arrays.fill(decoded, (byte) 0);
        Arrays.fill(returned, (byte) 0);

        assertInvalid("show", "--vault", "vault", "--id", "00112233445566778899AABBCCDDEEFF");
        assertInvalid("delete", "--vault", "vault", "--id", "00112233445566778899aabbccddeef");
        assertInvalid("show", "--vault", "vault", "--id", "00112233445566778899aabbccddeefg");
        assertThrows(IllegalArgumentException.class, () -> CliArgumentParser.formatId(new byte[15]));
    }

    @Test
    void argumentErrorIsSpanishAndContainsNoStackTraceOrInputValue() {
        CliArgumentException error = assertThrows(CliArgumentException.class,
                () -> parser.parse("list", "--vault", "private-name", "--secret", "synthetic-secret"));
        assertTrue(error.getMessage().contains("Argumentos inválidos"));
        assertTrue(!error.getMessage().contains("private-name"));
        assertTrue(!error.getMessage().contains("synthetic-secret"));
        assertEquals(null, error.getCause());
    }

    private void assertInvalid(String... arguments) {
        assertThrows(CliArgumentException.class, () -> parser.parse(arguments));
    }
}
