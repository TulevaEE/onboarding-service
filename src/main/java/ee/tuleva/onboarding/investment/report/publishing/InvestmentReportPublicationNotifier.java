package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.NotChecked;
import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.NotLinked;
import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.Outdated;
import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.Published;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import java.time.LocalDate;
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

  private enum Verdict {
    PUBLISHED,
    MISSING,
    UNCONFIRMED
  }

  private final OperationsNotificationService notificationService;

  void notify(YearMonth month, List<ReportPublication> publications, LocalDate today) {
    try {
      var verdict = verdict(publications);
      var overdue = !today.isBefore(deadline(month));
      notificationService.sendMessage(
          message(month, publications, verdict, overdue), INVESTMENT, severity(verdict, overdue));
    } catch (Exception e) {
      log.error("Failed to send investment report publication notification: month={}", month, e);
    }
  }

  private static Verdict verdict(List<ReportPublication> publications) {
    if (publications.stream().anyMatch(ReportPublication::isMissing)) {
      return Verdict.MISSING;
    }
    return ReportPublication.allPublished(publications) ? Verdict.PUBLISHED : Verdict.UNCONFIRMED;
  }

  private static Severity severity(Verdict verdict, boolean overdue) {
    if (verdict == Verdict.PUBLISHED) {
      return INFO;
    }
    return overdue ? ERROR : WARNING;
  }

  private static String message(
      YearMonth month, List<ReportPublication> publications, Verdict verdict, boolean overdue) {
    return Stream.concat(
            Stream.of(headline(month, verdict, overdue)),
            publications.stream()
                .map(
                    publication ->
                        LINE_INDENT + icon(publication, overdue) + " " + publication.describe()))
        .collect(joining("\n"));
  }

  private static String headline(YearMonth month, Verdict verdict, boolean overdue) {
    return switch (verdict) {
      case PUBLISHED -> "✅ Investment reports for %s are published on tuleva.ee".formatted(month);
      case MISSING ->
          "%s Investment reports for %s are not all published on tuleva.ee — due %s"
              .formatted(alarm(overdue), month, deadline(month));
      case UNCONFIRMED ->
          "%s Could not confirm the investment reports for %s on tuleva.ee — due %s"
              .formatted(alarm(overdue), month, deadline(month));
    };
  }

  private static String icon(ReportPublication publication, boolean overdue) {
    return switch (publication) {
      case Published _ -> "✅";
      case NotChecked _ -> "⏸";
      case Outdated _, NotLinked _ -> alarm(overdue);
    };
  }

  private static String alarm(boolean overdue) {
    return overdue ? "🔴" : "⚠️";
  }

  private static LocalDate deadline(YearMonth month) {
    return month.plusMonths(1).atDay(SISEKORD_2_P_7_3_2_PUBLISHED_BY_DAY_OF_THE_NEXT_MONTH);
  }
}
