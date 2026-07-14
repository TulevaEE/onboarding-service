package ee.tuleva.onboarding.fund.fees;

import static ee.tuleva.onboarding.fund.fees.FundFeeUpdater.FeeField.MANAGEMENT_FEE;
import static ee.tuleva.onboarding.fund.fees.FundFeeUpdater.FeeField.ONGOING_CHARGES;

import ee.tuleva.onboarding.fund.fees.FundFeeUpdater.FeeField;
import java.util.List;
import java.util.function.Supplier;
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
public class FundFeeSyncJob {

  private static final String TIMEZONE = "Europe/Tallinn";

  private final PensionikeskusDailyStatisticsClient dailyStatisticsClient;
  private final PensionikeskusFeeComparisonClient feeComparisonClient;
  private final FundFeeUpdater fundFeeUpdater;

  @Scheduled(cron = "0 30 8 * * MON-FRI", zone = TIMEZONE)
  @SchedulerLock(name = "FundFeeSyncJob_syncFees", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void syncFees() {
    syncUnit(2, ONGOING_CHARGES, () -> dailyStatisticsClient.fetchOngoingCharges(2));
    syncUnit(3, ONGOING_CHARGES, () -> dailyStatisticsClient.fetchOngoingCharges(3));
    syncUnit(2, MANAGEMENT_FEE, () -> feeComparisonClient.fetchManagementFees(2));
    syncUnit(3, MANAGEMENT_FEE, () -> feeComparisonClient.fetchManagementFees(3));
  }

  private void syncUnit(int pillar, FeeField field, Supplier<List<PensionikeskusFeeRow>> fetcher) {
    try {
      List<PensionikeskusFeeRow> rows = fetcher.get();
      fundFeeUpdater.update(pillar, rows, field);
    } catch (Exception e) {
      log.error("Failed to sync fund fees: pillar={}, field={}", pillar, field, e);
    }
  }
}
