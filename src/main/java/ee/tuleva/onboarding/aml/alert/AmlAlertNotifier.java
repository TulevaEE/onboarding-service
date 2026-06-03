package ee.tuleva.onboarding.aml.alert;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AmlAlertNotifier {

  private final OperationsNotificationService notificationService;
  private final UserService userService;

  @EventListener
  public void onAmlThresholdAlert(AmlThresholdAlertEvent event) {
    Long userId =
        userService.findByPersonalCode(event.getPersonalId()).map(User::getId).orElse(null);
    notificationService.sendMessage(
        "AML alert: %s, userId=%s, amount=%s, ref=%s"
            .formatted(event.getType(), userId, event.getAmount(), event.getReference()),
        AML);
  }
}
