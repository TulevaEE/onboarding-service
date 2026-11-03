package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;

class TrustListHealthIndicatorTest {

  private final TrustListHealthIndicator trustList = new TrustListHealthIndicator();

  @Test
  void isOutOfServiceBeforeTheTrustListHasLoaded() {
    assertThat(trustList.health()).isEqualTo(Health.outOfService().build());
  }

  @Test
  void isUpOnceTheTrustListHasLoaded() {
    trustList.trustListLoaded();

    assertThat(trustList.health()).isEqualTo(Health.up().build());
  }
}
