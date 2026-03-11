package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.JobRunSchedule.*;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPositionImportService.ImportResult;
import ee.tuleva.onboarding.investment.position.parser.FundPositionParser;
import ee.tuleva.onboarding.investment.position.parser.SebFundPositionParser;
import ee.tuleva.onboarding.investment.position.parser.SwedbankFundPositionParser;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.report.ReportProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FundPositionImportJob {

  private static final int LOOKBACK_DAYS = 30;
  private static final List<ReportProvider> PROVIDERS = List.of(SWEDBANK, SEB);

  private final Map<ReportProvider, FundPositionParser> parsers;
  private final FundPositionImportService importService;
  private final InvestmentReportService reportService;
  private final FundPositionLedgerService fundPositionLedgerService;
  private final Clock clock;

  public FundPositionImportJob(
      SwedbankFundPositionParser swedbankParser,
      SebFundPositionParser sebParser,
      FundPositionImportService importService,
      InvestmentReportService reportService,
      FundPositionLedgerService fundPositionLedgerService,
      Clock clock) {
    this.parsers = Map.of(SWEDBANK, swedbankParser, SEB, sebParser);
    this.importService = importService;
    this.reportService = reportService;
    this.fundPositionLedgerService = fundPositionLedgerService;
    this.clock = clock;
  }

  @Schedules({
    @Scheduled(cron = PARSE_MORNING, zone = TIMEZONE),
    @Scheduled(cron = PARSE_AFTERNOON, zone = TIMEZONE)
  })
  @SchedulerLock(name = "FundPositionImportJob", lockAtMostFor = "55m", lockAtLeastFor = "4m")
  public void runImport() {
    LocalDate today = LocalDate.now(clock);
    IntStream.iterate(LOOKBACK_DAYS, i -> i >= 1, i -> i - 1)
        .mapToObj(today::minusDays)
        .forEach(
            date -> {
              for (ReportProvider provider : PROVIDERS) {
                try {
                  importForProviderAndDate(provider, date);
                } catch (Exception e) {
                  log.error("Fund position import failed: provider={}, date={}", provider, date, e);
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
        parser.parse(
            investmentReport.getRawData(),
            investmentReport.getReportDate(),
            investmentReport.getMetadata());
    log.info(
        "Parsed fund positions: provider={}, date={}, count={}", provider, date, positions.size());

    ImportResult result = importService.upsertPositions(positions);
    log.info(
        "Fund position import completed: provider={}, date={}, imported={}, updated={}, rowCount={}",
        provider,
        date,
        result.imported(),
        result.updated(),
        investmentReport.getRawData().size());

    List<TulevaFund> navFunds =
        positions.stream()
            .map(FundPosition::getFund)
            .filter(TulevaFund::hasNavCalculation)
            .distinct()
            .toList();

    navFunds.forEach(fund -> fundPositionLedgerService.recordPositionsToLedger(fund, date));

    if (result.updated() > 0) {
      Instant correctionTimestamp =
          Optional.ofNullable(investmentReport.getMetadata().get("s3LastModified"))
              .map(Object::toString)
              .map(Instant::parse)
              .orElse(clock.instant());
      navFunds.forEach(
          fund ->
              fundPositionLedgerService.correctPositionsInLedger(fund, date, correctionTimestamp));
    }
  }
}
