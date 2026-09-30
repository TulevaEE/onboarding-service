package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class MissingReportAsOfDateAlertListener {

  private static final Set<ReportType> REPORT_TYPES_DATED_BY_THEIR_AS_OF_HEADER =
      Set.of(POSITIONS, PENDING_TRANSACTIONS);
  private static final int MAX_QUOTED_VALUE_LENGTH = 100;

  private final InvestmentReportService reportService;
  private final OperationsNotificationService notificationService;
  private final Clock clock;

  @EventListener
  @Order(HIGHEST_PRECEDENCE)
  public void onReportImportCompleted(ReportImportCompleted event) {
    if (event.provider() != SEB
        || !REPORT_TYPES_DATED_BY_THEIR_AS_OF_HEADER.contains(event.reportType())) {
      return;
    }
    try {
      reportService
          .getReport(event.provider(), event.reportType(), event.reportDate())
          .filter(report -> SebReportHeaders.asOfDate(report) == null)
          .ifPresent(this::alertUnlessOlderThanTheImportLooksBack);
    } catch (RuntimeException e) {
      log.error(
          "Failed to send missing report As-of date alert: provider={}, reportType={},"
              + " reportDate={}",
          event.provider(),
          event.reportType(),
          event.reportDate(),
          e);
    }
  }

  private void alertUnlessOlderThanTheImportLooksBack(InvestmentReport report) {
    if (isOlderThanTheImportLooksBack(report.getReportDate())) {
      log.info(
          "Report with no usable As-of date is older than the import looks back, skipping alert:"
              + " provider={}, reportType={}, reportDate={}",
          report.getProvider(),
          report.getReportType(),
          report.getReportDate());
      return;
    }
    notificationService.sendMessage(buildSlackMessage(report), INVESTMENT);
  }

  private boolean isOlderThanTheImportLooksBack(LocalDate reportDate) {
    return reportDate.isBefore(LocalDate.now(clock).minusDays(ReportImportJob.LOOKBACK_DAYS));
  }

  private static String buildSlackMessage(InvestmentReport report) {
    return """
        🔴 %s %s raportis puudub kasutatav „As of“ kuupäev – %s
        %s
        Raport imporditi sellegipoolest ja read on dateeritud faili nime kuupäeva järgi. \
        Kui faili nime kuupäev ei ole ridade äripäev, on NAV-i kuupäev ja tehingute \
        reported_date ühe päeva võrra nihkes.
        Helista SEB-le kohe ja palu uus raport, mis on enne saatmist üle vaadatud – kui päis on \
        vigane, võib ka ülejäänud sisu olla vigane. Kui selle kuupäeva NAV on veel arvutamata, \
        peab parandatud fail jõudma enne NAV-arvutust. Uus fail imporditakse automaatselt. \
        <!channel>"""
        .formatted(
            report.getProvider(), report.getReportType(), report.getReportDate(), cause(report));
  }

  private static String cause(InvestmentReport report) {
    String unreadable =
        SebReportHeaders.unreadableAsOfValue(report.getMetadata(), report.getRawData());
    if (unreadable == null) {
      return "Raporti päise esimesest viiest reast ei leitud „As of“ välja – kas see puudub või on"
          + " päise kuju muutunud.";
    }
    return "Raporti „As of“ kuupäeva ei õnnestunud lugeda: \"%s\" – oodatud vorming on AAAA-KK-PP."
        .formatted(shortened(unreadable));
  }

  private static String shortened(String value) {
    return value.length() <= MAX_QUOTED_VALUE_LENGTH
        ? value
        : value.substring(0, MAX_QUOTED_VALUE_LENGTH) + "…";
  }
}
