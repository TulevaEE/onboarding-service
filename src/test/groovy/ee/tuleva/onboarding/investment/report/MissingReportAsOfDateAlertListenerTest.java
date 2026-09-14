package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissingReportAsOfDateAlertListenerTest {

  @Mock private OperationsNotificationService notificationService;
  @Mock private InvestmentReportService reportService;

  @InjectMocks private MissingReportAsOfDateAlertListener listener;

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 1, 26);

  @Test
  void alertsThatTheReportWasNotUsed_whenTheMarkerIsAbsent() {
    givenLatestReportDate(REPORT_DATE);

    listener.onMissingReportAsOfDate(
        new MissingReportAsOfDateEvent(SEB, POSITIONS, REPORT_DATE, null));

    then(notificationService)
        .should()
        .sendMessage(contains("Raportis puudub „As of\" kuupäev."), eq(INVESTMENT));
  }

  // A header we cannot read is a different job from a header that is not there: the first needs the
  // format chased, the second needs the file resent. Telling the operator "missing" for an
  // unreadable date would send them to ask for the same file again.
  @Test
  void namesTheUnreadableValue_whenTheMarkerIsPresentButNotADate() {
    givenLatestReportDate(REPORT_DATE);

    listener.onMissingReportAsOfDate(
        new MissingReportAsOfDateEvent(SEB, POSITIONS, REPORT_DATE, "25.01.2026"));

    then(notificationService)
        .should()
        .sendMessage(contains("ei õnnestunud lugeda: \"25.01.2026\""), eq(INVESTMENT));
  }

  // Every producer re-scans a window, so without this one broken file would alert twice a day for
  // a fortnight and the backfill would alert once per broken historical report.
  @Test
  void staysSilent_whenTheRefusedReportIsNotTheLatest() {
    givenLatestReportDate(REPORT_DATE.plusDays(3));

    listener.onMissingReportAsOfDate(
        new MissingReportAsOfDateEvent(SEB, POSITIONS, REPORT_DATE, null));

    then(notificationService).should(never()).sendMessage(any(), any());
  }

  @Test
  void doesNotPropagateANotificationFailure() {
    givenLatestReportDate(REPORT_DATE);
    willThrow(new RuntimeException("slack down"))
        .given(notificationService)
        .sendMessage(any(), any());

    assertThatCode(
            () ->
                listener.onMissingReportAsOfDate(
                    new MissingReportAsOfDateEvent(SEB, POSITIONS, REPORT_DATE, null)))
        .doesNotThrowAnyException();
  }

  private void givenLatestReportDate(LocalDate date) {
    given(reportService.getLatestReport(SEB, POSITIONS))
        .willReturn(
            Optional.of(
                InvestmentReport.builder()
                    .provider(SEB)
                    .reportType(POSITIONS)
                    .reportDate(date)
                    .build()));
  }
}
