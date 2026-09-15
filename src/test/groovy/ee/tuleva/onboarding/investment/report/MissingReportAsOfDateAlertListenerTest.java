package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissingReportAsOfDateAlertListenerTest {

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 1, 26);

  @Mock private OperationsNotificationService notificationService;

  private final Clock clock =
      Clock.fixed(REPORT_DATE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

  private MissingReportAsOfDateAlertListener listener() {
    return new MissingReportAsOfDateAlertListener(notificationService, clock);
  }

  @Test
  void saysTheReportWasImportedAnyway_whenTheMarkerIsAbsent() {
    listener()
        .onMissingReportAsOfDate(new MissingReportAsOfDateEvent(SEB, POSITIONS, REPORT_DATE, null));

    then(notificationService)
        .should()
        .sendMessage(contains("Raport imporditi sellegipoolest"), eq(INVESTMENT));
  }

  @Test
  void saysTheHeaderWasNotFound_whenTheMarkerIsAbsent() {
    listener()
        .onMissingReportAsOfDate(new MissingReportAsOfDateEvent(SEB, POSITIONS, REPORT_DATE, null));

    then(notificationService)
        .should()
        .sendMessage(
            contains("ei leitud „As of“ välja – kas see puudub või on päise kuju muutunud"),
            eq(INVESTMENT));
  }

  @Test
  void namesTheUnreadableValue_whenTheMarkerIsPresentButNotADate() {
    listener()
        .onMissingReportAsOfDate(
            new MissingReportAsOfDateEvent(SEB, POSITIONS, REPORT_DATE, "25.01.2026"));

    then(notificationService)
        .should()
        .sendMessage(contains("ei õnnestunud lugeda: \"25.01.2026\""), eq(INVESTMENT));
  }

  @Test
  void staysSilent_whenTheReportIsOlderThanTheAlertWindow() {
    listener()
        .onMissingReportAsOfDate(
            new MissingReportAsOfDateEvent(SEB, POSITIONS, REPORT_DATE.minusDays(4), null));

    then(notificationService).should(never()).sendMessage(any(), any());
  }

  @Test
  void alerts_whenTheReportIsAtTheEdgeOfTheAlertWindow() {
    listener()
        .onMissingReportAsOfDate(
            new MissingReportAsOfDateEvent(SEB, POSITIONS, REPORT_DATE.minusDays(3), null));

    then(notificationService).should().sendMessage(any(), eq(INVESTMENT));
  }

  @Test
  void shortensAnUnreadableValueThatIsTooLongToQuote() {
    listener()
        .onMissingReportAsOfDate(
            new MissingReportAsOfDateEvent(SEB, POSITIONS, REPORT_DATE, "x".repeat(250)));

    then(notificationService)
        .should()
        .sendMessage(contains("\"" + "x".repeat(100) + "…\""), eq(INVESTMENT));
  }

  @Test
  void doesNotPropagateANotificationFailure() {
    willThrow(new RuntimeException("slack down"))
        .given(notificationService)
        .sendMessage(any(), any());

    assertThatCode(
            () ->
                listener()
                    .onMissingReportAsOfDate(
                        new MissingReportAsOfDateEvent(SEB, POSITIONS, REPORT_DATE, null)))
        .doesNotThrowAnyException();
  }
}
