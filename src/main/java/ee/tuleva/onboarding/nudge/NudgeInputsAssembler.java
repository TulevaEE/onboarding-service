package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.pillar.Pillar.SECOND;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.PendingMandateApplications;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class NudgeInputsAssembler {

  private final PillarStatus pillarStatus;
  private final UserConversionService conversionService;
  private final PendingMandateApplications pendingApplications;
  private final SecondPillarPaymentRateService paymentRateService;
  private final SavingsFundFeeRate savingsFundFeeRate;
  private final FeeComparisonCalculator feeComparisonCalculator;
  private final KnownLookups lookups;

  NudgeInputs assemble(User user, NudgeAccount actingParty) {
    PillarActivity pillars = pillarStatus.of(user);
    ConversionResponse conversion = conversionService.getConversion(user);
    PaymentRates paymentRates = paymentRateService.getPaymentRates(user);
    NudgeAccount self = NudgeAccount.self(user);
    Known ownSavingsFundRecurring = lookups.savingsFundRecurring(self);
    Known ownSavingsFundSaver = lookups.savesFor(self);
    return NudgeInputs.builder()
        .adult(user.getAge() >= 18)
        .reachedRetirementAge(user.hasReachedRetirementAge())
        .member(user.isMember())
        .actingAsLegalEntity(actingParty.isLegalEntity())
        .secondPillarActive(pillars.secondPillarActive())
        .thirdPillarActive(pillars.thirdPillarActive())
        .secondPillarPartiallyConverted(conversion.isSecondPillarPartiallyConverted())
        .secondPillarFullyConverted(conversion.isSecondPillarFullyConverted())
        .secondPillarFee(conversion.getSecondPillarWeightedAverageFee())
        .thirdPillarPartiallyConverted(conversion.isThirdPillarPartiallyConverted())
        .thirdPillarFullyConverted(conversion.isThirdPillarFullyConverted())
        .thirdPillarFee(conversion.getThirdPillarWeightedAverageFee())
        .canIncreasePaymentRate(paymentRates.canIncrease())
        .pendingSecondPillarTransfer(
            !pendingApplications.getPendingExchanges(SECOND, user).isEmpty())
        .pendingSecondPillarWithdrawal(pendingApplications.hasPendingWithdrawals(user, SECOND))
        .leftSecondPillar(lookups.leftSecondPillar(user))
        .thirdPillarRecurring(
            pillars.thirdPillarActive() ? lookups.thirdPillarRecurring(user) : Known.NO)
        .ownSavingsFundRecurring(ownSavingsFundRecurring)
        .accountRecurring(
            actingParty.equals(self)
                ? ownSavingsFundRecurring
                : lookups.savingsFundRecurring(actingParty))
        .savesInSavingsFund(lookups.savesForAnyRepresentedParty(user, ownSavingsFundSaver))
        .ownSavingsFundSaver(ownSavingsFundSaver)
        .taxHeadroom(pillars.thirdPillarActive() ? lookups.taxHeadroom(user) : Known.NO)
        .feeComparison(feeComparison(user, conversion))
        .savingsFundFeePercent(savingsFundFeeRate.ongoingChargesPercent())
        .build();
  }

  private @Nullable FeeComparison feeComparison(User user, ConversionResponse conversion) {
    try {
      return feeComparisonCalculator
          .forSecondPillar(user, conversion.getSecondPillarWeightedAverageFee())
          .orElse(null);
    } catch (RuntimeException e) {
      log.warn("Nudge input unavailable, skipping the fee comparison: userId={}", user.getId(), e);
      return null;
    }
  }
}
