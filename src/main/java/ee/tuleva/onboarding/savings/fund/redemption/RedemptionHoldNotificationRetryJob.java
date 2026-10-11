package ee.tuleva.onboarding.savings.fund.redemption;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Profile("!staging")
public class RedemptionHoldNotificationRetryJob {

  private final RedemptionHoldService redemptionHoldService;

  @Scheduled(cron = "0 0 * * * *")
  @SchedulerLock(
      name = "RedemptionHoldNotificationRetryJob",
      lockAtMostFor = "50m",
      lockAtLeastFor = "1m")
  public void resendUnsentHoldNotifications() {
    redemptionHoldService.resendUnsentHoldNotifications();
  }
}
