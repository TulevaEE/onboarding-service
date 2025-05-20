package ee.tuleva.onboarding.deadline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EstonianClockConfigurationTest {

  private EstonianClockConfiguration estonianClockConfiguration;

  @BeforeEach
  void setUp() {
    estonianClockConfiguration = new EstonianClockConfiguration();
  }

  @Test
  void estonianClock_shouldReturnClockWithCorrectZone() {
    // Act
    Clock clock = estonianClockConfiguration.estonianClock();

    // Assert
    assertNotNull(clock, "The estonianClock bean should not be null.");

    ZoneId expectedZoneId = ZoneId.of("Europe/Tallinn");
    assertEquals(
        expectedZoneId,
        clock.getZone(),
        "The clock should be configured with the 'Europe/Tallinn' ZoneId.");
  }
}
