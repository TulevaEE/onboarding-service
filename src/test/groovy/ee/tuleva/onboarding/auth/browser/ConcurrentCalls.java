package ee.tuleva.onboarding.auth.browser;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

public final class ConcurrentCalls {

  private ConcurrentCalls() {}

  public static void runTogether(int rounds, Runnable... calls) throws Exception {
    try (ExecutorService executor = Executors.newFixedThreadPool(calls.length)) {
      for (int round = 0; round < rounds; round++) {
        runRound(executor, calls);
      }
    }
  }

  private static void runRound(ExecutorService executor, Runnable... calls) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> results =
        Stream.of(calls)
            .<Future<?>>map(
                call ->
                    executor.submit(
                        () -> {
                          start.await();
                          call.run();
                          return null;
                        }))
            .toList();
    start.countDown();
    for (Future<?> result : results) {
      result.get();
    }
  }
}
