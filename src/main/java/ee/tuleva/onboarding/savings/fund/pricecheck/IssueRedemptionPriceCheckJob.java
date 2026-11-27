package ee.tuleva.onboarding.savings.fund.pricecheck;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
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
  void checkTheDealsBookedSinceTheLastCheck() {
    LocalDate today = clock.instant().atZone(TALLINN).toLocalDate();
    if (!publicHolidays.isWorkingDay(today)) {
      return;
    }
    Optional<PriceCheckRun> run;
    try {
      run = check.run(today);
    } catch (RuntimeException e) {
      log.error("Issue/redemption price check failed: runDay={}", today, e);
      notificationService.sendMessage(couldNotRun(today, e), INVESTMENT, ERROR);
      return;
    }
    run.ifPresent(this::report);
  }

  private void report(PriceCheckRun run) {
    run.roundingOnly().forEach(price -> log.info("{}", offOnlyByRounding(price)));
    run.reported()
        .forEach(
            price ->
                log.warn(
                    "Issue/redemption price not at its NAV: transactionId={}, verdict={}",
                    price.dealing().transactionId(),
                    price.verdict(run.deviationThreshold())));
    run.alert().ifPresent(alert -> notificationService.sendMessage(alert, INVESTMENT));
    check.recordChecked(run, clock.instant());
    log.info(
        "Issue/redemption price check done: bookingDays={}, deals={}, reported={}",
        run.days().describe(),
        run.dealings().size(),
        run.reported().size());
  }

  private static String offOnlyByRounding(DealingPrice price) {
    return "Issue/redemption price off its NAV only by rounding: transactionId=%s, realisedPrice=%s, nav=%s, deviation=%s"
        .formatted(
            price.dealing().transactionId(), price.realisedPrice(), price.nav(), price.deviation());
  }

  static String couldNotRun(LocalDate runDay, RuntimeException e) {
    return "🛑 TKF100 issue/redemption price check could not run on %s: error=%s\n"
            .formatted(runDay, e.getMessage())
        + "Next: fix what stopped it; the next run checks every deal booked since the last"
        + " checked day";
  }
}
