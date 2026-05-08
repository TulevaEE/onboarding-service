package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportImportAlertJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  // 2025-01-15 = Wednesday; 10:40 Tallinn = 08:40 UTC (winter EET = UTC+2)
  private static final String WED_1040_UTC = "2025-01-15T08:40:00Z";
  // Saturday
  private static final String SAT_1040_UTC = "2025-01-18T08:40:00Z";
  // 2026-02-24 = Tuesday Independence Day
  private static final String INDEPENDENCE_DAY_1040_UTC = "2026-02-24T08:40:00Z";

  @Mock private InvestmentReportRepository reportRepository;
  @Mock private OperationsNotificationService notificationService;
  private final PublicHolidays publicHolidays = new PublicHolidays();

  @Test
  void alertFires_whenSebPositionsReportMissing() {
    var job = jobOn(WED_1040_UTC);
    LocalDate expectedDate = LocalDate.of(2025, 1, 14); // previous working day (Tuesday)
    given(reportRepository.existsByProviderAndReportTypeAndReportDate(SEB, POSITIONS, expectedDate))
        .willReturn(false);

    job.alertIfSebPositionsMissing();

    verify(notificationService)
        .sendMessage(contains("SEB positions report missing"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("2025-01-14"), eq(INVESTMENT));
  }

  @Test
  void silent_whenSebPositionsReportExists() {
    var job = jobOn(WED_1040_UTC);
    LocalDate expectedDate = LocalDate.of(2025, 1, 14);
    given(reportRepository.existsByProviderAndReportTypeAndReportDate(SEB, POSITIONS, expectedDate))
        .willReturn(true);

    job.alertIfSebPositionsMissing();

    verifyNoInteractions(notificationService);
  }

  @Test
  void silent_onNonWorkingDay() {
    var job = jobOn(SAT_1040_UTC);

    job.alertIfSebPositionsMissing();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(reportRepository);
  }

  @Test
  void silent_onWeekdayPublicHoliday() {
    var job = jobOn(INDEPENDENCE_DAY_1040_UTC);

    job.alertIfSebPositionsMissing();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(reportRepository);
  }

  private ReportImportAlertJob jobOn(String instant) {
    Clock clock = Clock.fixed(Instant.parse(instant), TALLINN);
    return new ReportImportAlertJob(reportRepository, notificationService, publicHolidays, clock);
  }
}
