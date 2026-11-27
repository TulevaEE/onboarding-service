package ee.tuleva.onboarding.investment.transaction;

import java.util.Optional;

@FunctionalInterface
interface OrderSendAccess {

  Optional<OrderSender> senderFor(String operator);
}
