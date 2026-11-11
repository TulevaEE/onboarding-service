package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.savings.TradeDay.Day.DAY_OFF;
import static ee.tuleva.onboarding.savings.TradeDay.Day.WORKING_DAY;
import static ee.tuleva.onboarding.savings.fund.nav.NavDeadline.SISEKORD_4_P_11_3_1;
import static ee.tuleva.onboarding.savings.fund.nav.NavDeadline.SISEKORD_4_P_11_3_1_PILLAR_2_NAV_DEADLINE;
import static ee.tuleva.onboarding.savings.fund.nav.NavDeadline.SISEKORD_4_P_11_3_2_PILLAR_3_AND_TKF_NAV_DEADLINE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.TradeDay.Day;
import ee.tuleva.onboarding.savings.fund.nav.NavDeadline.Report;
import ee.tuleva.onboarding.savings.fund.nav.NavDeadline.Status;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
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

  @Scheduled(cron = "0 35 11 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavEscalation_pillar2", lockAtMostFor = "5m", lockAtLeastFor = "1m")
  public void escalatePillar2IfStillMissing() {
    escalateIfStillMissing(List.of(TUK75, TUK00), SISEKORD_4_P_11_3_1_PILLAR_2_NAV_DEADLINE);
  }

  @Scheduled(cron = "0 35 15 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavEscalation_savingsPillar3", lockAtMostFor = "5m", lockAtLeastFor = "1m")
  public void escalateSavingsPillar3IfStillMissing() {
    escalateIfStillMissing(
        List.of(TKF100, TUV100), SISEKORD_4_P_11_3_2_PILLAR_3_AND_TKF_NAV_DEADLINE);
  }

  private void escalateIfStillMissing(List<TulevaFund> funds, LocalTime deadline) {
    List<TulevaFund> missing = lateFunds(funds);
    if (missing.isEmpty()) {
      return;
    }
    String missingCodes =
        missing.stream().map(TulevaFund::getCode).collect(Collectors.joining(", ", "[", "]"));
    String message =
        "URGENT: NAV still missing, deadline was "
            + deadline
            + " Tallinn: funds="
            + missingCodes
            + ", autoRetriesContinueUntil=17:45 Tallinn";
    log.error("{}", message);
    notificationService.sendMessage(message, INVESTMENT, ERROR);
  }

  private void alertIfMissing(
      List<TulevaFund> funds,
      String cronFireTime,
      String checkedAt,
      String autoRetriesContinueUntil) {
    List<TulevaFund> missing = lateFunds(funds);
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
    notificationService.sendMessage(message, INVESTMENT, ERROR);
  }

  private List<TulevaFund> lateFunds(List<TulevaFund> funds) {
    LocalDate today = ZonedDateTime.now(clock).withZoneSameInstant(TALLINN).toLocalDate();
    Day day = publicHolidays.isWorkingDay(today) ? WORKING_DAY : DAY_OFF;
    return funds.stream()
        .filter(TulevaFund::hasNavCalculation)
        .filter(fund -> SISEKORD_4_P_11_3_1.appliesTo(new Status(day, report(day, fund, today))))
        .toList();
  }

  private Report report(Day day, TulevaFund fund, LocalDate today) {
    if (day == DAY_OFF) {
      return Report.NOT_CHECKED;
    }
    return isNavMissingForToday(fund, today) ? Report.MISSING : Report.PUBLISHED;
  }

  private boolean isNavMissingForToday(TulevaFund fund, LocalDate today) {
    LocalDate expectedNavDate =
        NavCalculationService.expectedPositionReportDate(fund, today, publicHolidays);
    return !navReportRepository.existsPublishedByNavDateAndFundCode(
        expectedNavDate, fund.getCode());
  }
}
