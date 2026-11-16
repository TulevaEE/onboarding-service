package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.PersonRiskLevel.Level.HIGH;
import static ee.tuleva.onboarding.aml.PersonRiskLevel.Level.NOT_HIGH;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PersonRiskLevelTest {

  private static final Instant PEP_HIT_AT = Instant.parse("2026-11-16T09:00:00Z");

  @Test
  void aTkfLevelDecidedAfterAPepHitAccountsForIt() {
    var level = new PersonRiskLevel(NOT_HIGH, PEP_HIT_AT.plusSeconds(1));

    assertThat(level.accountsForPepHitAt(PEP_HIT_AT)).isTrue();
  }

  @Test
  void aTkfLevelDecidedAtTheSameInstantAsAPepHitDoesNotAccountForIt() {
    var level = new PersonRiskLevel(NOT_HIGH, PEP_HIT_AT);

    assertThat(level.accountsForPepHitAt(PEP_HIT_AT)).isFalse();
  }

  @Test
  void aTkfLevelDecidedBeforeAPepHitDoesNotAccountForIt() {
    var level = new PersonRiskLevel(NOT_HIGH, PEP_HIT_AT.minusSeconds(1));

    assertThat(level.accountsForPepHitAt(PEP_HIT_AT)).isFalse();
  }

  @Test
  void aTkfLevelWithoutADecisionTimeAccountsForNoPepHit() {
    var level = new PersonRiskLevel(NOT_HIGH, null);

    assertThat(level.accountsForPepHitAt(PEP_HIT_AT)).isFalse();
  }

  @Test
  void aHighLevelAccountsForAPepHitWhenEverItWasDecided() {
    var level = new PersonRiskLevel(HIGH, PEP_HIT_AT.minusSeconds(1));

    assertThat(level.accountsForPepHitAt(PEP_HIT_AT)).isTrue();
  }
}
