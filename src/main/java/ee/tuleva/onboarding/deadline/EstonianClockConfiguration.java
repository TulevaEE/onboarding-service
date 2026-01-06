package ee.tuleva.onboarding.deadline;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EstonianClockConfiguration {

  @Bean
  public Clock estonianClock() {
    return new Clock() {
      @Override
      public ZoneId getZone() {
        return ZoneId.of("Europe/Tallinn");
      }

      @Override
      public Clock withZone(ZoneId zone) {
        return ClockHolder.clock().withZone(zone);
      }

      @Override
      public Instant instant() {
        return ClockHolder.clock().instant();
      }
    };
  }
}
