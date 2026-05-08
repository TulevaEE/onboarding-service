package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class ReportImportAlertJob {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final InvestmentReportRepository reportRepository;
  private final OperationsNotificationService notificationService;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  @Scheduled(cron = "0 40 10 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ReportImportAlert_sebPositions",
      lockAtMostFor = "5m",
      lockAtLeastFor = "1m")
  public void alertIfSebPositionsMissing() {
    LocalDate today = ZonedDateTime.now(clock).withZoneSameInstant(TALLINN).toLocalDate();
    if (!publicHolidays.isWorkingDay(today)) {
      return;
    }
    LocalDate expectedDate = publicHolidays.previousWorkingDay(today);
    boolean exists =
        reportRepository.existsByProviderAndReportTypeAndReportDate(SEB, POSITIONS, expectedDate);
    if (exists) {
      return;
    }
    String message =
        "SEB positions report missing: expectedDate="
            + expectedDate
            + ", checkedAt=10:40 Tallinn."
            + " NAV calculation at 11:00 will fail without it.";
    log.warn("{}", message);
    notificationService.sendMessage(message, INVESTMENT);
  }
}
