package cl.germain.vault.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultLockTest {
  @TempDir Path directory;

  @Test
  void exclusiveLockBlocksSecondAcquisitionAndIsReleasedOnClose() throws IOException {
    VaultPaths paths = VaultPaths.resolve(directory.resolve("vault.dat"));
    VaultLock first = VaultLock.acquire(paths.lock());
    try {
      assertThrows(IOException.class, () -> VaultLock.acquire(paths.lock()));
      assertEquals(0, Files.size(paths.lock()));
      assertPrivateLockFile(paths.lock());
    } finally {
      first.close();
    }

    try (VaultLock second = VaultLock.acquire(paths.lock())) {
      assertEquals(0, Files.size(paths.lock()));
    }
  }

  @Test
  void closeIsIdempotent() throws IOException {
    VaultPaths paths = VaultPaths.resolve(directory.resolve("vault.dat"));
    VaultLock lock = VaultLock.acquire(paths.lock());
    lock.close();
    lock.close();
  }

  @Test
  void canonicalPathAliasesShareTheSameExclusiveLock() throws IOException {
    Path firstPath = directory.resolve("vault.dat");
    Path aliasPath = directory.resolve("child").resolve("..").resolve("vault.dat");
    VaultPaths first = VaultPaths.resolve(firstPath);
    VaultPaths alias = VaultPaths.resolve(aliasPath);

    try (VaultLock ignored = VaultLock.acquire(first.lock())) {
      assertThrows(IOException.class, () -> VaultLock.acquire(alias.lock()));
    }
  }

  private static void assertPrivateLockFile(Path lockPath) throws IOException {
    FileStore store = Files.getFileStore(lockPath.getParent());
    if (store.supportsFileAttributeView(PosixFileAttributeView.class)) {
      assertEquals(
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          Files.getPosixFilePermissions(lockPath));
    } else if (store.supportsFileAttributeView(AclFileAttributeView.class)) {
      AclFileAttributeView view = Files.getFileAttributeView(lockPath, AclFileAttributeView.class);
      assertEquals(1, view.getAcl().size());
      assertEquals(Files.getOwner(lockPath), view.getAcl().get(0).principal());
    } else {
      throw new AssertionError("Filesystem exposes neither POSIX permissions nor ACLs");
    }
  }
}
