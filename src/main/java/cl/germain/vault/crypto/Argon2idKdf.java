package cl.germain.vault.crypto;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;

import java.util.Arrays;
import java.util.Objects;

/** Package-private Argon2id key derivation primitive. */
final class Argon2idKdf {
    static final int KEY_LENGTH_BYTES = 32;
    private static final int ARGON2_VERSION_13 = 0x13;

    private Argon2idKdf() {}

    static byte[] derive(char[] password, byte[] salt, Argon2Parameters parameters) {
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(salt, "salt");
        Objects.requireNonNull(parameters, "parameters");
        if (password.length == 0) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        if (salt.length != 16) {
            throw new IllegalArgumentException("Salt must be 16 bytes");
        }

        byte[] key = new byte[KEY_LENGTH_BYTES];
        try {
            return PasswordEncoding.withUtf8Password(password, passwordBytes -> {
                org.bouncycastle.crypto.params.Argon2Parameters bcParameters =
                        new org.bouncycastle.crypto.params.Argon2Parameters.Builder(
                                org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_id)
                                .withVersion(ARGON2_VERSION_13)
                                .withMemoryAsKB(parameters.memoryKiB())
                                .withIterations(parameters.iterations())
                                .withParallelism(parameters.parallelism())
                                .withSalt(salt)
                                .build();
                Argon2BytesGenerator generator = new Argon2BytesGenerator();
                generator.init(bcParameters);
                generator.generateBytes(passwordBytes, key);
                return key;
            });
        } catch (RuntimeException | Error e) {
            Arrays.fill(key, (byte) 0);
            throw e;
        }
    }
}
