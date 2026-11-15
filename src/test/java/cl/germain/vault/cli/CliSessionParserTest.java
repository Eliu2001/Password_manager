package cl.germain.vault.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CliSessionParserTest {
  private final CliArgumentParser parser = new CliArgumentParser();

  @Test
  void parsesSessionWithDefaultAndExplicitTimeout() throws Exception {
    assertEquals(
        new CliSessionRequest(Path.of("vault.pvlt"), 5),
        parser.parseSession("session", "--vault", "vault.pvlt"));
    assertEquals(
        new CliSessionRequest(Path.of("vault.pvlt"), 60),
        parser.parseSession("session", "--vault", "vault.pvlt", "--timeout-minutes", "60"));
  }

  @Test
  void rejectsInvalidSessionOptions() {
    assertThrows(CliArgumentException.class, () -> parser.parseSession("session"));
    assertThrows(
        CliArgumentException.class,
        () -> parser.parseSession("session", "--vault", "a", "--vault", "b"));
    assertThrows(
        CliArgumentException.class,
        () -> parser.parseSession("session", "--vault", "a", "--timeout-minutes", "0"));
    assertThrows(
        CliArgumentException.class,
        () -> parser.parseSession("session", "--vault", "a", "--timeout-minutes", "61"));
    assertThrows(
        CliArgumentException.class,
        () -> parser.parseSession("session", "--vault", "a", "--timeout-minutes", "5x"));
    assertThrows(
        CliArgumentException.class,
        () -> parser.parseSession("session", "--vault", "a", "--extra"));
  }

  @Test
  void parsesOnlyVaultCommandsAndSessionControls() throws Exception {
    Path vault = Path.of("vault.pvlt");
    assertEquals(CliSessionCommand.help(), parser.parseSessionCommand(" help ", vault));
    assertEquals(CliSessionCommand.exit(), parser.parseSessionCommand("exit", vault));
    CliSessionCommand show =
        parser.parseSessionCommand("show --id 00112233445566778899aabbccddeeff", vault);
    assertEquals(CliSessionCommand.Kind.VAULT_OPERATION, show.kind());
    assertEquals(CliCommand.Type.SHOW, show.vaultCommand().type());
    assertEquals(vault, show.vaultCommand().vaultPath());

    assertThrows(CliArgumentException.class, () -> parser.parseSessionCommand("create", vault));
    assertThrows(CliArgumentException.class, () -> parser.parseSessionCommand("generate", vault));
    assertThrows(
        CliArgumentException.class,
        () -> parser.parseSessionCommand("list --vault other.pvlt", vault));
    assertThrows(CliArgumentException.class, () -> parser.parseSessionCommand(" ", vault));
  }
}
