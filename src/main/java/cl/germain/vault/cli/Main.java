package cl.germain.vault.cli;

import cl.germain.vault.generator.PasswordGenerator;

/** Java entry point for the interactive password-vault CLI. */
public final class Main {
  private Main() {}

  public static void main(String[] arguments) {
    int status = run(arguments, new SystemCliConsole());
    if (status != CliExitStatus.SUCCESS.code()) {
      System.exit(status);
    }
  }

  static int run(String[] arguments, CliConsole console) {
    CliArgumentParser parser = new CliArgumentParser();
    cl.germain.vault.storage.VaultStorage storage = new cl.germain.vault.storage.VaultStorage();
    VaultCliCommands commands = new VaultCliCommands(console, storage, new PasswordGenerator());
    return new CliApplication(
            parser,
            console,
            commands,
            new CliSessionRunner(console, storage, commands, parser, MonotonicTicker.system()))
        .run(arguments);
  }
}
