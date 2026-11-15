package cl.germain.vault.cli;

import java.nio.file.Path;
import java.util.Objects;

/** Validated options for an interactive vault session. */
public record CliSessionRequest(Path vaultPath, int timeoutMinutes) {
  public static final int DEFAULT_TIMEOUT_MINUTES = 5;
  public static final int MIN_TIMEOUT_MINUTES = 1;
  public static final int MAX_TIMEOUT_MINUTES = 60;

  public CliSessionRequest {
    Objects.requireNonNull(vaultPath, "vaultPath");
    if (timeoutMinutes < MIN_TIMEOUT_MINUTES || timeoutMinutes > MAX_TIMEOUT_MINUTES) {
      throw new IllegalArgumentException("Session timeout is outside the supported range");
    }
  }
}
