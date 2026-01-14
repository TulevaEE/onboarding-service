package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SlackPositionCalculationNotificationSender implements PositionCalculationNotificationSender {

  private final OperationsNotificationService notificationService;

  @Override
  public void send(String message) {
    notificationService.sendMessage(message, INVESTMENT);
  }
}
