package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.mandate.application.ApplicationType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum MandateType {
  WITHDRAWAL_CANCELLATION,
  EARLY_WITHDRAWAL_CANCELLATION,
  TRANSFER_CANCELLATION,
  TRANSFER,
  SELECTION,
  PAYMENT,
  PAYMENT_RATE,
  UNKNOWN;

//  public fromApplicationType() {
//    return MandateType.valueOf("");
//  }

}
