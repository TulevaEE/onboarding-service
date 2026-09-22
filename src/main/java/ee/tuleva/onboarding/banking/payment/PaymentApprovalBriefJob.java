package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.banking.check.payment.PaymentCheckEvent;
import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
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
public class PaymentApprovalBriefJob {
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final PaymentApprovalBriefService briefService;
  private final PaymentApprovalBriefFormatter formatter;
  private final PaymentCheckService paymentCheckService;
  private final OperationsNotificationService notificationService;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  @Scheduled(cron = "0 20 16 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "PaymentApprovalBriefJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void postBrief() {
    var today = clock.instant().atZone(TALLINN).toLocalDate();
    if (!publicHolidays.isWorkingDay(today)) {
      return;
    }

    var holds =
        paymentCheckService.holdsOn(today).stream().map(PaymentApprovalBriefJob::toHold).toList();
    var brief = briefService.build(today, holds);

    notificationService.sendMessage(formatter.format(brief), INVESTMENT);
  }

  private static PaymentApprovalBriefService.PaymentHold toHold(PaymentCheckEvent event) {
    return new PaymentApprovalBriefService.PaymentHold(
        event.getCheckType().name(), event.getCheckType() + ": " + event.getDetail());
  }
}
