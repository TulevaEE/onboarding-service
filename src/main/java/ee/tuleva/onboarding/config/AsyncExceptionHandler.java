package ee.tuleva.onboarding.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;

@Slf4j
public class AsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

  @Override
  public void handleUncaughtException(Throwable throwable, Method method, Object... obj) {
    log.error("Uncaught async exception", throwable);
    throw new RuntimeException(throwable);
  }

}
