package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class LimitCheckNotifier {

  private final OperationsNotificationService notificationService;

  void notify(List<LimitCheckResult> results) {
    try {
      var hasAnyBreaches = results.stream().anyMatch(LimitCheckResult::hasBreaches);

      if (!hasAnyBreaches) {
        notificationService.sendMessage(
            "Limit check completed: all funds within limits", INVESTMENT);
        return;
      }

      var message = new StringBuilder("LIMIT BREACH DETECTED\n");

      for (var result : results) {
        if (!result.hasBreaches()) {
          continue;
        }

        for (var breach : result.positionBreaches()) {
          if (breach.severity() != OK) {
            message.append(
                "\n[%s] POSITION %s: %s=%s%%, soft=%s%%, hard=%s%%"
                    .formatted(
                        breach.severity(),
                        result.fund(),
                        breach.label(),
                        breach.actualPercent(),
                        breach.softLimitPercent(),
                        breach.hardLimitPercent()));
          }
        }

        for (var breach : result.providerBreaches()) {
          if (breach.severity() != OK) {
            message.append(
                "\n[%s] PROVIDER %s: %s=%s%%, soft=%s%%, hard=%s%%"
                    .formatted(
                        breach.severity(),
                        result.fund(),
                        breach.provider(),
                        breach.actualPercent(),
                        breach.softLimitPercent(),
                        breach.hardLimitPercent()));
          }
        }

        if (result.reserveBreach() != null && result.reserveBreach().severity() != OK) {
          var breach = result.reserveBreach();
          message.append(
              "\n[%s] RESERVE %s: cash=%s, soft=%s, hard=%s"
                  .formatted(
                      breach.severity(),
                      result.fund(),
                      breach.cashBalance(),
                      breach.reserveSoft(),
                      breach.reserveHard()));
        }

        if (result.freeCashBreach() != null && result.freeCashBreach().severity() != OK) {
          var breach = result.freeCashBreach();
          message.append(
              "\n[%s] FREE_CASH %s: freeCash=%s, max=%s"
                  .formatted(
                      breach.severity(), result.fund(), breach.freeCash(), breach.maxFreeCash()));
        }
      }

      notificationService.sendMessage(message.toString(), INVESTMENT);

    } catch (Exception e) {
      log.error("Failed to send limit check notification", e);
    }
  }
}
