package ee.tuleva.onboarding.investment.transaction;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class OrderSendAccessNotConnected implements OrderSendAccess {

  @Override
  public Optional<OrderSender> senderFor(String operator) {
    throw new ResponseStatusException(
        SERVICE_UNAVAILABLE, "Order send access is not connected: operator=" + operator);
  }
}
