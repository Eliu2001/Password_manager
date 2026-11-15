package cl.germain.vault.storage;

/** Indicates that the vault uses a format version this implementation cannot read. */
final class UnsupportedVaultFormatException extends IllegalArgumentException {
  UnsupportedVaultFormatException() {
    super("Vault format version is not supported");
  }
}
