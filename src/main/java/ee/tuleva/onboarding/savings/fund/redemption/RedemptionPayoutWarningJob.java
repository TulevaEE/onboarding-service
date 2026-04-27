package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.WITHDRAWALS;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
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
public class RedemptionPayoutWarningJob {

  private static final BigDecimal PAYOUT_WARNING_THRESHOLD = new BigDecimal("40000");
  private static final LocalTime CUTOFF_TIME = LocalTime.of(16, 0, 0);
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final Clock clock;
  private final PublicHolidays publicHolidays;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = "0 0 13 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "RedemptionPayoutWarningJob", lockAtMostFor = "5m", lockAtLeastFor = "1m")
  public void checkPayoutThreshold() {
    LocalDate today = clock.instant().atZone(TALLINN).toLocalDate();
    if (!publicHolidays.isWorkingDay(today)) {
      return;
    }

    LocalDate previousWorkingDay = publicHolidays.previousWorkingDay(today);
    Instant cutoff = ZonedDateTime.of(previousWorkingDay, CUTOFF_TIME, TALLINN).toInstant();

    List<RedemptionRequest> requests =
        redemptionRequestRepository.findByStatusAndRequestedAtBefore(VERIFIED, cutoff);

    if (requests.isEmpty()) {
      return;
    }

    BigDecimal totalAmount =
        requests.stream()
            .map(RedemptionRequest::getRequestedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalAmount.compareTo(PAYOUT_WARNING_THRESHOLD) > 0) {
      String message =
          "WARNING: TKF100 estimated redemption payouts today: totalAmount=%s EUR, requests=%d. WITHDRAWAL_EUR credit limit increase may be needed."
              .formatted(totalAmount, requests.size());
      log.warn("{}", message);
      notificationService.sendMessage(message, WITHDRAWALS);
    }
  }
}
