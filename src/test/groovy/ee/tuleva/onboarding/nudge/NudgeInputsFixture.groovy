package ee.tuleva.onboarding.nudge

import java.time.LocalDate

import static ee.tuleva.onboarding.nudge.Known.NO
import static ee.tuleva.onboarding.nudge.Known.YES
import static ee.tuleva.onboarding.nudge.PaymentRateSeason.Mode.OFF_SEASON

class NudgeInputsFixture {

  static NudgeInputs.NudgeInputsBuilder everythingSorted() {
    return NudgeInputs.builder()
        .adult(true)
        .reachedRetirementAge(false)
        .member(true)
        .secondPillarActive(true)
        .thirdPillarActive(true)
        .secondPillarPartiallyConverted(true)
        .secondPillarFullyConverted(true)
        .secondPillarFee(0.0029)
        .thirdPillarPartiallyConverted(true)
        .thirdPillarFullyConverted(true)
        .thirdPillarFee(0.0029)
        .canIncreasePaymentRate(false)
        .pendingSecondPillarTransfer(false)
        .pendingSecondPillarWithdrawal(false)
        .leftSecondPillar(NO)
        .thirdPillarRecurring(YES)
        .savingsFundRecurring(YES)
        .savesInSavingsFund(YES)
        .savingsFundSaver(YES)
        .taxHeadroom(NO)
        .feeComparison(null)
        .savingsFundFeePercent(0.28)
        .paymentRateSeason(sampleSeason(OFF_SEASON))
  }

  static PaymentRateSeason sampleSeason(PaymentRateSeason.Mode mode) {
    return new PaymentRateSeason(LocalDate.parse("2026-11-30"), LocalDate.parse("2027-01-01"), mode)
  }

  static FeeComparison sampleFeeComparison() {
    return new FeeComparison(0.65, 130, 56, 74)
  }
}
