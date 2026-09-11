package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.mandate.details.*;
import java.util.EnumSet;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

public enum MandateType {
  FUND_PENSION_OPENING(FundPensionOpeningMandateDetails.class),
  WITHDRAWAL_CANCELLATION(WithdrawalCancellationMandateDetails.class),
  EARLY_WITHDRAWAL_CANCELLATION(EarlyWithdrawalCancellationMandateDetails.class),
  TRANSFER_CANCELLATION(TransferCancellationMandateDetails.class),
  PARTIAL_WITHDRAWAL(PartialWithdrawalMandateDetails.class),
  PAYMENT_RATE_CHANGE(PaymentRateChangeMandateDetails.class),
  SELECTION(SelectionMandateDetails.class),
  /*TRANSFER,
  PAYMENT,*/
  UNKNOWN(null);

  @Getter private final @Nullable Class<? extends MandateDetails> mandateDetailsClass;

  MandateType(@Nullable Class<? extends MandateDetails> mandateDetailsClass) {
    this.mandateDetailsClass = mandateDetailsClass;
  }

  public boolean isWithdrawalType() {
    return EnumSet.of(FUND_PENSION_OPENING, PARTIAL_WITHDRAWAL).contains(this);
  }
}
