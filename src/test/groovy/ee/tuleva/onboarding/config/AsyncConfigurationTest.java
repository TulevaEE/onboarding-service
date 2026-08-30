package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigurationTest {

  private final AsyncConfiguration configuration = new AsyncConfiguration();

  @Test
  void getAsyncExecutorRunsTasksOnAnInitializedNamedPool() throws Exception {
    Executor executor = configuration.getAsyncExecutor();
    AtomicReference<String> threadName = new AtomicReference<>();

    CompletableFuture.runAsync(() -> threadName.set(Thread.currentThread().getName()), executor)
        .get();

    assertThat(threadName.get()).startsWith("AsyncProcess-");
  }

  @Test
  void getAsyncExecutorConfiguresTheUnderlyingThreadPool() throws Exception {
    ThreadPoolTaskExecutor delegate = unwrap(configuration.getAsyncExecutor());

    assertThat(delegate.getCorePoolSize()).isEqualTo(2);
    assertThat(delegate.getMaxPoolSize()).isEqualTo(10);
    assertThat(delegate.getQueueCapacity()).isEqualTo(100);
  }

  @Test
  void getAsyncUncaughtExceptionHandlerReturnsAnExceptionHandler() {
    assertThat(configuration.getAsyncUncaughtExceptionHandler())
        .isInstanceOf(AsyncExceptionHandler.class);
  }

  private static ThreadPoolTaskExecutor unwrap(Executor executor) throws Exception {
    Field field = executor.getClass().getSuperclass().getSuperclass().getDeclaredField("delegate");
    field.setAccessible(true);
    return (ThreadPoolTaskExecutor) field.get(executor);
  }
}
