package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.time.format.DateTimeFormatter.BASIC_ISO_DATE;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.report.ReportProvider;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HealthCheckNotifier {

  private final OperationsNotificationService notificationService;
  private final HealthCheckEventRepository eventRepository;

  private static final DateTimeFormatter IMPORT_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Europe/Tallinn"));

  public boolean notify(
      ReportProvider provider,
      LocalDate date,
      List<HealthCheckResult> results,
      Map<TulevaFund, Instant> earlierImports) {
    try {
      var transitions = new ArrayList<Transition>();
      for (var result : results) {
        for (var checkType : HealthCheckType.values()) {
          var current = currentSeverity(result, checkType);
          var previous = previousSeverity(result.fund(), result.checkDate(), checkType);
          if (current == previous && !reasonChanged(result, checkType, current)) {
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
              provider, date, results, earlierImports, activeTransitions, clearedTransitions);
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
      HealthCheckResult result, HealthCheckType checkType, HealthCheckSeverity current) {
    if (current != NOT_RUN) {
      return false;
    }
    var rows =
        eventRepository.findTop2ByFundAndCheckDateAndCheckTypeOrderByCreatedAtDesc(
            result.fund(), result.checkDate(), checkType);
    if (rows.size() < 2) {
      return false;
    }
    return !messages(result, checkType).equals(storedMessages(rows.get(1)));
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
      ReportProvider provider,
      LocalDate date,
      List<HealthCheckResult> results,
      Map<TulevaFund, Instant> earlierImports,
      List<Transition> active,
      List<Transition> cleared) {
    var message = new StringBuilder();
    message.append(header(provider, date, results, earlierImports, active));
    message.append(fundStatuses(results, earlierImports));
    correctionSteps(provider, date, results).ifPresent(message::append);

    for (var transition : active) {
      for (var finding : transition.result.findings()) {
        if (finding.checkType() == transition.checkType && finding.severity() != PASS) {
          message.append(
              "\n%s [%s] %s %s: %s"
                  .formatted(
                      severityEmoji(finding.severity()),
                      finding.severity(),
                      finding.checkType(),
                      finding.fund(),
                      finding.message()));
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

  private String header(
      ReportProvider provider,
      LocalDate date,
      List<HealthCheckResult> results,
      Map<TulevaFund, Instant> earlierImports,
      List<Transition> active) {
    var blockedFunds = HealthCheckResult.blockedFunds(results);
    if (!blockedFunds.isEmpty()) {
      return "IMPORT BLOCKED: %s %s — %s, source files need to be fixed\n"
          .formatted(provider, date, whatBlockingLeft(blockedFunds, earlierImports));
    }
    if (active.stream().anyMatch(t -> t.current == WARNING)) {
      return "Import warning: %s %s\n".formatted(provider, date);
    }
    if (active.stream().anyMatch(t -> t.current == NOT_RUN)) {
      return "Import check could not run: %s %s\n".formatted(provider, date);
    }
    return "✅ Import cleared: %s %s\n".formatted(provider, date);
  }

  private static String whatBlockingLeft(
      Set<TulevaFund> blockedFunds, Map<TulevaFund, Instant> earlierImports) {
    var notImported = new ArrayList<String>();
    var stillInUse = new ArrayList<String>();
    for (var fund : blockedFunds) {
      var importedAt = earlierImports.get(fund);
      if (importedAt == null) {
        notImported.add(fund.getCode());
      } else {
        stillInUse.add(
            "%s positions from %s".formatted(fund.getCode(), IMPORT_TIME.format(importedAt)));
      }
    }
    var parts = new ArrayList<String>();
    if (!notImported.isEmpty()) {
      parts.add(String.join(", ", notImported) + " not imported");
    }
    if (!stillInUse.isEmpty()) {
      parts.add(String.join(", ", stillInUse) + " stay in use for NAV");
    }
    return String.join("; ", parts);
  }

  private String fundStatuses(
      List<HealthCheckResult> results, Map<TulevaFund, Instant> earlierImports) {
    return "Funds: %s\n"
        .formatted(
            results.stream()
                .map(result -> result.fund().getCode() + " " + status(result, earlierImports))
                .collect(joining(", ")));
  }

  private static String status(HealthCheckResult result, Map<TulevaFund, Instant> earlierImports) {
    if (result.hasFails() && earlierImports.containsKey(result.fund())) {
      return "blocked (earlier positions in use)";
    }
    return result.importStatus();
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

  private String severityEmoji(HealthCheckSeverity severity) {
    return switch (severity) {
      case FAIL -> "🛑";
      case WARNING -> "⚠️";
      case NOT_RUN -> "⏸";
      case PASS -> "✅";
    };
  }

  private record Transition(
      HealthCheckResult result,
      HealthCheckType checkType,
      HealthCheckSeverity current,
      HealthCheckSeverity previous) {}
}
