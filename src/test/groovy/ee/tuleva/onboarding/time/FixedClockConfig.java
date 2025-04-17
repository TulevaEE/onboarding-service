// src/test/java/ee/tuleva/onboarding/time/FixedClockConfig.java
package ee.tuleva.onboarding.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class FixedClockConfig {

  protected static final Clock testClock = TestClockHolder.clock;
  protected static final Instant testInstant = TestClockHolder.now;
  protected static final LocalDateTime testLocalDateTime =
      testInstant.atZone(ZoneOffset.UTC).toLocalDateTime();

  @BeforeEach
  void setUpFixedClock() {
    ClockHolder.setClock(testClock);
  }

  @AfterEach
  void resetClockToDefault() {
    ClockHolder.setDefaultClock();
  }
}
