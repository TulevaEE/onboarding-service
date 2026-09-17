package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.INFO;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class FeeCheckNotifier {

  private static final Limit PREVIOUS_AND_CURRENT = Limit.of(2);

  private static final Map<FeeCheckSeverity, String> EMOJI =
      Map.of(FAIL, "🛑", WARNING, "⚠️", NOT_RUN, "⏸", INFO, "ℹ️", PASS, "✅");

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
          var previous = previousState(result, checkType, scope);
          if (current == previous.severity()
              && sameAmount(currentDeviation(result, checkType, scope), previous.deviation())) {
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
  // month having failed too.
  //
  // The deviation travels with the severity because severity alone goes blind on a standing
  // failure: a divergence that cannot be recalculated - a fee accrual is forward-only, so a day
  // written before a fix keeps its old base forever - parks the check at FAIL, and from then on
  // every later FAIL is "no change" and never reaches anyone. A second bad day would arrive in
  // silence. Comparing the amount too keeps an unchanged failure quiet while letting a failure that
  // moved speak again.
  private PreviousState previousState(
      FeeCheckResult result, FeeCheckType checkType, FeeCheckScope scope) {
    var rows =
        result.feeMonth() == null
            ? eventRepository.findLatestDelivered(
                result.fund(), checkType, scope, PREVIOUS_AND_CURRENT)
            : eventRepository.findLatestDeliveredForFeeMonth(
                result.fund(), checkType, scope, result.feeMonth(), PREVIOUS_AND_CURRENT);
    if (rows.size() < 2) {
      return new PreviousState(PASS, null);
    }
    var previous = rows.get(1);
    var severity = previous.getSeverity();
    return new PreviousState(severity != null ? severity : PASS, previous.getDeviationAmount());
  }

  private BigDecimal currentDeviation(
      FeeCheckResult result, FeeCheckType checkType, FeeCheckScope scope) {
    return FeeCheckFinding.totalDeviation(
        result.findings().stream()
            .filter(f -> f.checkType() == checkType && f.scope() == scope)
            .toList());
  }

  // A row written before the amount was recorded has none; that is the same as no deviation.
  private static boolean sameAmount(BigDecimal current, @Nullable BigDecimal previous) {
    return current.compareTo(previous == null ? BigDecimal.ZERO : previous) == 0;
  }

  private record PreviousState(FeeCheckSeverity severity, @Nullable BigDecimal deviation) {}

  private String message(FeeCheckResult result, FeeCheckType checkType, FeeCheckScope scope) {
    return result.findings().stream()
        .filter(f -> f.checkType() == checkType && f.scope() == scope)
        .map(FeeCheckFinding::message)
        .filter(m -> !m.isBlank())
        .findFirst()
        .orElse("");
  }

  private String buildMessage(List<Transition> transitions) {
    var deviations =
        transitions.stream().filter(t -> t.severity() == WARNING || t.severity() == FAIL).toList();
    var notes = transitions.stream().filter(t -> t.severity() == INFO).toList();
    var blind = transitions.stream().filter(t -> t.severity() == NOT_RUN).toList();
    var cleared = transitions.stream().filter(t -> t.severity() == PASS).toList();

    var message = new StringBuilder(header(deviations, notes, blind));
    deviations.forEach(t -> message.append('\n').append(t.render()));
    if (!notes.isEmpty()) {
      message.append("\n\nExplained, no action needed:");
      notes.forEach(t -> message.append('\n').append(t.render()));
    }
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

  private String header(
      List<Transition> deviations, List<Transition> notes, List<Transition> blind) {
    if (deviations.stream().anyMatch(t -> t.severity() == FAIL)) {
      return "Fee check FAILED — needs manual correction";
    }
    if (!deviations.isEmpty()) {
      return "Fee check warning";
    }
    if (!notes.isEmpty()) {
      return "Fee check note — nothing to correct";
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
