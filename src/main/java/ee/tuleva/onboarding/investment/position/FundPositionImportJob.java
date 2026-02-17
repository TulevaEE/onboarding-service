package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.JobRunSchedule.*;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.PositionCalculation;
import ee.tuleva.onboarding.investment.calculation.PositionCalculationService;
import ee.tuleva.onboarding.investment.position.parser.FundPositionParser;
import ee.tuleva.onboarding.investment.position.parser.SebFundPositionParser;
import ee.tuleva.onboarding.investment.position.parser.SwedbankFundPositionParser;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.report.ReportProvider;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.NavPositionLedger;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
public class FundPositionImportJob {

  private static final int LOOKBACK_DAYS = 7;
  private static final List<ReportProvider> PROVIDERS = List.of(SWEDBANK, SEB);

  private final Map<ReportProvider, FundPositionParser> parsers;
  private final FundPositionImportService importService;
  private final InvestmentReportService reportService;
  private final PositionCalculationService positionCalculationService;
  private final FundPositionRepository fundPositionRepository;
  private final NavPositionLedger navPositionLedger;
  private final NavLedgerRepository navLedgerRepository;

  public FundPositionImportJob(
      SwedbankFundPositionParser swedbankParser,
      SebFundPositionParser sebParser,
      FundPositionImportService importService,
      InvestmentReportService reportService,
      PositionCalculationService positionCalculationService,
      FundPositionRepository fundPositionRepository,
      NavPositionLedger navPositionLedger,
      NavLedgerRepository navLedgerRepository) {
    this.parsers = Map.of(SWEDBANK, swedbankParser, SEB, sebParser);
    this.importService = importService;
    this.reportService = reportService;
    this.positionCalculationService = positionCalculationService;
    this.fundPositionRepository = fundPositionRepository;
    this.navPositionLedger = navPositionLedger;
    this.navLedgerRepository = navLedgerRepository;
  }

  @Schedules({
    @Scheduled(cron = PARSE_MORNING, zone = TIMEZONE),
    @Scheduled(cron = PARSE_AFTERNOON, zone = TIMEZONE)
  })
  @SchedulerLock(name = "FundPositionImportJob", lockAtMostFor = "55m", lockAtLeastFor = "4m")
  public void runImport() {
    LocalDate today = LocalDate.now();
    IntStream.rangeClosed(1, LOOKBACK_DAYS)
        .mapToObj(today::minusDays)
        .forEach(
            date -> {
              for (ReportProvider provider : PROVIDERS) {
                try {
                  importForProviderAndDate(provider, date);
                } catch (Exception e) {
                  log.error(
                      "Import failed, continuing with next: provider={}, date={}",
                      provider,
                      date,
                      e);
                }
              }
            });
  }

  public void importForProviderAndDate(ReportProvider provider, LocalDate date) {
    log.info("Starting fund position import: provider={}, date={}", provider, date);

    Optional<InvestmentReport> report = reportService.getReport(provider, POSITIONS, date);
    if (report.isEmpty()) {
      log.info("No positions report in database: provider={}, date={}", provider, date);
      return;
    }

    FundPositionParser parser = parsers.get(provider);
    if (parser == null) {
      log.warn("No parser configured for provider: provider={}", provider);
      return;
    }

    InvestmentReport investmentReport = report.get();
    List<FundPosition> positions =
        parser.parse(investmentReport.getRawData(), investmentReport.getReportDate());
    log.info(
        "Parsed fund positions: provider={}, date={}, count={}", provider, date, positions.size());

    int imported = importService.importPositions(positions);
    log.info(
        "Fund position import completed: provider={}, date={}, imported={}, rowCount={}",
        provider,
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
        calculateDelta(fund, SECURITIES_VALUE, calculateSecuritiesValue(fund, date));
    BigDecimal cashDelta =
        calculateDelta(fund, CASH_POSITION, calculatePositionValue(fund, date, CASH));
    BigDecimal receivablesDelta =
        calculateDelta(fund, TRADE_RECEIVABLES, calculatePositionValue(fund, date, RECEIVABLES));
    BigDecimal payablesDelta =
        calculateDelta(fund, TRADE_PAYABLES, calculatePositionValue(fund, date, LIABILITY));

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
