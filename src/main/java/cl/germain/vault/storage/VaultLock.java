package cl.germain.vault.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Holds an exclusive operating-system lock on the stable sidecar file. */
final class VaultLock implements AutoCloseable {
  private final FileChannel channel;
  private final FileLock lock;
  private boolean closed;

  private VaultLock(FileChannel channel, FileLock lock) {
    this.channel = channel;
    this.lock = lock;
  }

  static VaultLock acquire(Path lockPath) throws IOException {
    Objects.requireNonNull(lockPath, "lockPath");
    if (java.nio.file.Files.isSymbolicLink(lockPath)) {
      throw new IOException("Vault lock path is invalid");
    }

    PrivateFilePermissions.ensurePrivateFile(lockPath);
    FileChannel channel =
        FileChannel.open(
            lockPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS);
    try {
      FileLock acquired = channel.tryLock();
      if (acquired == null) {
        throw new IOException("Vault is already locked");
      }
      return new VaultLock(channel, acquired);
    } catch (OverlappingFileLockException exception) {
      closeQuietly(channel);
      throw new IOException("Vault is already locked");
    } catch (IOException | RuntimeException exception) {
      closeQuietly(channel);
      throw exception;
    }
  }

  @Override
  public synchronized void close() throws IOException {
    if (!closed) {
      closed = true;
      try {
        lock.release();
      } finally {
        channel.close();
      }
    }
  }

  private static void closeQuietly(FileChannel channel) {
    try {
      channel.close();
    } catch (IOException ignored) {
      // Preserve the acquisition failure.
    }
  }
}
