package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class MissingReportAsOfDateAlertListener {

  private final OperationsNotificationService notificationService;
  private final InvestmentReportService reportService;

  @EventListener
  public void onMissingReportAsOfDate(MissingReportAsOfDateEvent event) {
    if (!isLatestReport(event)) {
      log.info(
          "Refused report is not the latest, skipping alert: provider={}, reportType={},"
              + " reportDate={}",
          event.provider(),
          event.reportType(),
          event.reportDate());
      return;
    }
    try {
      notificationService.sendMessage(buildSlackMessage(event), INVESTMENT);
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

  private boolean isLatestReport(MissingReportAsOfDateEvent event) {
    return reportService
        .getLatestReport(event.provider(), event.reportType())
        .map(InvestmentReport::getReportDate)
        .map(latest -> !event.reportDate().isBefore(latest))
        .orElse(true);
  }

  private static String buildSlackMessage(MissingReportAsOfDateEvent event) {
    return """
        ⚠️ %s %s raportit ei kasutatud – %s
        %s
        Ilma selle kuupäevata ei saa ridu ajas paigutada ja faili enda kuupäev on saatmispäev, \
        mis on ühe pangapäeva hiljem.
        Palu SEB-lt uus raport ja lase neil see enne saatmist üle vaadata – kui päis on vigane, \
        võib ka ülejäänud sisu olla vigane. Uus fail imporditakse automaatselt."""
        .formatted(event.provider(), event.reportType(), event.reportDate(), cause(event));
  }

  private static String cause(MissingReportAsOfDateEvent event) {
    String unreadable = event.unreadableValue();
    if (unreadable == null) {
      return "Raportis puudub „As of\" kuupäev.";
    }
    return "Raporti „As of\" kuupäeva ei õnnestunud lugeda: \"%s\" – oodatud vorming on AAAA-KK-PP."
        .formatted(unreadable);
  }
}
