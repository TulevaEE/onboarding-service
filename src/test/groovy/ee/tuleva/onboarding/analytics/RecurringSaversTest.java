package ee.tuleva.onboarding.analytics;

import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.nudge.NudgeAccount;
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
  private static final String CHILD_CODE = "51111111111";

  @Mock private AnalyticsThirdPillarTransactionRepository thirdPillarTransactions;
  @Mock private SavingsFundContributions savingsFundContributions;

  private RecurringSavers recurringSavers;

  @BeforeEach
  void setUp() {
    recurringSavers = new RecurringSavers(thirdPillarTransactions, savingsFundContributions, CLOCK);
  }

  @Test
  void thirdPillarIsRecurringFromThreeContributionMonthsInTheLastFour() {
    given(thirdPillarTransactions.countOwnContributionMonthsSince(PERSONAL_CODE, EXPECTED_FROM))
        .willReturn(3);

    assertThat(recurringSavers.thirdPillar(PERSONAL_CODE)).isTrue();
  }

  @Test
  void thirdPillarIsNotRecurringJustBelowTheThreshold() {
    given(thirdPillarTransactions.countOwnContributionMonthsSince(PERSONAL_CODE, EXPECTED_FROM))
        .willReturn(2);

    assertThat(recurringSavers.thirdPillar(PERSONAL_CODE)).isFalse();
  }

  @Test
  void savingsFundCadenceIsScopedToTheGivenPersonalAccount() {
    given(
            savingsFundContributions.countIssuedPaymentMonthsSince(
                SaverId.person(CHILD_CODE), EXPECTED_FROM))
        .willReturn(3);

    assertThat(recurringSavers.savingsFund(NudgeAccount.person(CHILD_CODE))).isTrue();
    assertThat(recurringSavers.savingsFund(NudgeAccount.person(PERSONAL_CODE))).isFalse();
  }

  @Test
  void savingsFundCadenceIsScopedToTheGivenCompanyAccount() {
    given(
            savingsFundContributions.countIssuedPaymentMonthsSince(
                new SaverId(SaverId.Type.LEGAL_ENTITY, "12345678"), EXPECTED_FROM))
        .willReturn(2);

    assertThat(recurringSavers.savingsFund(new NudgeAccount(LEGAL_ENTITY, "12345678"))).isFalse();
  }
}
