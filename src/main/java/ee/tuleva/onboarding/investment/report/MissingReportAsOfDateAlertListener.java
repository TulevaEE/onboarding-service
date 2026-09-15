package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
class MissingReportAsOfDateAlertListener {

  private static final int ALERT_WINDOW_DAYS = 3;
  private static final int MAX_QUOTED_VALUE_LENGTH = 100;

  private final OperationsNotificationService notificationService;
  private final Clock clock;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onMissingReportAsOfDate(MissingReportAsOfDateEvent event) {
    try {
      if (isOlderThanTheAlertWindow(event)) {
        log.info(
            "Report with no usable As-of date is outside the alert window, skipping alert:"
                + " provider={}, reportType={}, reportDate={}",
            event.provider(),
            event.reportType(),
            event.reportDate());
        return;
      }
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

  private boolean isOlderThanTheAlertWindow(MissingReportAsOfDateEvent event) {
    return event.reportDate().isBefore(LocalDate.now(clock).minusDays(ALERT_WINDOW_DAYS));
  }

  private static String buildSlackMessage(MissingReportAsOfDateEvent event) {
    return """
        ⚠️ %s %s raportis puudub kasutatav „As of“ kuupäev – %s
        %s
        Raport imporditi sellegipoolest ja read on dateeritud faili nime kuupäeva järgi. \
        Kui faili nime kuupäev ei ole ridade äripäev, on NAV-i kuupäev ja tehingute \
        reported_date ühe päeva võrra nihkes.
        Palu SEB-lt uus raport ja lase neil see enne saatmist üle vaadata – kui päis on vigane, \
        võib ka ülejäänud sisu olla vigane. Uus fail imporditakse automaatselt."""
        .formatted(event.provider(), event.reportType(), event.reportDate(), cause(event));
  }

  private static String cause(MissingReportAsOfDateEvent event) {
    String unreadable = event.unreadableValue();
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
