package cl.germain.vault.cli;

import java.util.Objects;

/** A parsed line entered inside an interactive vault session. */
public record CliSessionCommand(Kind kind, CliCommand vaultCommand) {
  public enum Kind {
    HELP,
    EXIT,
    VAULT_OPERATION
  }

  public CliSessionCommand {
    Objects.requireNonNull(kind, "kind");
    if ((kind == Kind.VAULT_OPERATION) != (vaultCommand != null)) {
      throw new IllegalArgumentException("Session command payload does not match its kind");
    }
  }

  public static CliSessionCommand help() {
    return new CliSessionCommand(Kind.HELP, null);
  }

  public static CliSessionCommand exit() {
    return new CliSessionCommand(Kind.EXIT, null);
  }

  public static CliSessionCommand operation(CliCommand command) {
    return new CliSessionCommand(Kind.VAULT_OPERATION, Objects.requireNonNull(command));
  }
}
