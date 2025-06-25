package ee.tuleva.onboarding.config;

import java.lang.annotation.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.core.annotation.AliasFor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@EnableScheduling
@ExtendWith(SpringExtension.class)
@OverrideAutoConfiguration(enabled = false)
@ImportAutoConfiguration({
  TaskSchedulingAutoConfiguration.class,
  TaskExecutionAutoConfiguration.class,
  PropertyPlaceholderAutoConfiguration.class
})
@SpringJUnitConfig
public @interface ScheduledTest {

  @AliasFor(annotation = SpringJUnitConfig.class, attribute = "classes")
  Class<?>[] value() default {};

  @AliasFor(annotation = SpringJUnitConfig.class, attribute = "classes")
  Class<?>[] classes() default {};
}
