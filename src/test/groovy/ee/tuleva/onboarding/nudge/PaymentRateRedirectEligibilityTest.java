package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.nudge.Known.UNKNOWN;
import static ee.tuleva.onboarding.nudge.Known.YES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentRateRedirectEligibilityTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final Clock CLOCK =
      Clock.fixed(LocalDateTime.parse("2026-09-15T09:00:00").atZone(TALLINN).toInstant(), TALLINN);
  private static final Instant PREVIOUS_DEADLINE =
      LocalDateTime.parse("2025-11-30T23:59:59.999999999").atZone(TALLINN).toInstant();

  @Mock private NudgeDecisionService nudgeDecisionService;
  @Mock private SecondPillarPaymentRateService paymentRateService;
  @Mock private PaymentRateChangeHistory paymentRateChangeHistory;
  @Mock private MonthlySalary monthlySalary;

  private final User user = sampleUser().build();

  private PaymentRateRedirectEligibility eligibility;

  @BeforeEach
  void setUp() {
    eligibility =
        new PaymentRateRedirectEligibility(
            nudgeDecisionService,
            paymentRateService,
            paymentRateChangeHistory,
            monthlySalary,
            new PaymentRateSeasons(CLOCK, new MandateDeadlinesService(CLOCK, new PublicHolidays())),
            properties(new BigDecimal("3300")));
    lenient()
        .when(nudgeDecisionService.inputsFor(user))
        .thenReturn(NudgeInputsFixture.everythingSorted().build());
    lenient().when(paymentRateService.getPaymentRates(user)).thenReturn(new PaymentRates(2, null));
    lenient().when(paymentRateChangeHistory.hasChangeSince(any(), any())).thenReturn(false);
    lenient()
        .when(monthlySalary.latestGross(user, 2))
        .thenReturn(Optional.of(new BigDecimal("3300.00")));
  }

  private static PaymentRateRedirectProperties properties(BigDecimal salaryThreshold) {
    return new PaymentRateRedirectProperties(
        true, "season-2026-test-seed", 20, salaryThreshold, LocalDate.of(2026, 9, 1));
  }

  @Test
  void aFullyConvertedTwoPercentSaverOnAHighSalaryIsEligible() {
    assertThat(eligibility.isEligible(user)).isTrue();
  }

  @Test
  void theRateChangeHistoryIsReadFromThePreviousPaymentRateDeadlineOnwards() {
    given(paymentRateChangeHistory.hasChangeSince(user, PREVIOUS_DEADLINE)).willReturn(true);

    assertThat(eligibility.isEligible(user)).isFalse();
  }

  @Test
  void aSaverWithoutAnActiveSecondPillarIsNotEligible() {
    given(nudgeDecisionService.inputsFor(user))
        .willReturn(NudgeInputsFixture.everythingSorted().secondPillarActive(false).build());

    assertThat(eligibility.isEligible(user)).isFalse();
  }

  @Test
  void aTwoPercentSaverInACheapFundElsewhereIsEligibleWithoutAnyTulevaUnits() {
    given(nudgeDecisionService.inputsFor(user))
        .willReturn(
            NudgeInputsFixture.everythingSorted()
                .secondPillarPartiallyConverted(false)
                .secondPillarFullyConverted(false)
                .secondPillarFee(new BigDecimal("0.0029"))
                .build());

    assertThat(eligibility.isEligible(user)).isTrue();
  }

  @Test
  void aSaverWhoseSecondPillarSitsInAnExpensiveFundIsNotEligibleBecauseTheTransferNudgeWins() {
    given(nudgeDecisionService.inputsFor(user))
        .willReturn(
            NudgeInputsFixture.everythingSorted()
                .secondPillarPartiallyConverted(false)
                .secondPillarFullyConverted(false)
                .secondPillarFee(new BigDecimal("0.0065"))
                .build());
    assertThat(eligibility.isEligible(user)).isFalse();

    given(nudgeDecisionService.inputsFor(user))
        .willReturn(
            NudgeInputsFixture.everythingSorted()
                .secondPillarFullyConverted(false)
                .secondPillarFee(new BigDecimal("0.003"))
                .build());
    assertThat(eligibility.isEligible(user)).isFalse();
  }

  @Test
  void anUnknownSecondPillarFeeIsNotEligible() {
    given(nudgeDecisionService.inputsFor(user))
        .willReturn(
            NudgeInputsFixture.everythingSorted()
                .secondPillarFullyConverted(false)
                .secondPillarFee(null)
                .build());

    assertThat(eligibility.isEligible(user)).isFalse();
  }

  @Test
  void aMinorIsNotEligible() {
    given(nudgeDecisionService.inputsFor(user))
        .willReturn(NudgeInputsFixture.everythingSorted().adult(false).build());

    assertThat(eligibility.isEligible(user)).isFalse();
  }

  @Test
  void aSaverAtRetirementAgeIsNotEligible() {
    given(nudgeDecisionService.inputsFor(user))
        .willReturn(NudgeInputsFixture.everythingSorted().reachedRetirementAge(true).build());

    assertThat(eligibility.isEligible(user)).isFalse();
  }

  @Test
  void aLeaverAndAnUnknownLeaverStatusAreBothNotEligible() {
    given(nudgeDecisionService.inputsFor(user))
        .willReturn(NudgeInputsFixture.everythingSorted().leftSecondPillar(YES).build());
    assertThat(eligibility.isEligible(user)).isFalse();

    given(nudgeDecisionService.inputsFor(user))
        .willReturn(NudgeInputsFixture.everythingSorted().leftSecondPillar(UNKNOWN).build());
    assertThat(eligibility.isEligible(user)).isFalse();
  }

  @Test
  void aPendingWithdrawalMakesTheRateRaiseMoot() {
    given(nudgeDecisionService.inputsFor(user))
        .willReturn(
            NudgeInputsFixture.everythingSorted().pendingSecondPillarWithdrawal(true).build());

    assertThat(eligibility.isEligible(user)).isFalse();
  }

  @Test
  void aSaverWhoAlreadyPaysMoreThanTwoPercentIsNotEligible() {
    given(paymentRateService.getPaymentRates(user)).willReturn(new PaymentRates(4, null));

    assertThat(eligibility.isEligible(user)).isFalse();
  }

  @Test
  void aPendingRaiseAboveTwoPercentIsNotEligibleButAPendingTwoPercentIs() {
    given(paymentRateService.getPaymentRates(user)).willReturn(new PaymentRates(2, 6));
    assertThat(eligibility.isEligible(user)).isFalse();

    given(paymentRateService.getPaymentRates(user)).willReturn(new PaymentRates(2, 2));
    assertThat(eligibility.isEligible(user)).isTrue();
  }

  @Test
  void aSalaryBelowTheThresholdIsNotEligibleAndTheThresholdItselfIs() {
    given(monthlySalary.latestGross(user, 2)).willReturn(Optional.of(new BigDecimal("3299.99")));
    assertThat(eligibility.isEligible(user)).isFalse();

    given(monthlySalary.latestGross(user, 2)).willReturn(Optional.of(new BigDecimal("3300.00")));
    assertThat(eligibility.isEligible(user)).isTrue();
  }

  @Test
  void anUnknownSalaryIsNotEligible() {
    given(monthlySalary.latestGross(user, 2)).willReturn(Optional.empty());

    assertThat(eligibility.isEligible(user)).isFalse();
  }

  @Test
  void anyFailureReadingThePersonsDataCountsAsNotEligible() {
    given(nudgeDecisionService.inputsFor(user)).willThrow(new IllegalStateException("EPIS down"));

    assertThat(eligibility.isEligible(user)).isFalse();
  }
}
