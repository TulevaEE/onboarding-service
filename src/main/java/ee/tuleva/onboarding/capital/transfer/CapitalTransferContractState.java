package ee.tuleva.onboarding.capital.transfer;

import lombok.Getter;

public enum CapitalTransferContractState {
  CREATED(true),
  SELLER_SIGNED(true),
  BUYER_SIGNED(true),
  PAYMENT_CONFIRMED_BY_BUYER(true),
  PAYMENT_CONFIRMED_BY_SELLER(true),
  REVIEW(true),
  APPROVED(true),
  EXECUTED(false),
  APPROVED_AND_NOTIFIED(false),
  CANCELLED(false);

  @Getter private boolean isInProgress;

  CapitalTransferContractState(boolean isInProgress) {
    this.isInProgress = isInProgress;
  }
}
