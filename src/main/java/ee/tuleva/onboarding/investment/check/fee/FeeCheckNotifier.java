package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class FeeCheckNotifier {

  private static final Map<FeeCheckSeverity, String> EMOJI =
      Map.of(FAIL, "🛑", WARNING, "⚠️", NOT_RUN, "⏸", PASS, "✅");

  private final FeeCheckEventRepository eventRepository;
  private final OperationsNotificationService notificationService;

  FeeCheckNotification notify(List<FeeCheckResult> results) {
    try {
      var transitions = transitions(results);
      if (transitions.isEmpty()) {
        return FeeCheckNotification.NOTHING_TO_REPORT;
      }
      notificationService.sendMessage(buildMessage(transitions), INVESTMENT);
      return FeeCheckNotification.SENT;
    } catch (Exception e) {
      log.error("Failed to send fee check notification: results={}", results.size(), e);
      return FeeCheckNotification.SEND_FAILED;
    }
  }

  private List<Transition> transitions(List<FeeCheckResult> results) {
    var transitions = new ArrayList<Transition>();
    for (var result : results) {
      for (var checkType : FeeCheckType.values()) {
        for (var scope : FeeCheckScope.values()) {
          var current = currentSeverity(result, checkType, scope);
          if (current == null) {
            continue;
          }
          var previous = previousSeverity(result, checkType, scope);
          if (current == previous) {
            continue;
          }
          transitions.add(
              new Transition(result, checkType, scope, current, message(result, checkType, scope)));
        }
      }
    }
    return transitions;
  }

  private @Nullable FeeCheckSeverity currentSeverity(
      FeeCheckResult result, FeeCheckType checkType, FeeCheckScope scope) {
    return result.findings().stream()
        .filter(f -> f.checkType() == checkType && f.scope() == scope)
        .map(FeeCheckFinding::severity)
        .max(Enum::compareTo)
        .orElse(null);
  }

  // Diffs within the fee_month bucket, so a fresh month's failure is never masked by the previous
  // month having failed too, while a daily deviation that persists stays silent after the first.
  private FeeCheckSeverity previousSeverity(
      FeeCheckResult result, FeeCheckType checkType, FeeCheckScope scope) {
    var rows =
        result.feeMonth() == null
            ? eventRepository
                .findTop2ByFundAndCheckTypeAndFeeScopeAndAlertFailedFalseAndFeeMonthIsNullOrderByCreatedAtDesc(
                    result.fund(), checkType, scope)
            : eventRepository
                .findTop2ByFundAndCheckTypeAndFeeScopeAndAlertFailedFalseAndFeeMonthOrderByCreatedAtDesc(
                    result.fund(), checkType, scope, result.feeMonth());
    if (rows.size() < 2) {
      return PASS;
    }
    var severity = rows.get(1).getSeverity();
    return severity != null ? severity : PASS;
  }

  private String message(FeeCheckResult result, FeeCheckType checkType, FeeCheckScope scope) {
    return result.findings().stream()
        .filter(f -> f.checkType() == checkType && f.scope() == scope)
        .map(FeeCheckFinding::message)
        .filter(m -> !m.isBlank())
        .findFirst()
        .orElse("");
  }

  private String buildMessage(List<Transition> transitions) {
    var active = transitions.stream().filter(t -> t.severity() != PASS).toList();
    var deviations = active.stream().filter(t -> t.severity() != NOT_RUN).toList();
    var blind = active.stream().filter(t -> t.severity() == NOT_RUN).toList();
    var cleared = transitions.stream().filter(t -> t.severity() == PASS).toList();

    var message = new StringBuilder(header(deviations, blind));
    deviations.forEach(t -> message.append('\n').append(t.render()));
    if (!blind.isEmpty()) {
      message.append("\n\nCould not check:");
      blind.forEach(t -> message.append('\n').append(t.render()));
    }
    cleared.forEach(
        t ->
            message
                .append("\n[CLEARED] ")
                .append(t.checkType())
                .append(' ')
                .append(t.fundAndScope()));
    return message.toString();
  }

  private String header(List<Transition> deviations, List<Transition> blind) {
    if (deviations.stream().anyMatch(t -> t.severity() == FAIL)) {
      return "Fee check FAILED — needs manual correction";
    }
    if (!deviations.isEmpty()) {
      return "Fee check warning";
    }
    if (!blind.isEmpty()) {
      return "Fee check coverage changed";
    }
    return "✅ Fee check cleared";
  }

  private record Transition(
      FeeCheckResult result,
      FeeCheckType checkType,
      FeeCheckScope scope,
      FeeCheckSeverity severity,
      String message) {

    TulevaFund fund() {
      return result.fund();
    }

    @Nullable LocalDate feeMonth() {
      return result.feeMonth();
    }

    String fundAndScope() {
      return fund().name() + "/" + scope + (feeMonth() == null ? "" : " " + feeMonth());
    }

    String render() {
      return EMOJI.getOrDefault(severity, "")
          + " ["
          + severity
          + "] "
          + checkType
          + " "
          + fundAndScope()
          + (message.isBlank() ? "" : ": " + message);
    }
  }
}
