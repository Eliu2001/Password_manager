package cl.germain.vault.storage;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Ensures a file is accessible only to its owner/current user. */
final class PrivateFilePermissions {
  private static final Set<PosixFilePermission> OWNER_ONLY_POSIX =
      PosixFilePermissions.fromString("rw-------");
  private static final Set<AclEntryPermission> OWNER_ONLY_ACL =
      EnumSet.allOf(AclEntryPermission.class);

  private PrivateFilePermissions() {}

  /** Creates a file if absent and enforces private permissions before content is written. */
  static void ensurePrivateFile(Path path) throws IOException {
    Objects.requireNonNull(path, "path");
    if (Files.isSymbolicLink(path)) {
      throw new IOException("Private file path is invalid");
    }

    FileStore store = Files.getFileStore(path.getParent());
    boolean posix = store.supportsFileAttributeView(PosixFileAttributeView.class);
    boolean acl = store.supportsFileAttributeView(AclFileAttributeView.class);
    if (!posix && !acl) {
      throw new IOException("Private file permissions are unsupported");
    }

    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      try {
        if (posix) {
          FileAttribute<Set<PosixFilePermission>> permissions =
              PosixFilePermissions.asFileAttribute(OWNER_ONLY_POSIX);
          Files.createFile(path, permissions);
        } else {
          Files.createFile(path);
        }
      } catch (FileAlreadyExistsException ignored) {
        // A concurrent creator may have created the file; secure it below.
      }
    }

    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Private file path is invalid");
    }
    if (posix) {
      ensurePosixPrivate(path);
    } else {
      ensureAclPrivate(path);
    }
  }

  private static void ensurePosixPrivate(Path path) throws IOException {
    PosixFileAttributeView view =
        Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (view == null) {
      throw new IOException("POSIX file permissions are unavailable");
    }
    view.setPermissions(OWNER_ONLY_POSIX);
    if (!view.readAttributes().permissions().equals(OWNER_ONLY_POSIX)) {
      throw new IOException("Private file permissions could not be verified");
    }
  }

  private static void ensureAclPrivate(Path path) throws IOException {
    AclFileAttributeView view =
        Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (view == null) {
      throw new IOException("Windows file ACL is unavailable");
    }
    String userName = System.getProperty("user.name");
    if (userName == null || userName.isBlank()) {
      throw new IOException("Current Windows user could not be resolved");
    }
    UserPrincipal currentUser =
        path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(userName);
    AclEntry ownerEntry =
        AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(currentUser)
            .setPermissions(OWNER_ONLY_ACL)
            .build();
    view.setAcl(List.of(ownerEntry));

    List<AclEntry> actual = view.getAcl();
    if (actual.size() != 1
        || actual.get(0).type() != AclEntryType.ALLOW
        || !actual.get(0).principal().equals(currentUser)
        || !actual.get(0).permissions().containsAll(OWNER_ONLY_ACL)) {
      throw new IOException("Private file ACL could not be verified");
    }
  }
}
