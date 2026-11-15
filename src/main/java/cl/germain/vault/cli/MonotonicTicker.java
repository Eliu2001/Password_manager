package cl.germain.vault.cli;

/** Supplies monotonic nanosecond ticks for timeout measurement. */
@FunctionalInterface
public interface MonotonicTicker {
  long read();

  static MonotonicTicker system() {
    return System::nanoTime;
  }
}
