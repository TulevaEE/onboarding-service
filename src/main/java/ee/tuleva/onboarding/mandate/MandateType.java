package ee.tuleva.onboarding.mandate;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum MandateType {
  FUND_PENSION_OPENING,
  WITHDRAWAL_CANCELLATION,
  EARLY_WITHDRAWAL_CANCELLATION,
  TRANSFER_CANCELLATION,
  /*TRANSFER,
  SELECTION,
  PAYMENT,
  PAYMENT_RATE,*/
  UNKNOWN;
}
