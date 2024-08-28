package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.epis.mandate.details.*;
import lombok.Getter;

public enum MandateType {
  FUND_PENSION_OPENING(FundPensionOpeningMandateDetails.class),
  WITHDRAWAL_CANCELLATION(WithdrawalCancellationMandateDetails.class),
  EARLY_WITHDRAWAL_CANCELLATION(EarlyWithdrawalCancellationMandateDetails.class),
  TRANSFER_CANCELLATION(TransferCancellationMandateDetails.class),
  /*TRANSFER,
  SELECTION,
  PAYMENT,
  PAYMENT_RATE,*/
  UNKNOWN(null);

  @Getter private final Class<? extends MandateDetails> mandateDetailsClass;

  MandateType(Class<? extends MandateDetails> mandateDetailsClass) {
    this.mandateDetailsClass = mandateDetailsClass;
  }
}
