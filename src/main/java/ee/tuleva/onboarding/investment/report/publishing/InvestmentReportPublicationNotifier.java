package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class InvestmentReportPublicationNotifier {

  private static final int SISEKORD_2_P_7_3_2_PUBLISHED_BY_DAY_OF_THE_NEXT_MONTH = 15;
  private static final String LINE_INDENT = "  ";

  private final OperationsNotificationService notificationService;

  void notify(YearMonth month, List<ReportPublication> publications) {
    try {
      var severity = severity(publications);
      notificationService.sendMessage(message(month, publications, severity), INVESTMENT, severity);
    } catch (Exception e) {
      log.error("Failed to send investment report publication notification: month={}", month, e);
    }
  }

  private static String message(
      YearMonth month, List<ReportPublication> publications, Severity severity) {
    return Stream.concat(
            Stream.of(headline(month, severity)),
            publications.stream().map(publication -> LINE_INDENT + publication.line()))
        .collect(joining("\n"));
  }

  private static String headline(YearMonth month, Severity severity) {
    return switch (severity) {
      case INFO -> "✅ Investment reports for %s are published on tuleva.ee".formatted(month);
      case ERROR ->
          "🔴 Investment reports for %s are not all published on tuleva.ee — due %s"
              .formatted(month, deadline(month));
      case WARNING ->
          "⏸ Could not confirm the investment reports for %s on tuleva.ee — due %s"
              .formatted(month, deadline(month));
    };
  }

  private static Severity severity(List<ReportPublication> publications) {
    if (publications.stream().anyMatch(ReportPublication::isMissing)) {
      return ERROR;
    }
    return publications.stream().allMatch(ReportPublication::isPublished) ? INFO : WARNING;
  }

  private static String deadline(YearMonth month) {
    return month
        .plusMonths(1)
        .atDay(SISEKORD_2_P_7_3_2_PUBLISHED_BY_DAY_OF_THE_NEXT_MONTH)
        .toString();
  }
}
