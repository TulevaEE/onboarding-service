package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
public class FeeAccrualPositionSyncJob {

  private final FeeAccrualRepository feeAccrualRepository;
  private final FundPositionImportService fundPositionImportService;
  private final FundPositionRepository fundPositionRepository;
  private final Clock clock;

  @Scheduled(cron = "0 30 9 * * MON-FRI", zone = TIMEZONE)
  @SchedulerLock(name = "FeeAccrualPositionSyncJob", lockAtMostFor = "30m", lockAtLeastFor = "5m")
  void run() {
    log.info("Starting fee accrual position sync");
    int count = sync(7);
    log.info("Fee accrual position sync completed: positionsWritten={}", count);
  }

  @Scheduled(cron = "0 25 12 12 3 *", zone = TIMEZONE)
  @SchedulerLock(
      name = "FeeAccrualPositionBackfillJob",
      lockAtMostFor = "30m",
      lockAtLeastFor = "5m")
  void backfill() {
    log.info("Starting fee accrual position backfill");
    int count = sync(7);
    log.info("Fee accrual position backfill completed: positionsWritten={}", count);
  }

  int sync(int daysBack) {
    var today = LocalDate.now(clock);
    var cutoffDate = today.minusDays(daysBack);
    int total = 0;

    for (var fund : TulevaFund.values()) {
      var navDates =
          fundPositionRepository.findDistinctNavDatesByFund(fund).stream()
              .filter(date -> !date.isBefore(cutoffDate))
              .toList();

      for (var navDate : navDates) {
        var mgmtAccrual = feeAccrualRepository.getUnsettledAccrual(fund, MANAGEMENT, navDate);
        var depotAccrual = feeAccrualRepository.getUnsettledAccrual(fund, DEPOT, navDate);

        var positions =
            List.of(
                feeAccrualPosition(fund, navDate, "Management Fee Accrual", mgmtAccrual),
                feeAccrualPosition(fund, navDate, "Depot Fee Accrual", depotAccrual));

        fundPositionImportService.upsertPositions(positions);
        total += positions.size();
      }
    }

    return total;
  }

  private FundPosition feeAccrualPosition(
      TulevaFund fund, LocalDate navDate, String accountName, BigDecimal accrual) {
    return FundPosition.builder()
        .navDate(navDate)
        .fund(fund)
        .accountType(LIABILITY)
        .accountName(accountName)
        .quantity(BigDecimal.ONE)
        .marketPrice(accrual.negate())
        .currency("EUR")
        .marketValue(accrual.negate())
        .createdAt(clock.instant())
        .build();
  }
}
