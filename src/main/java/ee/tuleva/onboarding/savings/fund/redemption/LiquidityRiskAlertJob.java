package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.WITHDRAWALS;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionCutoff.TALLINN;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
public class LiquidityRiskAlertJob {

  private static final BigDecimal LIQUIDITY_THRESHOLD_PERCENT = new BigDecimal("0.01");

  private final Clock clock;
  private final PublicHolidays publicHolidays;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final FundValueRepository fundValueRepository;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = "0 5 16 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "LiquidityRiskAlertJob", lockAtMostFor = "5m", lockAtLeastFor = "1m")
  public void checkLiquidityRisk() {
    LocalDate today = clock.instant().atZone(TALLINN).toLocalDate();
    if (!publicHolidays.isWorkingDay(today)) {
      return;
    }

    Instant cutoff = RedemptionCutoff.cutoffInstant(today);
    List<RedemptionRequest> requests =
        redemptionRequestRepository.findByStatusAndRequestedAtBefore(VERIFIED, cutoff);

    if (requests.isEmpty()) {
      return;
    }

    BigDecimal totalAmount =
        requests.stream()
            .map(RedemptionRequest::getRequestedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    Optional<FundValue> aum = fundValueRepository.findLastValueForFund(TKF100.getAumKey());
    if (aum.isEmpty()) {
      log.warn("AUM not available for TKF100, skipping liquidity risk check");
      return;
    }

    BigDecimal aumValue = aum.get().value();
    if (aumValue.signum() <= 0) {
      log.warn("AUM for TKF100 is not positive: value={}, skipping liquidity risk check", aumValue);
      return;
    }
    BigDecimal threshold = aumValue.multiply(LIQUIDITY_THRESHOLD_PERCENT);

    if (totalAmount.compareTo(threshold) > 0) {
      BigDecimal percentage =
          totalAmount.multiply(new BigDecimal("100")).divide(aumValue, 2, RoundingMode.HALF_UP);
      String message =
          "LIQUIDITY WARNING: TKF100 pending withdrawals totalAmount=%s EUR (%s%% of AUM), requests=%d, AUM=%s EUR. Exceeds 1%% threshold."
              .formatted(totalAmount, percentage, requests.size(), aumValue);
      log.info("{}", message);
      notificationService.sendMessage(message, WITHDRAWALS);
    }
  }
}
