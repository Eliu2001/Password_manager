package cl.germain.vault.storage;

import cl.germain.vault.crypto.Argon2Parameters;
import cl.germain.vault.crypto.DerivedKey;

@FunctionalInterface
interface VaultKeyDeriver {
    DerivedKey derive(char[] password, byte[] salt, Argon2Parameters parameters);
}
