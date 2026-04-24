package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.report.ReportProvider;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HealthCheckNotifier {

  private final OperationsNotificationService notificationService;
  private final HealthCheckEventRepository eventRepository;

  public boolean notify(ReportProvider provider, LocalDate date, List<HealthCheckResult> results) {
    try {
      var transitions = new ArrayList<Transition>();
      for (var result : results) {
        for (var checkType : HealthCheckType.values()) {
          var current = currentSeverity(result, checkType);
          var previous = previousSeverity(result.fund(), result.checkDate(), checkType);
          if (current == previous) {
            continue;
          }
          transitions.add(new Transition(result, checkType, current, previous));
        }
      }

      if (transitions.isEmpty()) {
        return false;
      }

      var activeTransitions = transitions.stream().filter(t -> t.current != PASS).toList();
      var clearedTransitions = transitions.stream().filter(t -> t.current == PASS).toList();

      var message = buildMessage(provider, date, activeTransitions, clearedTransitions);
      notificationService.sendMessage(message, INVESTMENT);
      return true;

    } catch (Exception e) {
      log.error("Failed to send health check notification", e);
      return false;
    }
  }

  private HealthCheckSeverity currentSeverity(HealthCheckResult result, HealthCheckType checkType) {
    return result.findings().stream()
        .filter(f -> f.checkType() == checkType)
        .map(HealthCheckFinding::severity)
        .max(Enum::compareTo)
        .orElse(PASS);
  }

  private HealthCheckSeverity previousSeverity(
      TulevaFund fund, LocalDate checkDate, HealthCheckType checkType) {
    var rows =
        eventRepository.findTop2ByFundAndCheckDateAndCheckTypeOrderByCreatedAtDesc(
            fund, checkDate, checkType);
    if (rows.size() < 2) {
      return PASS;
    }
    var severity = rows.get(1).getSeverity();
    return severity != null ? severity : PASS;
  }

  private String buildMessage(
      ReportProvider provider, LocalDate date, List<Transition> active, List<Transition> cleared) {
    var message = new StringBuilder();
    message.append(header(provider, date, active));

    for (var transition : active) {
      for (var finding : transition.result.findings()) {
        if (finding.checkType() == transition.checkType && finding.severity() != PASS) {
          message.append(
              "\n[%s] %s %s: %s"
                  .formatted(
                      finding.severity(), finding.checkType(), finding.fund(), finding.message()));
        }
      }
    }

    if (!cleared.isEmpty()) {
      if (!active.isEmpty()) {
        message.append("\n");
      }
      for (var transition : cleared) {
        message.append(
            "\n[CLEARED] %s %s".formatted(transition.checkType, transition.result.fund()));
      }
    }

    return message.toString();
  }

  private String header(ReportProvider provider, LocalDate date, List<Transition> active) {
    if (active.stream().anyMatch(t -> t.current == FAIL)) {
      return "IMPORT BLOCKED: %s %s — source files need to be fixed\n".formatted(provider, date);
    }
    if (active.stream().anyMatch(t -> t.current == WARNING)) {
      return "Import warning: %s %s\n".formatted(provider, date);
    }
    return "✅ Import cleared: %s %s\n".formatted(provider, date);
  }

  private record Transition(
      HealthCheckResult result,
      HealthCheckType checkType,
      HealthCheckSeverity current,
      HealthCheckSeverity previous) {}
}
