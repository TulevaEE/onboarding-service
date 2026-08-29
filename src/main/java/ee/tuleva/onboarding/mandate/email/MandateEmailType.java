package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.notification.email.EmailType.*;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.PillarSuggestion;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.notification.email.EmailType;

public final class MandateEmailType {

  public static EmailType emailTypeFor(Mandate mandate) {
    if (mandate.isPaymentRateApplication()) {
      return SECOND_PILLAR_PAYMENT_RATE;
    }
    if (mandate.isWithdrawalCancellation() || mandate.isEarlyWithdrawalCancellation()) {
      return SECOND_PILLAR_WITHDRAWAL_CANCELLATION;
    }
    if (mandate.isTransferCancellation()) {
      return SECOND_PILLAR_TRANSFER_CANCELLATION;
    }
    if (mandate.isThirdPillar()) {
      return THIRD_PILLAR_PAYMENT_REMINDER_MANDATE;
    }
    return SECOND_PILLAR_MANDATE;
  }

  public static EmailType emailTypeFor(MandateBatch batch) {
    var allMandatesWithdrawals =
        batch.getMandates().stream()
            .allMatch(
                mandate ->
                    mandate.getMandateType() == MandateType.PARTIAL_WITHDRAWAL
                        || mandate.getMandateType() == MandateType.FUND_PENSION_OPENING);

    if (allMandatesWithdrawals) {
      return WITHDRAWAL_BATCH;
    }

    throw new IllegalArgumentException("Cannot find email type for batch");
  }

  public static EmailType emailTypeFor(Mandate mandate, PillarSuggestion pillarSuggestion) {
    if (mandate.isThirdPillar() && pillarSuggestion.isSuggestSecondPillar()) {
      return THIRD_PILLAR_SUGGEST_SECOND;
    }
    return THIRD_PILLAR_SUGGEST_SECOND;
  }

  private MandateEmailType() {}
}
