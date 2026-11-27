package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.ACCOUNT_TAKEOVER_SIGNALS;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.HIGH_RISK;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.RISK_LEVEL_UNAVAILABLE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.SCREENING_UNAVAILABLE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Onboarding.NOT_CHECKED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Outcome.CLEAR;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Outcome.FREEZE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Outcome.HOLD_PAYOUT;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Outcome.RETRY;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Risk.HIGH;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Risk.NOT_ASKED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Risk.NOT_HIGH;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal.CONTACT_CHANGED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal.LARGE_PAYOUT;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal.NEW_IBAN;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.holdReasons;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.outcome;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Evidence;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Risk;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Screening;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RedemptionVerdictTest {

  @ParameterizedTest
  @EnumSource(TakeoverSignal.class)
  void anyTwoTakeoverSignalsHoldThePayout(TakeoverSignal theOneNotRaised) {
    var evidence = clearPartyWith(EnumSet.complementOf(EnumSet.of(theOneNotRaised)));

    assertThat(outcome(evidence)).isEqualTo(HOLD_PAYOUT);
    assertThat(holdReasons(evidence)).containsExactly(ACCOUNT_TAKEOVER_SIGNALS);
  }

  @Test
  void allThreeTakeoverSignalsHoldThePayout() {
    var evidence = clearPartyWith(Set.of(LARGE_PAYOUT, NEW_IBAN, CONTACT_CHANGED));

    assertThat(outcome(evidence)).isEqualTo(HOLD_PAYOUT);
    assertThat(holdReasons(evidence)).containsExactly(ACCOUNT_TAKEOVER_SIGNALS);
  }

  @ParameterizedTest
  @EnumSource(TakeoverSignal.class)
  void aSingleTakeoverSignalAloneClearsThePayout(TakeoverSignal theOnlyOneRaised) {
    var evidence = clearPartyWith(Set.of(theOnlyOneRaised));

    assertThat(outcome(evidence)).isEqualTo(CLEAR);
    assertThat(holdReasons(evidence)).isEmpty();
  }

  @Test
  void twoTakeoverSignalsOfAHighRiskPartyHoldThePayoutForBothReasons() {
    var evidence = new Evidence(Screening.CLEAR, HIGH, NOT_CHECKED, Set.of(NEW_IBAN, LARGE_PAYOUT));

    assertThat(outcome(evidence)).isEqualTo(HOLD_PAYOUT);
    assertThat(holdReasons(evidence))
        .containsExactlyInAnyOrder(HIGH_RISK, ACCOUNT_TAKEOVER_SIGNALS);
  }

  @Test
  void aSingleTakeoverSignalOfAHighRiskPartyHoldsThePayoutAsHighRiskAlone() {
    var evidence = new Evidence(Screening.CLEAR, HIGH, NOT_CHECKED, Set.of(CONTACT_CHANGED));

    assertThat(outcome(evidence)).isEqualTo(HOLD_PAYOUT);
    assertThat(holdReasons(evidence)).containsExactly(HIGH_RISK);
  }

  @Test
  void twoTakeoverSignalsStillRetryWhileTheRiskLevelIsUnavailableAndHoldForBothByTheDeadline() {
    var evidence =
        new Evidence(
            Screening.CLEAR, Risk.UNAVAILABLE, NOT_CHECKED, Set.of(NEW_IBAN, CONTACT_CHANGED));

    assertThat(outcome(evidence)).isEqualTo(RETRY);
    assertThat(holdReasons(evidence))
        .containsExactlyInAnyOrder(RISK_LEVEL_UNAVAILABLE, ACCOUNT_TAKEOVER_SIGNALS);
  }

  @Test
  void twoTakeoverSignalsStillRetryWhileScreeningIsUnavailable() {
    var evidence =
        new Evidence(
            Screening.UNAVAILABLE, NOT_ASKED, NOT_CHECKED, Set.of(LARGE_PAYOUT, CONTACT_CHANGED));

    assertThat(outcome(evidence)).isEqualTo(RETRY);
    assertThat(holdReasons(evidence))
        .containsExactlyInAnyOrder(SCREENING_UNAVAILABLE, ACCOUNT_TAKEOVER_SIGNALS);
  }

  @Test
  void twoTakeoverSignalsStillLetASanctionsHitFreezeTheOrder() {
    var evidence =
        new Evidence(
            Screening.SANCTION_HIT, NOT_ASKED, NOT_CHECKED, Set.of(LARGE_PAYOUT, NEW_IBAN));

    assertThat(outcome(evidence)).isEqualTo(FREEZE);
  }

  private static Evidence clearPartyWith(Set<TakeoverSignal> takeoverSignals) {
    return new Evidence(Screening.CLEAR, NOT_HIGH, NOT_CHECKED, takeoverSignals);
  }
}
