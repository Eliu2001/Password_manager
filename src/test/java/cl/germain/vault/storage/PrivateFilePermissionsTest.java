package cl.germain.vault.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivateFilePermissionsTest {
    @TempDir
    Path directory;

    @Test
    void createsPrivateFileAndVerifiesPlatformPermissions() throws IOException {
        Path file = directory.resolve("private-file");
        PrivateFilePermissions.ensurePrivateFile(file);
        assertTrue(Files.isRegularFile(file));

        FileStore store = Files.getFileStore(directory);
        if (store.supportsFileAttributeView(PosixFileAttributeView.class)) {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    permissions);
        } else if (store.supportsFileAttributeView(AclFileAttributeView.class)) {
            AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
            UserPrincipal owner = Files.getOwner(file);
            assertEquals(1, view.getAcl().size());
            assertEquals(AclEntryType.ALLOW, view.getAcl().get(0).type());
            assertEquals(owner, view.getAcl().get(0).principal());
        } else {
            throw new AssertionError("Test filesystem must expose POSIX permissions or ACLs");
        }
    }

    @Test
    void tightensPermissionsOfExistingFile() throws IOException {
        Path file = Files.writeString(directory.resolve("existing-file"), "");
        PrivateFilePermissions.ensurePrivateFile(file);

        FileStore store = Files.getFileStore(directory);
        if (store.supportsFileAttributeView(PosixFileAttributeView.class)) {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(file));
        } else if (store.supportsFileAttributeView(AclFileAttributeView.class)) {
            AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
            assertEquals(1, view.getAcl().size());
            assertEquals(Files.getOwner(file), view.getAcl().get(0).principal());
        }
    }
}
