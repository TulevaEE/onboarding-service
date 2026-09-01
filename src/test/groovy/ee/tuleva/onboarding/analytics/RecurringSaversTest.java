package ee.tuleva.onboarding.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.mandate.RecurringPayments;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecurringSaversTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-03-15T00:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate EXPECTED_FROM = LocalDate.of(2025, 12, 1);
  private static final String PERSONAL_CODE = "38888888888";
  private static final SaverId SAVER = SaverId.person(PERSONAL_CODE);

  @Mock private AnalyticsThirdPillarTransactionRepository thirdPillarTransactions;
  @Mock private SavingsFundContributions savingsFundContributions;

  private RecurringSavers recurringSavers;

  @BeforeEach
  void setUp() {
    recurringSavers = new RecurringSavers(thirdPillarTransactions, savingsFundContributions, CLOCK);
  }

  @Test
  void hasRecurringSavingsFundPaymentsIsTrueAtTheThreeMonthThreshold() {
    given(savingsFundContributions.countIssuedPaymentMonthsSince(SAVER, EXPECTED_FROM))
        .willReturn(3);

    assertThat(recurringSavers.hasRecurringSavingsFundPayments(SAVER)).isTrue();
  }

  @Test
  void hasRecurringSavingsFundPaymentsIsFalseJustBelowTheThreeMonthThreshold() {
    given(savingsFundContributions.countIssuedPaymentMonthsSince(SAVER, EXPECTED_FROM))
        .willReturn(2);

    assertThat(recurringSavers.hasRecurringSavingsFundPayments(SAVER)).isFalse();
  }

  @Test
  void recurringPaymentsOfIsTrueForBothPillarsAtTheThreshold() {
    given(thirdPillarTransactions.countOwnContributionMonthsSince(PERSONAL_CODE, EXPECTED_FROM))
        .willReturn(3);
    given(savingsFundContributions.countIssuedPaymentMonthsSince(SAVER, EXPECTED_FROM))
        .willReturn(3);

    assertThat(recurringSavers.recurringPaymentsOf(PERSONAL_CODE))
        .isEqualTo(new RecurringPayments(true, true));
  }

  @Test
  void recurringPaymentsOfIsFalseForThirdPillarJustBelowTheThreshold() {
    given(thirdPillarTransactions.countOwnContributionMonthsSince(PERSONAL_CODE, EXPECTED_FROM))
        .willReturn(2);
    given(savingsFundContributions.countIssuedPaymentMonthsSince(SAVER, EXPECTED_FROM))
        .willReturn(3);

    assertThat(recurringSavers.recurringPaymentsOf(PERSONAL_CODE))
        .isEqualTo(new RecurringPayments(false, true));
  }

  @Test
  void recurringPaymentsOfIsFalseForSavingsFundJustBelowTheThreshold() {
    given(thirdPillarTransactions.countOwnContributionMonthsSince(PERSONAL_CODE, EXPECTED_FROM))
        .willReturn(3);
    given(savingsFundContributions.countIssuedPaymentMonthsSince(SAVER, EXPECTED_FROM))
        .willReturn(2);

    assertThat(recurringSavers.recurringPaymentsOf(PERSONAL_CODE))
        .isEqualTo(new RecurringPayments(true, false));
  }
}
