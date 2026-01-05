package ee.tuleva.onboarding.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ClockConfig {

  @Bean
  @Primary
  public Clock clock() {
    return new ClockHolderDelegatingClock();
  }

  @RequiredArgsConstructor
  private static class ClockHolderDelegatingClock extends Clock {

    @Override
    public ZoneId getZone() {
      return ClockHolder.clock().getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return ClockHolder.clock().withZone(zone);
    }

    @Override
    public Instant instant() {
      return ClockHolder.clock().instant();
    }
  }
}
