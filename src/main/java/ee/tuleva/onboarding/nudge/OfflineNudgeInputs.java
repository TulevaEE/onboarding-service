package ee.tuleva.onboarding.nudge;

import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OfflineNudgeInputs {

  private final PensionRegistry pensionRegistry;
  private final KnownLookups lookups;
  private final PaymentRateSeasons paymentRateSeasons;

  NudgeInputs assemble(User user, NudgeContext context) {
    PensionRegistrySnapshot registry =
        pensionRegistry
            .snapshotFor(user.getPersonalCode())
            .orElse(PensionRegistrySnapshot.UNKNOWN_PERSON);
    boolean thirdPillarActive = registry.thirdPillarActive() || context.impliesThirdPillar();
    NudgeAccount self = NudgeAccount.self(user);
    Known savingsFundRecurring = lookups.savingsFundRecurring(self);
    Known savingsFundSaver = lookups.savesFor(self);
    return NudgeInputs.builder()
        .adult(user.getAge() >= 18)
        .reachedRetirementAge(user.hasReachedRetirementAge())
        .member(user.isMember())
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
        .thirdPillarRecurring(thirdPillarActive ? lookups.thirdPillarRecurring(user) : Known.NO)
        .savingsFundRecurring(savingsFundRecurring)
        .savesInSavingsFund(lookups.savesForAnyRepresentedParty(user, savingsFundSaver))
        .savingsFundSaver(savingsFundSaver)
        .taxHeadroom(Known.UNKNOWN)
        .feeComparison(null)
        .savingsFundFeePercent(lookups.savingsFundFeePercent())
        .paymentRateSeason(paymentRateSeasons.current())
        .build();
  }
}
