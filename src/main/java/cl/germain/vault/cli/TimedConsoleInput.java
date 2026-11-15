package cl.germain.vault.cli;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Serializes visible console reads and supports timeout without abandoning a pending read. */
final class TimedConsoleInput implements AutoCloseable {
  @FunctionalInterface
  interface LineReader {
    String readLine(String prompt) throws IOException;
  }

  private final LineReader reader;
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "vault-cli-console-reader");
        thread.setDaemon(true);
        return thread;
      });
  private Future<String> pendingLine;
  private boolean closed;

  TimedConsoleInput(LineReader reader) {
    this.reader = reader;
  }

  synchronized TimedLineResult readLineUntil(String prompt, long timeoutNanos)
      throws IOException, InterruptedException {
    if (closed) throw new IOException("Console input is closed");
    if (timeoutNanos < 0) throw new IllegalArgumentException("Timeout must not be negative");
    ensureNoPendingLine();
    pendingLine = executor.submit(() -> reader.readLine(prompt));
    try {
      String line = pendingLine.get(timeoutNanos, TimeUnit.NANOSECONDS);
      pendingLine = null;
      return line == null ? TimedLineResult.endOfInput() : TimedLineResult.line(line);
    } catch (TimeoutException exception) {
      return TimedLineResult.timedOut();
    } catch (ExecutionException exception) {
      pendingLine = null;
      throw asIOException(exception.getCause());
    }
  }

  synchronized void discardPendingLine() throws IOException, InterruptedException {
    if (pendingLine == null) return;
    try {
      pendingLine.get();
    } catch (ExecutionException exception) {
      throw asIOException(exception.getCause());
    } finally {
      pendingLine = null;
    }
  }

  synchronized void ensureNoPendingLine() throws IOException {
    if (closed) throw new IOException("Console input is closed");
    if (pendingLine != null) throw new IOException("Console input is still pending");
  }

  @Override
  public synchronized void close() {
    if (!closed) {
      closed = true;
      executor.shutdown();
    }
  }

  private static IOException asIOException(Throwable cause) {
    if (cause instanceof IOException ioException) return ioException;
    return new IOException("Console input failed");
  }
}
