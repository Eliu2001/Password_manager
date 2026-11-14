package cl.germain.vault.cli;

/** Executes a parsed command. */
@FunctionalInterface
public interface CliCommandHandler {
    CliExitStatus execute(CliCommand command) throws Exception;
}
