package ee.tuleva.onboarding.time;

import static ee.tuleva.onboarding.time.ClockHolder.CLOCK;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return CLOCK();
  }
}
