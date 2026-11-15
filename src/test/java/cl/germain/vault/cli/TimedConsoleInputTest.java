package cl.germain.vault.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TimedConsoleInputTest {
  @Test
  void timeoutKeepsSingleReadPendingUntilItsLineCanBeDiscarded() throws Exception {
    CountDownLatch readStarted = new CountDownLatch(1);
    CountDownLatch allowReadToFinish = new CountDownLatch(1);
    AtomicInteger reads = new AtomicInteger();
    TimedConsoleInput input =
        new TimedConsoleInput(prompt -> {
          int number = reads.incrementAndGet();
          if (number == 1) {
            readStarted.countDown();
            try {
              allowReadToFinish.await();
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
              throw new IOException("Read interrupted");
            }
            return "stale-command";
          }
          return "next-command";
        });

    assertEquals(TimedLineResult.timedOut(), input.readLineUntil("vault> ", 0));
    assertTrue(readStarted.await(5, TimeUnit.SECONDS));
    assertThrows(IOException.class, () -> input.readLineUntil("vault> ", 1_000_000));

    ExecutorService waiter = Executors.newSingleThreadExecutor();
    try {
      Future<?> discard =
          waiter.submit(() -> {
            try {
              input.discardPendingLine();
            } catch (Exception exception) {
              throw new RuntimeException(exception);
            }
          });
      assertFalse(discard.isDone());
      allowReadToFinish.countDown();
      discard.get(5, TimeUnit.SECONDS);
    } finally {
      allowReadToFinish.countDown();
      waiter.shutdownNow();
    }

    assertEquals(
        TimedLineResult.line("next-command"), input.readLineUntil("vault> ", 5_000_000_000L));
    assertEquals(2, reads.get());
  }

  @Test
  void endOfInputAndReaderErrorsAreReportedWithoutRetainingPendingState() throws Exception {
    TimedConsoleInput eof = new TimedConsoleInput(prompt -> null);
    assertEquals(TimedLineResult.endOfInput(), eof.readLineUntil("vault> ", 5_000_000_000L));

    TimedConsoleInput failed =
        new TimedConsoleInput(prompt -> {
          throw new IOException("synthetic console failure");
        });
    assertThrows(IOException.class, () -> failed.readLineUntil("vault> ", 5_000_000_000L));
    assertEquals(TimedLineResult.endOfInput(), eof.readLineUntil("vault> ", 5_000_000_000L));
  }
}
