package ee.tuleva.onboarding.config;

import static ee.tuleva.onboarding.auth.principal.ServiceSecurityContextUtil.createServiceSecurityContext;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.security.concurrent.DelegatingSecurityContextScheduledExecutorService;
import org.springframework.security.core.context.SecurityContext;

@Configuration
@EnableScheduling
public class SchedulingConfiguration implements SchedulingConfigurer {

  @Override
  public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    taskRegistrar.setScheduler(taskExecutor());
  }

  @Bean
  public Executor taskExecutor() {
    ScheduledExecutorService delegateExecutor = Executors.newSingleThreadScheduledExecutor();
    SecurityContext schedulerContext = createServiceSecurityContext();
    return new DelegatingSecurityContextScheduledExecutorService(
        delegateExecutor, schedulerContext);
  }
}
