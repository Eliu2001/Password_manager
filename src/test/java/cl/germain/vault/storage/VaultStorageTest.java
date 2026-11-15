package cl.germain.vault.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class VaultStorageTest {
    @TempDir Path directory;

    @Test void createAndOpenEmptyVault() throws Exception {
        Path file = directory.resolve("vault.dat");
        char[] master = "synthetic test master".toCharArray();
        VaultStorage storage = new VaultStorage();
        try (VaultSession created = storage.create(file, master)) {
            assertEquals(0, created.vault().list().size());
        }
        try (VaultSession opened = storage.open(file, master)) {
            assertEquals(0, opened.vault().list().size());
        }
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> storage.create(file, master));
        java.util.Arrays.fill(master, '\0');
    }

    @Test void legacyV1FixtureOpensSavesAndReopensWithoutChangingVersionOrSchema() throws Exception {
        Path file = directory.resolve("legacy-v1.dat");
        char[] master = "synthetic v1 master".toCharArray();
        writeV1Fixture(file, master);
        byte[] before = Files.readAllBytes(file);
        byte[] id;
        try (VaultSession session = new VaultStorage().open(file, master)) {
            assertEquals(1, session.version());
            assertEquals(1, session.vault().list().size());
            id = session.vault().list().get(0).id();
            session.vault().add("new service", "new user", "new synthetic password".toCharArray(), "");
            session.save();
            assertEquals(1, session.version());
        }
        byte[] after = Files.readAllBytes(file);
        assertEquals(1, ByteBuffer.wrap(after).order(ByteOrder.BIG_ENDIAN).getShort(4));
        assertFalse(java.util.Arrays.equals(before, after));
        try (VaultSession session = new VaultStorage().open(file, master)) {
            assertEquals(1, session.version());
            assertEquals(2, session.vault().list().size());
            assertArrayEquals(id, session.vault().list().get(0).id());
        }
        java.util.Arrays.fill(master, '\0');
        VaultStorage.clear(before, after, id);
    }

    @Test void explicitUpgradeConvertsV1AtomicallyAndLeavesSessionConsistentOnFailure() throws Exception {
        Path file = directory.resolve("upgrade-v1.dat");
        char[] master = "synthetic upgrade master".toCharArray();
        writeV1Fixture(file, master);
        byte[] original = Files.readAllBytes(file);
        VaultStorage failing = new VaultStorage(new cl.germain.vault.crypto.CryptoService(),
                temp -> { throw new java.nio.file.AtomicMoveNotSupportedException(
                        temp.toString(), "vault", "synthetic unsupported move"); });
        try (VaultSession session = failing.open(file, master)) {
            assertThrows(IOException.class, session::upgradeToV2);
            assertEquals(1, session.version());
            assertArrayEquals(original, Files.readAllBytes(file));
            assertEquals(1, session.vault().list().size());
            try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
        }
        try (VaultSession session = new VaultStorage().open(file, master)) {
            session.upgradeToV2();
            assertEquals(2, session.version());
        }
        byte[] upgraded = Files.readAllBytes(file);
        assertEquals(2, ByteBuffer.wrap(upgraded).order(ByteOrder.BIG_ENDIAN).getShort(4));
        try (VaultSession session = new VaultStorage().open(file, master)) {
            assertEquals(2, session.version());
            assertEquals(1, session.vault().list().size());
            assertThrows(IllegalStateException.class, session::upgradeToV2);
        }
        java.util.Arrays.fill(master, '\0');
        VaultStorage.clear(original, upgraded);
    }

    @Test void externalChangePreventsUpgradeAndPreservesExternallyWrittenBytes() throws Exception {
        Path file = directory.resolve("upgrade-external.dat");
        char[] master = "synthetic external upgrade".toCharArray();
        writeV1Fixture(file, master);
        try (VaultSession session = new VaultStorage().open(file, master)) {
            byte[] external = Files.readAllBytes(file);
            external[external.length - 1] ^= 1;
            Files.write(file, external);
            assertThrows(IOException.class, session::upgradeToV2);
            assertEquals(1, session.version());
            assertArrayEquals(external, Files.readAllBytes(file));
            VaultStorage.clear(external);
        }
        java.util.Arrays.fill(master, '\0');
    }

    @Test void upgradeRejectsV2PayloadOverMaximumWithoutReplacingV1() throws Exception {
        Path file = directory.resolve("upgrade-too-large.dat");
        char[] master = "synthetic maximum upgrade".toCharArray();
        writeValidMaximumFile(file, master);
        byte[] original = Files.readAllBytes(file);
        try (VaultSession session = new VaultStorage().open(file, master)) {
            assertThrows(IllegalArgumentException.class, session::upgradeToV2);
            assertEquals(1, session.version());
            assertArrayEquals(original, Files.readAllBytes(file));
            assertEquals(1, session.vault().list().size());
        }
        java.util.Arrays.fill(master, '\0');
        VaultStorage.clear(original);
    }

    @Test void v2TotpSecretSurvivesReopenAndNeverAppearsInVaultFile() throws Exception {
        Path file = directory.resolve("totp-v2.dat");
        char[] master = "synthetic v2 master".toCharArray();
        byte[] secret = new byte[20];
        java.util.Arrays.fill(secret, (byte) 0x5a);
        byte[] id;
        try (VaultSession session = new VaultStorage().create(file, master)) {
            assertEquals(2, session.version());
            char[] entryPassword = "synthetic password".toCharArray();
            id = session.vault().add("service", "user", entryPassword, "");
            java.util.Arrays.fill(entryPassword, '\0');
            session.vault().setTotpSecret(id, secret);
            session.save();
        }
        byte[] fileBytes = Files.readAllBytes(file);
        assertFalse(contains(fileBytes, secret));
        try (VaultSession session = new VaultStorage().open(file, master)) {
            byte[] loaded = session.vault().totpSecret(id).orElseThrow();
            assertArrayEquals(secret, loaded);
            VaultStorage.clear(loaded);
        }
        java.util.Arrays.fill(master, '\0');
        VaultStorage.clear(secret, fileBytes, id);
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer: for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (haystack[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }

    private static void writeV1Fixture(Path file, char[] master) throws Exception {
        cl.germain.vault.crypto.CryptoService crypto = new cl.germain.vault.crypto.CryptoService();
        cl.germain.vault.crypto.Argon2Parameters params = new cl.germain.vault.crypto.Argon2Parameters(19_456, 2, 1);
        byte[] salt = crypto.generateSalt();
        byte[] prefix = VaultHeader.createAadPrefix(params, salt, 1);
        cl.germain.vault.model.Vault vault = new cl.germain.vault.model.Vault();
        byte[] payload = null, nonce = null, cipher = null, header = null, bytes = null;
        try {
            char[] entryPassword = "synthetic fixture password".toCharArray();
            try { vault.add("fixture service", "fixture user", entryPassword, "fixture notes"); }
            finally { java.util.Arrays.fill(entryPassword, '\0'); }
            payload = new VaultPayloadCodec().encode(vault, 1);
            try (var key = crypto.deriveKey(master, salt, params)) {
                var encrypted = crypto.encrypt(key, payload, prefix);
                nonce = encrypted.nonce(); cipher = encrypted.ciphertextAndTag();
            }
            header = VaultHeader.assemble(prefix, nonce, cipher.length);
            bytes = VaultStorage.join(header, cipher);
            Files.write(file, bytes);
            PrivateFilePermissions.ensurePrivateFile(file);
        } finally {
            vault.close();
            VaultStorage.clear(salt, prefix, payload, nonce, cipher, header, bytes);
        }
    }

    @Test void vaultLockAndSaveTemporaryArePrivateOnPosix() throws Exception {
        Path file = directory.resolve("posix-permissions.dat");
        char[] master = "synthetic test master".toCharArray();
        VaultStorage storage = new VaultStorage(new cl.germain.vault.crypto.CryptoService(),
                temp -> assertPosixPrivateIfAvailable(temp));
        try (VaultSession session = storage.create(file, master)) {
            assertPosixPrivateIfAvailable(file);
            assertPosixPrivateIfAvailable(directory.resolve("posix-permissions.dat.lock"));
            session.vault().add("service", "user", "secret".toCharArray(), "");
            session.save();
            assertPosixPrivateIfAvailable(file);
        }
        java.util.Arrays.fill(master, '\0');
    }

    @Test void explicitSavePersistsChangesAndCloseDoesNotSave() throws Exception {
        Path file = directory.resolve("saved.dat");
        char[] master = "synthetic test master".toCharArray();
        byte[] id;
        try (VaultSession session = new VaultStorage().create(file, master)) {
            id = session.vault().add("example", "user", "synthetic-secret".toCharArray(), "note");
            session.save();
        }
        try (VaultSession session = new VaultStorage().open(file, master)) {
            assertEquals(1, session.vault().list().size());
            assertArrayEquals(id, session.vault().list().get(0).id());
            session.vault().add("discarded", "user", "synthetic".toCharArray(), "");
        }
        try (VaultSession session = new VaultStorage().open(file, master)) {
            assertEquals(1, session.vault().list().size());
        }
        java.util.Arrays.fill(master, '\0');
    }

    @Test void wrongPasswordAndTamperingDoNotReturnVault() throws Exception {
        Path file = directory.resolve("tampered.dat");
        char[] master = "synthetic test master".toCharArray();
        try (VaultSession ignored = new VaultStorage().create(file, master)) { }
        byte[] original = java.nio.file.Files.readAllBytes(file);
        char[] wrong = "different synthetic password".toCharArray();
        assertThrows(cl.germain.vault.crypto.DecryptionException.class,
                () -> new VaultStorage().open(file, wrong));
        original[VaultHeader.HEADER_LENGTH_BYTES] ^= 1;
        java.nio.file.Files.write(file, original);
        assertThrows(cl.germain.vault.crypto.DecryptionException.class,
                () -> new VaultStorage().open(file, master));
        assertArrayEquals(original, java.nio.file.Files.readAllBytes(file));
        java.util.Arrays.fill(master, '\0'); java.util.Arrays.fill(wrong, '\0');
        java.util.Arrays.fill(original, (byte) 0);
    }

    @Test void externalModificationPreventsSaveAndLeavesSessionOpen() throws Exception {
        Path file = directory.resolve("external.dat");
        char[] master = "synthetic test master".toCharArray();
        try (VaultSession ignored = new VaultStorage().create(file, master)) { }
        try (VaultSession session = new VaultStorage().open(file, master)) {
            session.vault().add("service", "user", "secret".toCharArray(), "");
            byte[] external = java.nio.file.Files.readAllBytes(file);
            external[external.length - 1] ^= 1;
            java.nio.file.Files.write(file, external);
            assertThrows(java.io.IOException.class, session::save);
            assertEquals(1, session.vault().list().size());
            assertArrayEquals(external, java.nio.file.Files.readAllBytes(file));
            java.util.Arrays.fill(external, (byte) 0);
        }
        java.util.Arrays.fill(master, '\0');
    }

    @Test void failedReplacementKeepsOldFileAndCanBeRetried() throws Exception {
        Path file = directory.resolve("retry.dat");
        char[] master = "synthetic test master".toCharArray();
        AtomicBoolean failOnce = new AtomicBoolean(true);
        VaultStorage storage = new VaultStorage(new cl.germain.vault.crypto.CryptoService(), temp -> {
            byte[] bytes = java.nio.file.Files.readAllBytes(temp);
            try {
                assertTrue(bytes.length >= VaultHeader.HEADER_LENGTH_BYTES + VaultHeader.MIN_CIPHERTEXT_LENGTH_BYTES);
                VaultHeader header = VaultHeader.parse(java.util.Arrays.copyOf(bytes, VaultHeader.HEADER_LENGTH_BYTES));
                header.validateFileLength(bytes.length);
            } finally { java.util.Arrays.fill(bytes, (byte) 0); }
            if (failOnce.getAndSet(false)) throw new IOException("synthetic pre-move failure");
        });
        byte[] original;
        try (VaultSession session = storage.create(file, master)) {
            original = java.nio.file.Files.readAllBytes(file);
            session.vault().add("service", "user", "secret".toCharArray(), "");
            assertThrows(IOException.class, session::save);
            assertArrayEquals(original, java.nio.file.Files.readAllBytes(file));
            assertEquals(1, session.vault().list().size());
            try (var files = java.nio.file.Files.list(directory)) {
                assertEquals(2, files.count()); // vault and persistent lock only; failed temp removed
            }
            session.save();
        }
        java.util.Arrays.fill(original, (byte) 0);
        try (VaultSession reopened = storage.open(file, master)) { assertEquals(1, reopened.vault().list().size()); }
        java.util.Arrays.fill(master, '\0');
    }

    @Test void unsupportedAtomicMoveFailsClosedWithoutFallback() throws Exception {
        Path file = directory.resolve("atomic-unsupported.dat");
        char[] master = "synthetic test master".toCharArray();
        VaultStorage storage = new VaultStorage(new cl.germain.vault.crypto.CryptoService(), temp -> {
            throw new java.nio.file.AtomicMoveNotSupportedException(temp.toString(), "target", "test");
        });
        try (VaultSession session = storage.create(file, master)) {
            byte[] before = Files.readAllBytes(file);
            session.vault().add("service", "user", "secret".toCharArray(), "");
            assertThrows(IOException.class, session::save);
            assertArrayEquals(before, Files.readAllBytes(file));
            try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
            assertEquals(1, session.vault().list().size());
            java.util.Arrays.fill(before, (byte) 0);
        }
        java.util.Arrays.fill(master, '\0');
    }

    @Test void acceptsExactMaximumAndRejectsMaximumPlusOneBeforeKdf() throws Exception {
        Path exact = directory.resolve("exact-size.dat");
        char[] master = "synthetic boundary password".toCharArray();
        writeValidMaximumFile(exact, master);
        assertEquals(16_777_216L, Files.size(exact));
        try (VaultSession session = new VaultStorage().open(exact, master)) {
            assertEquals(1, session.vault().list().size());
        }

        Path oversized = directory.resolve("oversized.dat");
        try (var channel = java.nio.channels.FileChannel.open(oversized,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.position(16_777_216L);
            channel.write(ByteBuffer.wrap(new byte[] { 0 }));
        }
        AtomicBoolean derived = new AtomicBoolean();
        assertThrows(IOException.class, () -> new VaultStorage(new cl.germain.vault.crypto.CryptoService(),
                path -> { }, (p, s, params) -> {
                derived.set(true);
                throw new AssertionError("KDF must not run for oversized file");
        }).open(oversized, master));
        assertFalse(derived.get());
        java.util.Arrays.fill(master, '\0');
    }

    @Test void malformedOuterFilesAreRejectedWithoutChangingBytes() throws Exception {
        Path file = directory.resolve("invalids.dat");
        char[] master = "synthetic format password".toCharArray();
        try (VaultSession ignored = new VaultStorage().create(file, master)) { }
        byte[] valid = Files.readAllBytes(file);
        for (int mode = 0; mode < 5; mode++) {
            byte[] malformed = valid.clone();
            switch (mode) {
                case 0 -> malformed[0] ^= 1; // wrong magic
                case 1 -> malformed[5] = 3; // unsupported version
                case 2 -> malformed = java.util.Arrays.copyOf(malformed, malformed.length - 1);
                case 3 -> malformed = java.util.Arrays.copyOf(malformed, malformed.length + 1);
                case 4 -> ByteBuffer.wrap(malformed).order(ByteOrder.BIG_ENDIAN)
                        .putInt(46, VaultHeader.MIN_CIPHERTEXT_LENGTH_BYTES - 1);
                default -> throw new AssertionError();
            }
            Files.write(file, malformed);
            assertThrows(RuntimeException.class, () -> new VaultStorage().open(file, master));
            assertArrayEquals(malformed, Files.readAllBytes(file));
            java.util.Arrays.fill(malformed, (byte) 0);
        }
        java.util.Arrays.fill(valid, (byte) 0);
        java.util.Arrays.fill(master, '\0');
    }

    private static void writeValidMaximumFile(Path path, char[] master) throws Exception {
        var crypto = new cl.germain.vault.crypto.CryptoService();
        var parameters = new cl.germain.vault.crypto.Argon2Parameters(19_456, 2, 1);
        byte[] salt = crypto.generateSalt();
        byte[] aad = VaultHeader.createAadPrefix(parameters, salt);
        byte[] service = "a".repeat(16_777_096).getBytes(StandardCharsets.UTF_8);
        byte[] payload = ByteBuffer.allocate(16_777_150).order(ByteOrder.BIG_ENDIAN)
                .putInt(1).put(new byte[16]).putLong(0).putLong(0)
                .putInt(service.length).put(service).putInt(1).put((byte) 'u')
                .putInt(1).put((byte) 'p').putInt(0).array();
        byte[] nonce = null, ciphertext = null, header = null, file = null;
        try (var key = crypto.deriveKey(master, salt, parameters)) {
            var encrypted = crypto.encrypt(key, payload, aad);
            nonce = encrypted.nonce(); ciphertext = encrypted.ciphertextAndTag();
            header = VaultHeader.assemble(aad, nonce, ciphertext.length);
            file = VaultStorage.join(header, ciphertext);
            assertEquals(16_777_216, file.length);
            Files.write(path, file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            PrivateFilePermissions.ensurePrivateFile(path);
        } finally {
            VaultStorage.clear(salt, aad, service, payload, nonce, ciphertext, header, file);
        }
    }

    private static void assertPosixPrivateIfAvailable(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class)) {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(path));
        } else if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux")) {
            fail("Linux test filesystem must expose POSIX permissions");
        }
    }
}
