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

  private static final int MAX_GAINED_IN_MESSAGE = 5;

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
          var findings = findingsOf(result, checkType, scope);
          if (findings.isEmpty()) {
            continue;
          }
          var current = state(findings);
          var previous = previousState(result, checkType, scope);
          if (!hasSomethingNewToSay(current, previous, result)) {
            continue;
          }
          var gained =
              current.sameSeverityAs(previous) ? current.gainedSince(previous) : List.<String>of();
          transitions.add(
              new Transition(
                  result, checkType, scope, current.severity(), message(findings, gained)));
        }
      }
    }
    return transitions;
  }

  // A monthly leg is keyed on a fixed fee month: nothing rolls out of view, so a total that fell is
  // the money itself moving and has to speak. Only the daily legs sum over a window that moves on
  // its own, where a smaller total can mean no more than the oldest day leaving the window.
  private boolean hasSomethingNewToSay(
      CheckState current, CheckState previous, FeeCheckResult result) {
    if (!current.sameSeverityAs(previous)) {
      return true;
    }
    if (previous.predatesTheFingerprint()) {
      return false;
    }
    return !current.gainedSince(previous).isEmpty()
        || (result.coversAFixedFeeMonth()
            ? current.totalDiffersFrom(previous)
            : current.totalGrewSince(previous));
  }

  private List<FeeCheckFinding> findingsOf(
      FeeCheckResult result, FeeCheckType checkType, FeeCheckScope scope) {
    return result.findings().stream()
        .filter(f -> f.checkType() == checkType && f.scope() == scope)
        .toList();
  }

  private CheckState state(List<FeeCheckFinding> findings) {
    return new CheckState(
        findings.stream().map(FeeCheckFinding::severity).max(Enum::compareTo).orElseThrow(),
        FeeCheckFinding.fingerprint(findings),
        FeeCheckFinding.totalDeviation(findings));
  }

  // Diffs within the fee_month bucket, so a fresh month's failure is never masked by the previous
  // month having failed too.
  //
  // The finding set and the deviation travel with the severity because severity alone goes blind
  // on a standing failure: a divergence that cannot be recalculated - a fee accrual is
  // forward-only, so a day written before a fix keeps its old base forever - parks the check at
  // FAIL, and from then on every later FAIL is "no change" and never reaches anyone.
  private CheckState previousState(
      FeeCheckResult result, FeeCheckType checkType, FeeCheckScope scope) {
    var feeMonth = result.feeMonth();
    var rows =
        feeMonth == null
            ? eventRepository.findLatestDelivered(
                result.fund(), checkType, scope, PREVIOUS_AND_CURRENT)
            : eventRepository.findLatestDeliveredForFeeMonth(
                result.fund(), checkType, scope, feeMonth, PREVIOUS_AND_CURRENT);
    if (rows.size() < 2) {
      return new CheckState(PASS, List.of(), BigDecimal.ZERO);
    }
    var previous = rows.get(1);
    var severity = previous.getSeverity();
    var deviation = previous.getDeviationAmount();
    return new CheckState(
        severity != null ? severity : PASS,
        previous.fingerprint(),
        deviation != null ? deviation : BigDecimal.ZERO);
  }

  // What a run reported: the worst severity, the set of findings behind it, and what they add up
  // to. Severity alone goes blind on a standing failure, and a total alone cannot tell a check that
  // found something new from one whose oldest day rolled out of view.
  private record CheckState(
      FeeCheckSeverity severity, @Nullable List<String> fingerprint, BigDecimal totalDeviation) {

    boolean sameSeverityAs(CheckState other) {
      return severity == other.severity;
    }

    boolean predatesTheFingerprint() {
      return fingerprint == null;
    }

    List<String> gainedSince(CheckState previous) {
      var alreadyReported = previous.fingerprint;
      var reporting = fingerprint;
      if (alreadyReported == null || reporting == null) {
        return List.of();
      }
      return reporting.stream().filter(entry -> !alreadyReported.contains(entry)).toList();
    }

    boolean totalGrewSince(CheckState previous) {
      return totalDeviation.compareTo(previous.totalDeviation) > 0;
    }

    boolean totalDiffersFrom(CheckState previous) {
      return totalDeviation.compareTo(previous.totalDeviation) != 0;
    }
  }

  // At an unchanged severity the finding messages read exactly as they did on the run the operator
  // has already seen, so a re-alert has to name what the check has newly found - and say it in the
  // words of the finding that carries it, not of whichever finding happens to come first.
  private String message(List<FeeCheckFinding> findings, List<String> gained) {
    if (gained.isEmpty()) {
      return firstMessage(findings);
    }
    var carried = firstMessage(findingsCarrying(findings, gained));
    return newlyFound(gained) + (carried.isBlank() ? "" : " · " + carried);
  }

  private String newlyFound(List<String> gained) {
    var shown = gained.stream().limit(MAX_GAINED_IN_MESSAGE).toList();
    var suffix =
        gained.size() > MAX_GAINED_IN_MESSAGE
            ? " ... (" + (gained.size() - MAX_GAINED_IN_MESSAGE) + " more)"
            : "";
    return "New since the last alert: " + String.join(" · ", shown) + suffix;
  }

  private List<FeeCheckFinding> findingsCarrying(
      List<FeeCheckFinding> findings, List<String> gained) {
    return findings.stream().filter(finding -> finding.carriesAnyOf(gained)).toList();
  }

  private String firstMessage(List<FeeCheckFinding> findings) {
    return findings.stream()
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
