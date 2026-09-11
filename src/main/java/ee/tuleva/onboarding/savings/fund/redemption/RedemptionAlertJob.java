package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionCutoff.TALLINN;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.RedemptionAlertThresholds;
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
public class RedemptionAlertJob {

  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  private final Clock clock;
  private final PublicHolidays publicHolidays;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final FundValueQueries fundValueQueries;
  private final OperationsNotificationService notificationService;
  private final RedemptionAlertThresholds redemptionAlertThresholds;

  @Scheduled(cron = "0 5 16 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "RedemptionAlertJob", lockAtMostFor = "5m", lockAtLeastFor = "1m")
  public void checkRedemptionAlerts() {
    LocalDate today = clock.instant().atZone(TALLINN).toLocalDate();
    if (!publicHolidays.isWorkingDay(today)) {
      return;
    }

    Instant cutoff = RedemptionCutoff.cutoffInstant(today);
    List<RedemptionRequest> requests =
        redemptionRequestRepository.findAcceptedBefore(VERIFIED, cutoff);

    if (requests.isEmpty()) {
      return;
    }

    BigDecimal totalAmount =
        requests.stream()
            .map(RedemptionRequest::getRequestedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    checkPayoutThreshold(today, totalAmount, requests.size());
    checkLiquidityRisk(today, totalAmount, requests.size());
  }

  private void checkPayoutThreshold(LocalDate today, BigDecimal totalAmount, int requestCount) {
    Optional<BigDecimal> payoutThreshold =
        redemptionAlertThresholds.redemptionPayoutWarningThreshold(TKF100, today);
    if (payoutThreshold.isEmpty()) {
      log.warn(
          "Redemption payout warning threshold not configured, skipping payout check: fund={}, asOf={}",
          TKF100,
          today);
      return;
    }

    if (totalAmount.compareTo(payoutThreshold.get()) > 0) {
      String message =
          "PAYOUT WARNING: TKF100 pending redemption payouts: totalAmount=%s EUR, requests=%d. WITHDRAWAL_EUR credit limit increase may be needed."
              .formatted(totalAmount, requestCount);
      log.warn(message);
      notificationService.sendMessage(message, INVESTMENT, ERROR);
    }
  }

  private void checkLiquidityRisk(LocalDate today, BigDecimal totalAmount, int requestCount) {
    Optional<BigDecimal> shareOfAum =
        redemptionAlertThresholds.redemptionLiquidityWarningShareOfAum(TKF100, today);
    if (shareOfAum.isEmpty()) {
      log.warn(
          "Redemption liquidity warning share of AUM not configured, skipping liquidity risk check: fund={}, asOf={}",
          TKF100,
          today);
      return;
    }

    Optional<FundValue> aum = fundValueQueries.findLastValueForFund(TKF100.getAumKey());
    if (aum.isEmpty()) {
      log.warn("AUM not available for TKF100, skipping liquidity risk check");
      return;
    }

    BigDecimal aumValue = aum.get().value();
    if (aumValue.signum() <= 0) {
      log.warn("AUM for TKF100 is not positive: value={}, skipping liquidity risk check", aumValue);
      return;
    }

    BigDecimal share = shareOfAum.get();
    BigDecimal threshold = aumValue.multiply(share);
    if (totalAmount.compareTo(threshold) > 0) {
      BigDecimal percentage =
          totalAmount.multiply(ONE_HUNDRED).divide(aumValue, 2, RoundingMode.HALF_UP);
      String message =
          "LIQUIDITY WARNING: TKF100 pending withdrawals totalAmount=%s EUR (%s%% of AUM), requests=%d, AUM=%s EUR. Exceeds %s%% of AUM."
              .formatted(totalAmount, percentage, requestCount, aumValue, asPercent(share));
      log.warn(message);
      notificationService.sendMessage(message, INVESTMENT, ERROR);
    }
  }

  private static String asPercent(BigDecimal share) {
    return share.multiply(ONE_HUNDRED).stripTrailingZeros().toPlainString();
  }
}
