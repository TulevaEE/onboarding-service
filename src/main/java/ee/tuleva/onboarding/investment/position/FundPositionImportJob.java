package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.JobRunSchedule.*;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.PositionCalculation;
import ee.tuleva.onboarding.investment.calculation.PositionCalculationService;
import ee.tuleva.onboarding.investment.position.parser.FundPositionParser;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.report.ReportProvider;
import ee.tuleva.onboarding.investment.report.ReportType;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.NavPositionLedger;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class FundPositionImportJob {

  private static final int LOOKBACK_DAYS = 7;
  private static final ReportProvider PROVIDER = ReportProvider.SWEDBANK;
  private static final ReportType REPORT_TYPE = ReportType.POSITIONS;

  private final FundPositionParser parser;
  private final FundPositionImportService importService;
  private final InvestmentReportService reportService;
  private final PositionCalculationService positionCalculationService;
  private final FundPositionRepository fundPositionRepository;
  private final NavPositionLedger navPositionLedger;
  private final NavLedgerRepository navLedgerRepository;

  @Schedules({
    @Scheduled(cron = PARSE_MORNING, zone = TIMEZONE),
    @Scheduled(cron = PARSE_AFTERNOON, zone = TIMEZONE)
  })
  @SchedulerLock(name = "FundPositionImportJob", lockAtMostFor = "55m", lockAtLeastFor = "5m")
  public void runImport() {
    LocalDate today = LocalDate.now();
    IntStream.rangeClosed(1, LOOKBACK_DAYS)
        .mapToObj(today::minusDays)
        .forEach(
            date -> {
              try {
                importForDate(date);
              } catch (Exception e) {
                log.error("Import failed, continuing with next date: date={}", date, e);
              }
            });
  }

  public void importForDate(LocalDate date) {
    log.info("Starting fund position import: provider={}, date={}", PROVIDER, date);

    Optional<InvestmentReport> report = reportService.getReport(PROVIDER, REPORT_TYPE, date);
    if (report.isEmpty()) {
      log.info("No positions report in database: provider={}, date={}", PROVIDER, date);
      return;
    }

    List<FundPosition> positions = parser.parse(report.get().getRawData());
    log.info(
        "Parsed fund positions: provider={}, date={}, count={}", PROVIDER, date, positions.size());

    int imported = importService.importPositions(positions);
    log.info(
        "Fund position import completed: provider={}, date={}, imported={}, rowCount={}",
        PROVIDER,
        date,
        imported,
        report.get().getRawData().size());

    positions.stream()
        .map(FundPosition::getFund)
        .distinct()
        .forEach(fund -> recordPositionsToLedger(fund, date));
  }

  private void recordPositionsToLedger(TulevaFund fund, LocalDate date) {
    BigDecimal securitiesDelta =
        calculateDelta(fund, SystemAccount.SECURITIES_VALUE, calculateSecuritiesValue(fund, date));
    BigDecimal cashDelta =
        calculateDelta(fund, SystemAccount.CASH_POSITION, calculatePositionValue(fund, date, CASH));
    BigDecimal receivablesDelta =
        calculateDelta(
            fund, SystemAccount.TRADE_RECEIVABLES, calculatePositionValue(fund, date, RECEIVABLES));
    BigDecimal payablesDelta =
        calculateDelta(
            fund, SystemAccount.TRADE_PAYABLES, calculatePositionValue(fund, date, LIABILITY));

    navPositionLedger.recordPositions(
        fund.name(), date, securitiesDelta, cashDelta, receivablesDelta, payablesDelta);
    log.info(
        "Recorded position deltas to ledger: fund={}, date={}, securities={}, cash={}, receivables={}, payables={}",
        fund,
        date,
        securitiesDelta,
        cashDelta,
        receivablesDelta,
        payablesDelta);
  }

  private BigDecimal calculateDelta(TulevaFund fund, SystemAccount account, BigDecimal newValue) {
    BigDecimal currentBalance =
        navLedgerRepository.getPositionBalanceByFund(account.name(), fund.name());
    return newValue.subtract(currentBalance);
  }

  private BigDecimal calculateSecuritiesValue(TulevaFund fund, LocalDate date) {
    return positionCalculationService.calculate(fund, date).stream()
        .map(PositionCalculation::calculatedMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal calculatePositionValue(TulevaFund fund, LocalDate date, AccountType type) {
    return fundPositionRepository
        .findByReportingDateAndFundAndAccountType(date, fund, type)
        .stream()
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }
}
