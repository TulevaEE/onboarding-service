package ee.tuleva.onboarding.nudge

import static ee.tuleva.onboarding.nudge.Known.NO
import static ee.tuleva.onboarding.nudge.Known.YES

class NudgeInputsFixture {

  static NudgeInputs.NudgeInputsBuilder everythingSorted() {
    return NudgeInputs.builder()
        .adult(true)
        .reachedRetirementAge(false)
        .member(true)
        .actingAsLegalEntity(false)
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
        .ownSavingsFundRecurring(YES)
        .accountRecurring(YES)
        .savesInSavingsFund(YES)
        .ownSavingsFundSaver(YES)
        .taxHeadroom(NO)
        .feeComparison(null)
        .savingsFundFeePercent(0.28)
  }

  static FeeComparison sampleFeeComparison() {
    return new FeeComparison(0.65, 130, 56, 74)
  }
}
