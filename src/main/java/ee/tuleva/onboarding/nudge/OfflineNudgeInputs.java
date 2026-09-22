package ee.tuleva.onboarding.nudge;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OfflineNudgeInputs {

  private final PensionRegistry pensionRegistry;
  private final KnownLookups lookups;
  private final PaymentRateSeasons paymentRateSeasons;

  NudgeInputs assemble(OfflineSaver saver, NudgeContext context) {
    PensionRegistrySnapshot registry =
        pensionRegistry
            .snapshotFor(saver.personalCode())
            .orElse(PensionRegistrySnapshot.UNKNOWN_PERSON);
    boolean thirdPillarActive = registry.thirdPillarActive() || context.impliesThirdPillar();
    NudgeAccount self = saver.account();
    Known savingsFundRecurring = lookups.savingsFundRecurring(self);
    Known savingsFundSaver = lookups.savesFor(self);
    return NudgeInputs.builder()
        .adult(saver.adult())
        .reachedRetirementAge(saver.reachedRetirementAge())
        .member(saver.member())
        .secondPillarActive(registry.secondPillarActive())
        .thirdPillarActive(thirdPillarActive)
        .secondPillarPartiallyConverted(registry.secondPillarAtTuleva())
        .secondPillarFullyConverted(registry.secondPillarAtTuleva())
        .secondPillarFee(null)
        .thirdPillarPartiallyConverted(true)
        .thirdPillarFullyConverted(true)
        .thirdPillarFee(null)
        .canIncreasePaymentRate(registry.canIncreasePaymentRate())
        .pendingSecondPillarTransfer(false)
        .pendingSecondPillarWithdrawal(false)
        .leftSecondPillar(Known.of(registry.leftSecondPillar()))
        .thirdPillarRecurring(
            thirdPillarActive ? lookups.thirdPillarRecurring(saver.personalCode()) : Known.NO)
        .savingsFundRecurring(savingsFundRecurring)
        .savesInSavingsFund(
            lookups.savesForAnyRepresentedParty(saver.personalCode(), savingsFundSaver))
        .savingsFundSaver(savingsFundSaver)
        .taxHeadroom(Known.UNKNOWN)
        .feeComparison(null)
        .savingsFundFeePercent(lookups.savingsFundFeePercent())
        .paymentRateSeason(paymentRateSeasons.current())
        .build();
  }
}
