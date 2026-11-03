package ee.tuleva.onboarding.config;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component(TrustListHealthIndicator.NAME + "HealthIndicator")
class TrustListHealthIndicator implements HealthIndicator {

  static final String NAME = "trustList";

  private final AtomicBoolean loadedOnce = new AtomicBoolean();

  void trustListLoaded() {
    loadedOnce.set(true);
  }

  @Override
  public Health health() {
    return loadedOnce.get() ? Health.up().build() : Health.outOfService().build();
  }
}
