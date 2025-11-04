package ee.tuleva.onboarding.analytics.secondpillar

import java.time.Instant

import static ee.tuleva.onboarding.analytics.secondpillar.SecondPillarAbandonment.SecondPillarAbandonmentBuilder
import static ee.tuleva.onboarding.analytics.secondpillar.SecondPillarAbandonment.builder

class SecondPillarAbandonmentFixture {

  static SecondPillarAbandonmentBuilder aSecondPillarAbandonment() {
    builder()
        .personalCode(uniquePersonalCode(0))
        .firstName("John")
        .lastName("Doe")
        .email(uniqueEmail(0))
        .language("EST")
        .lastEmailSentDate(Instant.parse("2024-01-15T10:00:00Z"))
        .count(5)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .path("/2nd-pillar-flow")
        .currentRate(2)
        .pendingRate(null)
        .pendingRateDate(null)
  }

  static String uniquePersonalCode(int uniqueId) {
    "385103095${String.format('%02d', uniqueId)}"
  }

  static String uniqueEmail(int uniqueId) {
    "john.doe${uniqueId}@example.com"
  }
}
