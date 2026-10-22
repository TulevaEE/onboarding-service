package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static java.time.temporal.ChronoUnit.MINUTES;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class PaymentApprovalReminderJob {
  public static final String FROM_QUARTER_PAST_FOUR_CRON = "0 15/5 16 * * MON-FRI";
  public static final String UNTIL_SIX_CRON = "0 0/5 17 * * MON-FRI";
  static final String EVERY_FIVE_MINUTES_UNTIL_QUARTER_TO_FIVE_CRON = "0 15-45/5 16 * * MON-FRI";
  static final String EVERY_QUARTER_HOUR_AFTER_QUARTER_TO_FIVE_CRON = "0 0/15 17 * * MON-FRI";
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final BriefedBatches briefedBatches;
  private final PaymentApprovalReminder reminder;
  private final PaymentSettlementCheck settlementCheck;
  private final OperationsNotificationService notificationService;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  @Scheduled(cron = FROM_QUARTER_PAST_FOUR_CRON, zone = "Europe/Tallinn")
  @Scheduled(cron = UNTIL_SIX_CRON, zone = "Europe/Tallinn")
  @SchedulerLock(name = "PaymentApprovalReminderJob", lockAtMostFor = "4m", lockAtLeastFor = "1m")
  public void remind() {
    var now = clock.instant().atZone(TALLINN).truncatedTo(MINUTES);
    var today = now.toLocalDate();
    if (!publicHolidays.isWorkingDay(today)) {
      return;
    }

    var batch = briefedBatches.on(today);
    if (isAReminderRun(now)) {
      reminder
          .forPaymentsStillUnexecuted(batch, today)
          .ifPresent(message -> notificationService.sendMessage(message, SAVINGS));
    }

    var closing =
        isTheLastRunOfTheDay(now)
            ? settlementCheck.lastClosingOfTheDayFor(batch, today)
            : settlementCheck.closingFor(batch, today);
    closing.ifPresent(
        posted -> {
          notificationService.sendMessage(posted.message(), SAVINGS);
          settlementCheck.markPosted(posted);
        });
  }

  private static boolean isTheLastRunOfTheDay(ZonedDateTime now) {
    var nextRun = CronExpression.parse(UNTIL_SIX_CRON).next(now);
    return nextRun == null || !nextRun.toLocalDate().equals(now.toLocalDate());
  }

  private static boolean isAReminderRun(ZonedDateTime now) {
    return firesAt(EVERY_FIVE_MINUTES_UNTIL_QUARTER_TO_FIVE_CRON, now)
        || firesAt(EVERY_QUARTER_HOUR_AFTER_QUARTER_TO_FIVE_CRON, now);
  }

  private static boolean firesAt(String cron, ZonedDateTime now) {
    return now.equals(CronExpression.parse(cron).next(now.minusSeconds(1)));
  }
}
