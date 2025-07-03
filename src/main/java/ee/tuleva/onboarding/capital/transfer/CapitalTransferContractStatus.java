package ee.tuleva.onboarding.capital.transfer;

public enum CapitalTransferContractStatus {
  SELLER_SIGNED,
  BUYER_SIGNED,
  PAYMENT_CONFIRMED_BY_BUYER,
  PAYMENT_CONFIRMED_BY_SELLER,
  BOARD_APPROVED,
  COMPLETED,
  CANCELLED
}
