package ee.tuleva.onboarding.config;

import java.util.concurrent.Executor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

@EnableAsync
@Configuration
@ConditionalOnProperty(name = "async.enabled", havingValue = "true")
public class AsyncConfiguration implements AsyncConfigurer {

  @Override
  public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(100);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("AsyncProcess-");
    executor.initialize();
    return new DelegatingSecurityContextAsyncTaskExecutor(executor);
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return new AsyncExceptionHandler();
  }
}
