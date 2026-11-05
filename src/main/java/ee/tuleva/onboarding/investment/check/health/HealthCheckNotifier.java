package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.FUND_IN_REPORT;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.time.format.DateTimeFormatter.BASIC_ISO_DATE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;

import ee.tuleva.onboarding.investment.report.ReportProvider;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HealthCheckNotifier {

  private final OperationsNotificationService notificationService;
  private final HealthCheckEventRepository eventRepository;

  public boolean notify(
      ReportProvider provider,
      LocalDate date,
      List<HealthCheckResult> results,
      Map<TulevaFund, Instant> earlierImports) {
    try {
      var transitions = new ArrayList<Transition>();
      for (var result : results) {
        for (var checkType : result.checkTypes()) {
          var current = currentSeverity(result, checkType);
          var latestEvents =
              eventRepository.findTop2ByFundAndCheckDateAndCheckTypeOrderByCreatedAtDesc(
                  result.fund(), result.checkDate(), checkType);
          var previous = previousSeverity(latestEvents);
          if (current == previous && !reasonChanged(result, checkType, current, latestEvents)) {
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

      var message =
          buildMessage(
              provider,
              date,
              results,
              new EarlierImports(earlierImports),
              activeTransitions,
              clearedTransitions);
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

  private boolean reasonChanged(
      HealthCheckResult result,
      HealthCheckType checkType,
      HealthCheckSeverity current,
      List<HealthCheckEvent> latestEvents) {
    if ((current != NOT_RUN && checkType != FUND_IN_REPORT) || latestEvents.size() < 2) {
      return false;
    }
    return checkType == FUND_IN_REPORT
        ? result.presenceDiffersFrom(latestEvents.get(1))
        : !messages(result, checkType).equals(storedMessages(latestEvents.get(1)));
  }

  private List<String> messages(HealthCheckResult result, HealthCheckType checkType) {
    return result.findings().stream()
        .filter(finding -> finding.checkType() == checkType)
        .map(HealthCheckFinding::message)
        .sorted()
        .toList();
  }

  @SuppressWarnings("unchecked")
  private List<String> storedMessages(HealthCheckEvent event) {
    var findings = event.getResult().get("findings");
    if (!(findings instanceof List<?> list)) {
      return List.of();
    }
    return list.stream()
        .filter(Map.class::isInstance)
        .map(finding -> ((Map<String, Object>) finding).get("message"))
        .filter(Objects::nonNull)
        .map(Object::toString)
        .sorted()
        .toList();
  }

  private static HealthCheckSeverity previousSeverity(List<HealthCheckEvent> latestEvents) {
    if (latestEvents.size() < 2) {
      return PASS;
    }
    var severity = latestEvents.get(1).getSeverity();
    return severity != null ? severity : PASS;
  }

  private String buildMessage(
      ReportProvider provider,
      LocalDate date,
      List<HealthCheckResult> results,
      EarlierImports earlierImports,
      List<Transition> active,
      List<Transition> cleared) {
    var message = new StringBuilder();
    message.append(header(provider, date, results, earlierImports, active));
    message.append(fundStatuses(results, earlierImports));
    correctionSteps(provider, date, results).ifPresent(message::append);

    message.append(findingLines(active));

    if (!cleared.isEmpty()) {
      if (!active.isEmpty()) {
        message.append("\n");
      }
      message.append(clearedLines(cleared));
    }

    return message.toString();
  }

  private static String findingLines(List<Transition> active) {
    var lines = new StringBuilder();
    active.stream()
        .flatMap(Transition::activeFindings)
        .filter(finding -> finding.checkType() != FUND_IN_REPORT)
        .forEach(
            finding -> lines.append(FindingLine.of(finding).render(EnumSet.of(finding.fund()))));
    active.stream()
        .flatMap(Transition::activeFindings)
        .filter(finding -> finding.checkType() == FUND_IN_REPORT)
        .collect(
            groupingBy(
                FindingLine::of,
                LinkedHashMap::new,
                mapping(
                    HealthCheckFinding::fund,
                    toCollection(() -> EnumSet.noneOf(TulevaFund.class)))))
        .forEach((line, funds) -> lines.append(line.render(funds)));
    return lines.toString();
  }

  private static String clearedLines(List<Transition> cleared) {
    var lines = new StringBuilder();
    cleared.stream()
        .filter(transition -> transition.checkType != FUND_IN_REPORT)
        .forEach(
            transition ->
                lines.append(
                    "\n[CLEARED] %s %s".formatted(transition.checkType, transition.result.fund())));
    var fundsBackInReport =
        cleared.stream()
            .filter(transition -> transition.checkType == FUND_IN_REPORT)
            .map(transition -> transition.result.fund())
            .collect(toCollection(() -> EnumSet.noneOf(TulevaFund.class)));
    if (!fundsBackInReport.isEmpty()) {
      lines.append("\n[CLEARED] %s %s".formatted(FUND_IN_REPORT, codes(fundsBackInReport)));
    }
    return lines.toString();
  }

  private static String codes(Set<TulevaFund> funds) {
    return funds.stream().map(TulevaFund::toString).collect(joining(", "));
  }

  private String header(
      ReportProvider provider,
      LocalDate date,
      List<HealthCheckResult> results,
      EarlierImports earlierImports,
      List<Transition> active) {
    var fundsImportingNothing = HealthCheckResult.fundsImportingNothing(results);
    if (!HealthCheckResult.blockedFunds(results).isEmpty()) {
      return "IMPORT BLOCKED: %s %s — %s, source files need to be fixed\n"
          .formatted(provider, date, earlierImports.whatIsLeftFor(fundsImportingNothing));
    }
    if (!fundsImportingNothing.isEmpty()) {
      return "Import warning: %s %s — %s\n"
          .formatted(provider, date, earlierImports.whatIsLeftFor(fundsImportingNothing));
    }
    if (active.stream().anyMatch(t -> t.current == WARNING)) {
      return "Import warning: %s %s\n".formatted(provider, date);
    }
    if (active.stream().anyMatch(t -> t.current == NOT_RUN)) {
      return "Import check could not run: %s %s\n".formatted(provider, date);
    }
    return "✅ Import cleared: %s %s\n".formatted(provider, date);
  }

  private String fundStatuses(List<HealthCheckResult> results, EarlierImports earlierImports) {
    return "Funds: %s\n"
        .formatted(
            results.stream()
                .map(result -> result.fund().getCode() + " " + earlierImports.importStatus(result))
                .collect(joining(", ")));
  }

  private static Optional<String> correctionSteps(
      ReportProvider provider, LocalDate date, List<HealthCheckResult> results) {
    if (provider != SEB || HealthCheckResult.blockedFunds(results).isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        ("Fix: ask SEB to resend the file, or email a corrected"
                + " TULEVA_pos_raport_%s_korrigeeritud.csv from funds@tuleva.ee to"
                + " funds@tuleva.ee only; a report up to 7 days old is picked up within 10 minutes"
                + " between 08:00 and 18:00, an older one needs /admin/reimport-positions\n")
            .formatted(date.format(BASIC_ISO_DATE)));
  }

  private record Transition(
      HealthCheckResult result,
      HealthCheckType checkType,
      HealthCheckSeverity current,
      HealthCheckSeverity previous) {

    Stream<HealthCheckFinding> activeFindings() {
      return result.findings().stream()
          .filter(finding -> finding.checkType() == checkType && finding.severity() != PASS);
    }
  }

  private record FindingLine(
      HealthCheckSeverity severity, HealthCheckType checkType, String message) {

    static FindingLine of(HealthCheckFinding finding) {
      return new FindingLine(finding.severity(), finding.checkType(), finding.message());
    }

    String render(Set<TulevaFund> funds) {
      return "\n%s [%s] %s %s: %s".formatted(emoji(), severity, checkType, codes(funds), message);
    }

    private String emoji() {
      return switch (severity) {
        case FAIL -> "🛑";
        case WARNING -> "⚠️";
        case NOT_RUN -> "⏸";
        case PASS -> "✅";
      };
    }
  }
}
