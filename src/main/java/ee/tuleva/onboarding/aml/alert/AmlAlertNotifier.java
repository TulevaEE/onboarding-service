package ee.tuleva.onboarding.aml.alert;

import static ee.tuleva.onboarding.aml.alert.AlertPartyType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@NullMarked
@Component
@RequiredArgsConstructor
public class AmlAlertNotifier {

  private final OperationsNotificationService notificationService;
  private final UserService userService;

  @EventListener
  public void onAmlThresholdAlert(AmlThresholdAlertEvent event) {
    notificationService.sendMessage(
        "AML alert: %s, %s, amount=%s, ref=%s"
            .formatted(
                event.getType(),
                partyReference(event),
                event.getAmount().toPlainString(),
                event.getReference()),
        AML);
  }

  private String partyReference(AmlThresholdAlertEvent event) {
    if (event.getPartyType() == LEGAL_ENTITY) {
      return "code=" + event.getPersonalId();
    }
    return userService
        .findByPersonalCode(event.getPersonalId())
        .map(User::getId)
        .map(userId -> "userId=" + userId)
        .orElse("userId=null");
  }
}
