package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissingReportAsOfDateAlertListenerTest {

  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private MissingReportAsOfDateAlertListener listener;

  private static final MissingReportAsOfDateEvent EVENT =
      new MissingReportAsOfDateEvent(SEB, POSITIONS, LocalDate.of(2026, 1, 26));

  @Test
  void onMissingReportAsOfDate_tellsInvestmentTheReportWasNotUsed() {
    listener.onMissingReportAsOfDate(EVENT);

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⚠️ SEB POSITIONS raportis puudub „As of" kuupäev – 2026-01-26
            Raportit ei kasutatud: ilma selle kuupäevata ei saa ridu ajas paigutada ja faili enda \
            kuupäev on saatmispäev, mis on ühe pangapäeva hiljem.
            Palu SEB-lt uus raport ja lase neil see enne saatmist üle vaadata – kui päis on puudu, \
            võib ka ülejäänud sisu olla vigane. Uus fail imporditakse automaatselt.""",
            INVESTMENT);
  }

  @Test
  void onMissingReportAsOfDate_doesNotPropagateANotificationFailure() {
    willThrow(new RuntimeException("slack down"))
        .given(notificationService)
        .sendMessage(any(), any());

    assertThatCode(() -> listener.onMissingReportAsOfDate(EVENT)).doesNotThrowAnyException();
  }
}
