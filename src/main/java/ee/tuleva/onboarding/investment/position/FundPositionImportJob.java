package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.event.PipelineStep.HEALTH_CHECK;
import static ee.tuleva.onboarding.investment.event.PipelineStep.POSITION_IMPORT;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.check.health.HealthCheckNotifier;
import ee.tuleva.onboarding.investment.check.health.HealthCheckResult;
import ee.tuleva.onboarding.investment.check.health.HealthCheckService;
import ee.tuleva.onboarding.investment.event.FundPositionsImported;
import ee.tuleva.onboarding.investment.event.PipelineTracker;
import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.event.RunFundPositionImportRequested;
import ee.tuleva.onboarding.investment.position.FundPositionImportService.ImportResult;
import ee.tuleva.onboarding.investment.position.parser.FundPositionParser;
import ee.tuleva.onboarding.investment.position.parser.SebFundPositionParser;
import ee.tuleva.onboarding.investment.position.parser.SwedbankFundPositionParser;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.report.ReportProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FundPositionImportJob {

  private static final int LOOKBACK_DAYS = 14;
  private static final List<ReportProvider> PROVIDERS = List.of(SWEDBANK, SEB);

  private final Map<ReportProvider, FundPositionParser> parsers;
  private final FundPositionImportService importService;
  private final InvestmentReportService reportService;
  private final FundPositionLedgerService fundPositionLedgerService;
  private final HealthCheckService healthCheckService;
  private final HealthCheckNotifier healthCheckNotifier;
  private final Clock clock;
  private final ApplicationEventPublisher eventPublisher;
  private final PipelineTracker pipelineTracker;

  public FundPositionImportJob(
      SwedbankFundPositionParser swedbankParser,
      SebFundPositionParser sebParser,
      FundPositionImportService importService,
      InvestmentReportService reportService,
      FundPositionLedgerService fundPositionLedgerService,
      HealthCheckService healthCheckService,
      HealthCheckNotifier healthCheckNotifier,
      Clock clock,
      ApplicationEventPublisher eventPublisher,
      PipelineTracker pipelineTracker) {
    this.parsers = Map.of(SWEDBANK, swedbankParser, SEB, sebParser);
    this.importService = importService;
    this.reportService = reportService;
    this.fundPositionLedgerService = fundPositionLedgerService;
    this.healthCheckService = healthCheckService;
    this.healthCheckNotifier = healthCheckNotifier;
    this.clock = clock;
    this.eventPublisher = eventPublisher;
    this.pipelineTracker = pipelineTracker;
  }

  @EventListener
  public void onReportImportCompleted(ReportImportCompleted event) {
    runImportPipeline();
  }

  @EventListener
  public void onFundPositionImportRequested(RunFundPositionImportRequested event) {
    runImportPipeline();
  }

  private void runImportPipeline() {
    pipelineTracker.stepStarted(POSITION_IMPORT);
    var totals = runImport();
    pipelineTracker.stepCompleted(POSITION_IMPORT, totals);

    pipelineTracker.stepStarted(HEALTH_CHECK);
    if (healthCheckFailed) {
      pipelineTracker.stepFailed(HEALTH_CHECK, healthCheckFailureDetail);
    } else {
      pipelineTracker.stepCompleted(HEALTH_CHECK);
    }

    eventPublisher.publishEvent(new FundPositionsImported());
  }

  private boolean healthCheckFailed;
  private String healthCheckFailureDetail;

  public String runImport() {
    healthCheckFailed = false;
    healthCheckFailureDetail = null;
    LocalDate today = LocalDate.now(clock);
    int[] totals = {0, 0};
    IntStream.iterate(LOOKBACK_DAYS, i -> i >= 1, i -> i - 1)
        .mapToObj(today::minusDays)
        .forEach(
            date -> {
              for (ReportProvider provider : PROVIDERS) {
                try {
                  var result = importForProviderAndDate(provider, date);
                  totals[0] += result.imported();
                  totals[1] += result.updated();
                } catch (Exception e) {
                  log.error("Fund position import failed: provider={}, date={}", provider, date, e);
                }
              }
            });
    if (totals[0] == 0 && totals[1] == 0) {
      return null;
    }
    return "%d new, %d updated".formatted(totals[0], totals[1]);
  }

  public ImportResult importForProviderAndDate(ReportProvider provider, LocalDate date) {
    log.info("Starting fund position import: provider={}, date={}", provider, date);

    Optional<InvestmentReport> report = reportService.getReport(provider, POSITIONS, date);
    if (report.isEmpty()) {
      log.info("No positions report in database: provider={}, date={}", provider, date);
      return new ImportResult(0, 0);
    }

    FundPositionParser parser = parsers.get(provider);
    if (parser == null) {
      log.warn("No parser configured for provider: provider={}", provider);
      return new ImportResult(0, 0);
    }

    InvestmentReport investmentReport = report.get();
    List<FundPosition> positions =
        parser.parse(
            investmentReport.getRawData(),
            investmentReport.getReportDate(),
            investmentReport.getMetadata());
    log.info(
        "Parsed fund positions: provider={}, date={}, count={}", provider, date, positions.size());

    var healthResults = healthCheckService.check(positions);
    if (healthResults.stream().anyMatch(HealthCheckResult::hasFails)) {
      healthCheckFailed = true;
      healthCheckFailureDetail = "Import blocked: provider=%s, date=%s".formatted(provider, date);
      healthCheckNotifier.notify(provider, date, healthResults);
      log.error("Health check failed, import blocked: provider={}, date={}", provider, date);
      return new ImportResult(0, 0);
    }

    ImportResult result = importService.upsertPositions(positions);

    if (healthResults.stream().anyMatch(HealthCheckResult::hasWarnings)) {
      healthCheckNotifier.notify(provider, date, healthResults);
    }
    if (result.imported() > 0 || result.updated() > 0) {
      pipelineTracker.markChanged();
    }
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
      navFunds.forEach(fund -> fundPositionLedgerService.rerecordPositionsFromDate(fund, date));
    }

    return result;
  }
}
