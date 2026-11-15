package cl.germain.vault.cli;

import cl.germain.vault.generator.PasswordGenerator;

/** Java entry point for the interactive password-vault CLI. */
public final class Main {
    private Main() { }

    public static void main(String[] arguments) {
        int status = run(arguments, new SystemCliConsole());
        if (status != CliExitStatus.SUCCESS.code()) {
            System.exit(status);
        }
    }

    static int run(String[] arguments, CliConsole console) {
        return new CliApplication(new CliArgumentParser(), console,
                new VaultCliCommands(console, new cl.germain.vault.storage.VaultStorage(),
                        new PasswordGenerator())).run(arguments);
    }
}
