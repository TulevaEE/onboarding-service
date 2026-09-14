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

  @EventListener
  public void onMissingReportAsOfDate(MissingReportAsOfDateEvent event) {
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

  private static String buildSlackMessage(MissingReportAsOfDateEvent event) {
    return """
        ⚠️ %s %s raportis puudub „As of" kuupäev – %s
        Raportit ei kasutatud: ilma selle kuupäevata ei saa ridu ajas paigutada ja faili enda \
        kuupäev on saatmispäev, mis on ühe pangapäeva hiljem.
        Palu SEB-lt uus raport ja lase neil see enne saatmist üle vaadata – kui päis on puudu, \
        võib ka ülejäänud sisu olla vigane. Uus fail imporditakse automaatselt."""
        .formatted(event.provider(), event.reportType(), event.reportDate());
  }
}
