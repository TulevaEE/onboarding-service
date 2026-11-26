package ee.tuleva.onboarding.savings.fund.pricecheck;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
class IssueRedemptionPriceCheckJob {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final IssueRedemptionPriceCheck check;
  private final OperationsNotificationService notificationService;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "IssueRedemptionPriceCheckJob",
      lockAtMostFor = "10m",
      lockAtLeastFor = "1m")
  void checkThePreviousWorkingDaysDeals() {
    LocalDate today = clock.instant().atZone(TALLINN).toLocalDate();
    if (!publicHolidays.isWorkingDay(today)) {
      return;
    }
    BookingDays days = BookingDays.before(today, publicHolidays);
    try {
      report(check.run(days));
    } catch (RuntimeException e) {
      log.error("Issue/redemption price check failed: bookingDays={}", days.describe(), e);
      notificationService.sendMessage(couldNotRun(days, e), INVESTMENT, ERROR);
    }
  }

  private void report(PriceCheckRun run) {
    run.roundingOnly()
        .forEach(
            price ->
                log.info(
                    "Issue/redemption price off its NAV only by rounding: transactionId={},"
                        + " realisedPrice={}, nav={}, deviation={}",
                    price.dealing().transactionId(),
                    price.realisedPrice(),
                    price.nav(),
                    price.deviation()));
    log.info(
        "Issue/redemption price check done: bookingDays={}, deals={}, reported={}",
        run.days().describe(),
        run.dealings().size(),
        run.reported().size());
    run.alert().ifPresent(alert -> notificationService.sendMessage(alert, INVESTMENT));
  }

  private static String couldNotRun(BookingDays days, RuntimeException e) {
    return "🛑 TKF100 issue/redemption price check could not run for deals booked %s: error=%s\n"
            .formatted(days.describe(), e.getMessage())
        + "Next: check those deals' prices against their NAV by hand and fix what stopped the"
        + " check";
  }
}
