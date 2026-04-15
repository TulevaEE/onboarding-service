package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
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
public class NavAlertJob {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final NavReportRepository navReportRepository;
  private final OperationsNotificationService notificationService;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  @Scheduled(cron = "0 6 11 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavMissingAlert_pillar2", lockAtMostFor = "5m", lockAtLeastFor = "1m")
  public void alertPillar2IfMissing() {
    alertIfMissing(List.of(TUK75, TUK00), "11:00", "11:06", "17:45");
  }

  @Scheduled(cron = "0 31 15 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "NavMissingAlert_savingsPillar3",
      lockAtMostFor = "5m",
      lockAtLeastFor = "1m")
  public void alertSavingsPillar3IfMissing() {
    alertIfMissing(List.of(TKF100, TUV100), "15:20", "15:31", "17:45");
  }

  private void alertIfMissing(
      List<TulevaFund> funds,
      String cronFireTime,
      String checkedAt,
      String autoRetriesContinueUntil) {
    LocalDate today = ZonedDateTime.now(clock).withZoneSameInstant(TALLINN).toLocalDate();
    if (!publicHolidays.isWorkingDay(today)) {
      return;
    }
    List<TulevaFund> missing =
        funds.stream()
            .filter(TulevaFund::hasNavCalculation)
            .filter(fund -> isNavMissingForToday(fund, today))
            .toList();
    if (missing.isEmpty()) {
      return;
    }
    String missingCodes =
        missing.stream().map(TulevaFund::getCode).collect(Collectors.joining(", ", "[", "]"));
    String message =
        "NAV publish still missing: funds="
            + missingCodes
            + ", cronFireTime="
            + cronFireTime
            + ", checkedAt="
            + checkedAt
            + ", autoRetriesContinueUntil="
            + autoRetriesContinueUntil
            + " Tallinn";
    log.warn("{}", message);
    notificationService.sendMessage(message, INVESTMENT);
  }

  private boolean isNavMissingForToday(TulevaFund fund, LocalDate today) {
    LocalDate expectedNavDate =
        NavCalculationService.expectedPositionReportDate(fund, today, publicHolidays);
    return navReportRepository
        .findByNavDateAndFundCodeOrderById(expectedNavDate, fund.getCode())
        .isEmpty();
  }
}
