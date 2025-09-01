package ee.tuleva.onboarding.capital.transfer;

public enum CapitalTransferContractState {
  CREATED,
  SELLER_SIGNED,
  BUYER_SIGNED,
  PAYMENT_CONFIRMED_BY_BUYER,
  PAYMENT_CONFIRMED_BY_SELLER,
  APPROVED,
  EXECUTED,
  APPROVED_AND_NOTIFIED,
  CANCELLED
}
