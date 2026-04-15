package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.PASS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.investment.report.ReportProvider;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HealthCheckNotifier {

  private final OperationsNotificationService notificationService;

  public void notify(ReportProvider provider, LocalDate date, List<HealthCheckResult> results) {
    try {
      var hasFails = results.stream().anyMatch(HealthCheckResult::hasFails);
      var hasWarnings = results.stream().anyMatch(HealthCheckResult::hasWarnings);

      if (!hasFails && !hasWarnings) {
        return;
      }

      var message = new StringBuilder();

      if (hasFails) {
        message.append(
            "IMPORT BLOCKED: %s %s — source files need to be fixed\n".formatted(provider, date));
      } else {
        message.append("Import warning: %s %s\n".formatted(provider, date));
      }

      for (var result : results) {
        for (var finding : result.findings()) {
          if (finding.severity() != PASS) {
            message.append(
                "\n[%s] %s %s: %s"
                    .formatted(
                        finding.severity(),
                        finding.checkType(),
                        finding.fund(),
                        finding.message()));
          }
        }
      }

      notificationService.sendMessage(message.toString(), INVESTMENT);

    } catch (Exception e) {
      log.error("Failed to send health check notification", e);
    }
  }
}
