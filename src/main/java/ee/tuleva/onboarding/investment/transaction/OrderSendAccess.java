package ee.tuleva.onboarding.investment.transaction;

@FunctionalInterface
interface OrderSendAccess {

  boolean canSend(String operator);
}
