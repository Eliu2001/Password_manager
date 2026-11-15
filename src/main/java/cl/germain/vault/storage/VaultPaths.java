package cl.germain.vault.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Resolves the canonical vault and stable sibling lock paths without following vault links. */
record VaultPaths(Path vault, Path lock) {
  static VaultPaths resolve(Path requestedPath) throws IOException {
    Objects.requireNonNull(requestedPath, "path");
    Path absolute = requestedPath.toAbsolutePath().normalize();
    Path requestedName = absolute.getFileName();
    Path requestedParent = absolute.getParent();
    if (requestedName == null || requestedParent == null) {
      throw invalidPath();
    }

    Path realParent = requestedParent.toRealPath();
    if (!Files.isDirectory(realParent, LinkOption.NOFOLLOW_LINKS)) {
      throw invalidPath();
    }
    Path candidate = realParent.resolve(requestedName);
    if (Files.isSymbolicLink(candidate)) {
      throw invalidPath();
    }

    Path canonicalVault;
    if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
      if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
        throw invalidPath();
      }
      canonicalVault = candidate.toRealPath();
    } else {
      canonicalVault = candidate;
    }

    Path fileName = canonicalVault.getFileName();
    if (fileName == null) {
      throw invalidPath();
    }
    Path lockPath = canonicalVault.resolveSibling(fileName + ".lock");
    if (Files.isSymbolicLink(lockPath)) {
      throw invalidPath();
    }
    return new VaultPaths(canonicalVault, lockPath);
  }

  private static IOException invalidPath() {
    return new IOException("Vault path is invalid");
  }
}
