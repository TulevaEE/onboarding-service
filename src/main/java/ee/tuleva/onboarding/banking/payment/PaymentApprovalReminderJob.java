package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class PaymentApprovalReminderJob {
  public static final String FROM_TWENTY_PAST_FOUR_CRON = "0 20/5 16 * * MON-FRI";
  public static final String UNTIL_SIX_CRON = "0 0/5 17 * * MON-FRI";
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final BriefedBatches briefedBatches;
  private final PaymentApprovalReminder reminder;
  private final PaymentSettlementCheck settlementCheck;
  private final OperationsNotificationService notificationService;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  @Scheduled(cron = FROM_TWENTY_PAST_FOUR_CRON, zone = "Europe/Tallinn")
  @Scheduled(cron = UNTIL_SIX_CRON, zone = "Europe/Tallinn")
  @SchedulerLock(name = "PaymentApprovalReminderJob", lockAtMostFor = "4m", lockAtLeastFor = "1m")
  public void remind() {
    var today = clock.instant().atZone(TALLINN).toLocalDate();
    if (!publicHolidays.isWorkingDay(today)) {
      return;
    }

    var batch = briefedBatches.on(today);
    var unexecuted = reminder.forPaymentsStillUnexecuted(batch, today);
    if (unexecuted.isPresent()) {
      notificationService.sendMessage(unexecuted.get(), SAVINGS);
      return;
    }

    settlementCheck
        .closingFor(batch, today)
        .ifPresent(
            closing -> {
              notificationService.sendMessage(closing.message(), SAVINGS);
              settlementCheck.markPosted(closing);
            });
  }
}
