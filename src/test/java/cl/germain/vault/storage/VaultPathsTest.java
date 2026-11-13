package cl.germain.vault.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VaultPathsTest {
    @TempDir
    Path directory;

    @Test
    void relativeAndNormalizedPathsResolveToOneStableLock() throws IOException {
        Path first = directory.resolve("vault.dat");
        Path second = directory.resolve("child").resolve("..").resolve("vault.dat");

        VaultPaths firstPaths = VaultPaths.resolve(first);
        VaultPaths secondPaths = VaultPaths.resolve(second);

        assertEquals(firstPaths.vault(), secondPaths.vault());
        assertEquals(firstPaths.lock(), secondPaths.lock());
        assertEquals(directory.toRealPath().resolve("vault.dat"), firstPaths.vault());
        assertEquals(directory.toRealPath().resolve("vault.dat.lock"), firstPaths.lock());
    }

    @Test
    void existingVaultUsesItsRealPathAndMustBeARegularFile() throws IOException {
        Path vaultFile = Files.writeString(directory.resolve("vault.dat"), "ciphertext");
        assertEquals(vaultFile.toRealPath(), VaultPaths.resolve(vaultFile).vault());

        Path directoryPath = Files.createDirectory(directory.resolve("not-a-file"));
        assertThrows(IOException.class, () -> VaultPaths.resolve(directoryPath));
    }

    @Test
    void missingParentIsNotCreated() {
        Path missingParent = directory.resolve("missing").resolve("vault.dat");
        assertThrows(IOException.class, () -> VaultPaths.resolve(missingParent));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(missingParent.getParent()));
    }

    @Test
    void rejectsVaultAndLockSymbolicLinksWhenPlatformAllowsCreation() throws IOException {
        Path target = directory.resolve("does-not-exist.vault");
        Path vaultLink = directory.resolve("vault-link");
        createSymlinkOrSkip(vaultLink, target);
        assertThrows(IOException.class, () -> VaultPaths.resolve(vaultLink));

        Path realVault = directory.resolve("real-vault");
        Path lockLink = directory.resolve("real-vault.lock");
        createSymlinkOrSkip(lockLink, directory.resolve("missing-lock-target"));
        assertThrows(IOException.class, () -> VaultPaths.resolve(realVault));
    }

    private static void createSymlinkOrSkip(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | SecurityException | IOException exception) {
            assumeTrue(false, "Symbolic links are unavailable: " + exception.getClass().getSimpleName());
        }
    }
}
