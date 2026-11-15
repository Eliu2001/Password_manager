package cl.germain.vault.storage;

import cl.germain.vault.crypto.Argon2Parameters;
import cl.germain.vault.crypto.CryptoService;
import cl.germain.vault.crypto.DecryptionException;
import cl.germain.vault.crypto.DerivedKey;
import cl.germain.vault.crypto.EncryptedData;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HexFormat;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultHeaderTest {
    @Test void parsesV1AndV2HeadersAndKeepsVersionInExactAadPrefix() {
        byte[] salt = sequence(16);
        byte[] nonce = sequence(12);
        Argon2Parameters parameters = new Argon2Parameters(19_456, 2, 1);
        for (int version : new int[] {1, 2}) {
            byte[] prefix = VaultHeader.createAadPrefix(parameters, salt, version);
            byte[] headerBytes = VaultHeader.assemble(prefix, nonce, 16);
            VaultHeader parsed = VaultHeader.parse(headerBytes);
            assertEquals(version, parsed.version());
            assertArrayEquals(prefix, parsed.aad());
            assertEquals(VaultHeader.HEADER_LENGTH_BYTES, parsed.encoded().length);
            Arrays.fill(prefix, (byte) 0); Arrays.fill(headerBytes, (byte) 0);
        }
        Arrays.fill(salt, (byte) 0); Arrays.fill(nonce, (byte) 0);
    }

    @Test
    void assemblesTheExactFiftyByteV1Header() {
        byte[] salt = sequence(16);
        byte[] nonce = sequence(12);
        byte[] prefix = VaultHeader.createAadPrefix(
                new Argon2Parameters(19_456, 2, 1), salt);
        byte[] header = VaultHeader.assemble(prefix, nonce, 16);

        try {
            byte[] expected = HexFormat.of().parseHex(
                    "50564c54000100004c000000000200000001" +
                    "000102030405060708090a0b0c0d0e0f" +
                    "000102030405060708090a0b" +
                    "00000010");
            assertEquals(50, header.length);
            assertArrayEquals(expected, header);
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(prefix, (byte) 0);
            Arrays.fill(header, (byte) 0);
        }
    }

    @Test
    void parsesHeaderAndPreservesOriginalAadPrefix() {
        byte[] salt = sequence(16);
        byte[] nonce = sequence(12);
        byte[] prefix = VaultHeader.createAadPrefix(
                new Argon2Parameters(65_536, 3, 1), salt);
        byte[] headerBytes = VaultHeader.assemble(prefix, nonce, 42);
        VaultHeader header = VaultHeader.parse(headerBytes);

        try {
            assertEquals(new Argon2Parameters(65_536, 3, 1), header.parameters());
            assertEquals(42, header.ciphertextLength());
            assertArrayEquals(salt, header.salt());
            assertArrayEquals(nonce, header.nonce());
            assertArrayEquals(prefix, header.aad());
            assertArrayEquals(headerBytes, header.encoded());

            byte[] exposed = header.aad();
            Arrays.fill(exposed, (byte) 0);
            assertArrayEquals(prefix, header.aad());
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(prefix, (byte) 0);
            Arrays.fill(headerBytes, (byte) 0);
        }
    }

    @Test
    void rejectsOutOfRangeParametersUnsupportedVersionsAndInvalidLengths() {
        byte[] salt = sequence(16);
        byte[] nonce = sequence(12);
        byte[] validPrefix = VaultHeader.createAadPrefix(
                new Argon2Parameters(19_456, 2, 1), salt);
        byte[] validHeader = VaultHeader.assemble(validPrefix, nonce, 16);

        try {
            byte[] unsupported = validHeader.clone();
            unsupported[5] = 3;
            assertThrows(UnsupportedVaultFormatException.class,
                    () -> VaultHeader.parse(unsupported));

            byte[] invalidMemory = validHeader.clone();
            invalidMemory[8] = 0;
            invalidMemory[9] = 0;
            invalidMemory[10] = 0;
            invalidMemory[11] = 1;
            assertThrows(IllegalArgumentException.class, () -> VaultHeader.parse(invalidMemory));

            byte[] tooShortCiphertext = validHeader.clone();
            tooShortCiphertext[49] = 15;
            assertThrows(IllegalArgumentException.class,
                    () -> VaultHeader.parse(tooShortCiphertext));

            assertThrows(IllegalArgumentException.class,
                    () -> VaultHeader.parse(Arrays.copyOf(validHeader, 49)));

            byte[] invalidMagic = validHeader.clone();
            invalidMagic[0] ^= 1;
            assertThrows(IllegalArgumentException.class,
                    () -> VaultHeader.parse(invalidMagic));
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(validPrefix, (byte) 0);
            Arrays.fill(validHeader, (byte) 0);
        }
    }

    @Test
    void validatesAllKdfParameterBoundsBeforeAnyKdfUse() {
        assertEquals(new Argon2Parameters(19_456, 2, 1),
                VaultHeader.parse(rawHeader(19_456, 2, 1)).parameters());
        assertEquals(new Argon2Parameters(262_144, 10, 4),
                VaultHeader.parse(rawHeader(262_144, 10, 4)).parameters());

        int[][] invalidParameters = {
                {19_455, 2, 1}, {262_145, 2, 1},
                {19_456, 1, 1}, {19_456, 11, 1},
                {19_456, 2, 0}, {19_456, 2, 5}
        };
        for (int[] parameters : invalidParameters) {
            assertThrows(IllegalArgumentException.class,
                    () -> VaultHeader.parse(rawHeader(parameters[0], parameters[1], parameters[2])));
        }
    }

    @Test
    void changingAnyHeaderBytePreventsOpening() throws Exception {
        CryptoService crypto = new CryptoService();
        Argon2Parameters parameters = new Argon2Parameters(19_456, 2, 1);
        char[] masterPassword = {'t', 'e', 's', 't'};
        byte[] salt = sequence(16);
        byte[] plaintext = {1, 2, 3, 4};
        byte[] prefix = VaultHeader.createAadPrefix(parameters, salt);
        byte[] headerBytes = null;
        byte[] ciphertext = null;
        byte[] nonce = null;

        try (DerivedKey key = crypto.deriveKey(masterPassword, salt, parameters)) {
            EncryptedData encrypted = crypto.encrypt(key, plaintext, prefix);
            ciphertext = encrypted.ciphertextAndTag();
            nonce = encrypted.nonce();
            headerBytes = VaultHeader.assemble(prefix, nonce, ciphertext.length);
            long completeFileLength = (long) headerBytes.length + ciphertext.length;

            for (int index = 0; index < headerBytes.length; index++) {
                byte[] altered = headerBytes.clone();
                altered[index] ^= 1;
                boolean rejected = false;
                try {
                    VaultHeader parsed = VaultHeader.parse(altered);
                    parsed.validateFileLength(completeFileLength);
                    byte[] alteredNonce = parsed.nonce();
                    byte[] aad = parsed.aad();
                    try {
                        byte[] recovered = crypto.decrypt(key,
                                new EncryptedData(alteredNonce, ciphertext), aad);
                        Arrays.fill(recovered, (byte) 0);
                    } finally {
                        Arrays.fill(alteredNonce, (byte) 0);
                        Arrays.fill(aad, (byte) 0);
                    }
                } catch (IllegalArgumentException | DecryptionException exception) {
                    rejected = true;
                } finally {
                    Arrays.fill(altered, (byte) 0);
                }
                assertTrue(rejected, "Header byte alteration was accepted at offset " + index);
            }
        } finally {
            Arrays.fill(masterPassword, '\0');
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(plaintext, (byte) 0);
            Arrays.fill(prefix, (byte) 0);
            if (headerBytes != null) {
                Arrays.fill(headerBytes, (byte) 0);
            }
            if (ciphertext != null) {
                Arrays.fill(ciphertext, (byte) 0);
            }
            if (nonce != null) {
                Arrays.fill(nonce, (byte) 0);
            }
        }
    }

    private static byte[] sequence(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    private static byte[] rawHeader(int memoryKiB, int iterations, int parallelism) {
        return ByteBuffer.allocate(VaultHeader.HEADER_LENGTH_BYTES)
                .put("PVLT".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .putShort((short) 1)
                .putInt(memoryKiB)
                .putInt(iterations)
                .putInt(parallelism)
                .put(new byte[16])
                .put(new byte[12])
                .putInt(16)
                .array();
    }
}
