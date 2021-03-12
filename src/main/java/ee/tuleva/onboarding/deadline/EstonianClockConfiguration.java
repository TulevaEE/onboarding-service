package ee.tuleva.onboarding.deadline;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EstonianClockConfiguration {

  @Bean
  public Clock estonianClock() {
    return Clock.system(ZoneId.of("Europe/Tallinn"));
  }

}
