package ee.tuleva.onboarding.aml.alert;

import static ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionFixture.exampleTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransaction;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThirdPillarTransactionFingerprintTest {

  @Test
  @DisplayName(
      "is stable across analytics row id changes for the same underlying transaction (re-sync safe)")
  void stableAcrossIdChanges() {
    AnalyticsThirdPillarTransaction a = exampleTransaction();
    a.setId(1L);
    AnalyticsThirdPillarTransaction b = exampleTransaction();
    b.setId(999L);

    assertThat(ThirdPillarTransactionFingerprint.of(a))
        .isEqualTo(ThirdPillarTransactionFingerprint.of(b));
  }

  @Test
  @DisplayName("differs when a natural field differs")
  void differsOnNaturalField() {
    AnalyticsThirdPillarTransaction a = exampleTransaction();
    AnalyticsThirdPillarTransaction b = exampleTransaction();
    b.setTransactionValue(b.getTransactionValue().add(BigDecimal.ONE));

    assertThat(ThirdPillarTransactionFingerprint.of(a))
        .isNotEqualTo(ThirdPillarTransactionFingerprint.of(b));
  }

  @Test
  @DisplayName("handles nullable optional fields without throwing")
  void handlesNullOptionalFields() {
    AnalyticsThirdPillarTransaction t = exampleTransaction();
    t.setTransactionSource(null);
    t.setTransactionValue(null);

    assertThat(ThirdPillarTransactionFingerprint.of(t)).isNotBlank();
  }
}
