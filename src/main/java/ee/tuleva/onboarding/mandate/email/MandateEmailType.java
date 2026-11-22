package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.notification.email.EmailType.*;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
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
    if (mandate.isRejoinCancellation()) {
      return SECOND_PILLAR_REJOIN_CANCELLATION;
    }
    if (mandate.isThirdPillar()) {
      return THIRD_PILLAR_PAYMENT_REMINDER_MANDATE;
    }
    return SECOND_PILLAR_MANDATE;
  }

  public static EmailType emailTypeFor(MandateBatch batch) {
    if (batch.getMandates().stream()
        .anyMatch(mandate -> mandate.getMandateType() == MandateType.REJOIN)) {
      return SECOND_PILLAR_REJOIN;
    }

    if (isWithdrawalBatch(batch)) {
      return WITHDRAWAL_BATCH;
    }

    throw new IllegalArgumentException("Cannot find email type for batch");
  }

  static boolean isWithdrawalBatch(MandateBatch batch) {
    return batch.getMandates().stream()
        .map(Mandate::getMandateType)
        .allMatch(MandateType::isWithdrawalType);
  }

  static boolean containsWithdrawal(MandateBatch batch) {
    return batch.getMandates().stream()
        .map(Mandate::getMandateType)
        .anyMatch(MandateType::isWithdrawalType);
  }

  private MandateEmailType() {}
}
