package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.report.ReportProvider.EPIS;
import static ee.tuleva.onboarding.investment.report.ReportType.R16_FORECASTED_PAYMENTS;
import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.epis.parser.R16ParsedFlow;
import ee.tuleva.onboarding.investment.epis.parser.R16ReportParser;
import ee.tuleva.onboarding.investment.epis.parser.R17ReportParser;
import ee.tuleva.onboarding.investment.epis.parser.R21ReportParser;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.investment.report.ReportType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpisReportIngestionService {

  private final InvestmentReportRepository reportRepository;
  private final EpisReportSummaryRepository summaryRepository;
  private final PevaRavaCycleRepository cycleRepository;
  private final PevaRavaPeriodService periodService;
  private final R45ReportService r45ReportService;
  private final R16ReportParser r16ReportParser;
  private final R17ReportParser r17ReportParser;
  private final R21ReportParser r21ReportParser;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public EpisReportIngestionResult ingestReport(ReportType reportType, String csv) {
    LocalDate today = LocalDate.now(clock);
    InvestmentReport report = saveRawReport(reportType, csv, today);

    switch (reportType) {
      case R45 -> r45ReportService.processAndStore(report.getId());
      case R17_PEVA -> processR17(report, csv, today);
      case R21_RAVA -> processR21(report, csv, today);
      case R16_FORECASTED_PAYMENTS -> processR16(report, csv);
      default ->
          throw new IllegalArgumentException(
              "Unsupported EPIS report type: reportType=" + reportType);
    }

    Map<TulevaFund, Map<String, Object>> fundSummaries = storedSummaries(report.getId());
    eventPublisher.publishEvent(
        new EpisReportProcessed(report.getId(), reportType, report.getReportDate()));
    log.info(
        "EPIS report ingested: reportId={}, reportType={}, reportDate={}, funds={}",
        report.getId(),
        reportType,
        report.getReportDate(),
        fundSummaries.keySet());
    return new EpisReportIngestionResult(
        report.getId(), reportType, report.getReportDate(), fundSummaries);
  }

  private InvestmentReport saveRawReport(ReportType reportType, String csv, LocalDate today) {
    List<Map<String, Object>> rawData = List.of(Map.of("csv", csv));
    InvestmentReport report =
        reportRepository
            .findByProviderAndReportTypeAndReportDate(EPIS, reportType, today)
            .orElseGet(
                () ->
                    InvestmentReport.builder()
                        .provider(EPIS)
                        .reportType(reportType)
                        .reportDate(today)
                        .createdAt(clock.instant())
                        .build());
    report.setRawData(rawData);
    return reportRepository.save(report);
  }

  private void processR17(InvestmentReport report, String csv, LocalDate today) {
    PevaRavaPeriod period = activePeriod(today);
    PevaRavaCycle cycle = period.cycle();
    Map<String, R17Result> parsed = r17ReportParser.parse(csv, cycle.lockDate(), cycle.execDate());

    replaceSummaries(
        report,
        R17_PEVA,
        report.getReportDate(),
        parsed,
        (data, result) -> {
          data.put("pikUnits", result.pikUnits());
          data.put("switchingNetUnits", result.switchingNetUnits());
        });
    linkCycle(period, entity -> entity.setR17ReportId(report.getId()));
  }

  private void processR21(InvestmentReport report, String csv, LocalDate today) {
    PevaRavaPeriod period = activePeriod(today);
    Map<String, R21Result> parsed =
        r21ReportParser.parse(csv, YearMonth.from(period.cycle().execDate()));

    replaceSummaries(
        report,
        R21_RAVA,
        report.getReportDate(),
        parsed,
        (data, result) -> data.put("ravaUnits", result.ravaUnits()));
    linkCycle(period, entity -> entity.setR21ReportId(report.getId()));
  }

  private void processR16(InvestmentReport report, String csv) {
    Map<String, R16ParsedFlow> parsed = r16ReportParser.parse(csv);
    if (parsed.isEmpty()) {
      throw new IllegalArgumentException(
          "R16 report contains no recognized fund rows: reportId=" + report.getId());
    }
    YearMonth paymentMonth = parsed.values().iterator().next().paymentMonth();

    replaceSummaries(
        report,
        R16_FORECASTED_PAYMENTS,
        paymentMonth.atDay(1),
        parsed,
        (data, flow) -> {
          data.put("fondimaksedUnits", flow.fondimaksedUnits());
          data.put("uhekordsedUnits", flow.uhekordsedUnits());
          data.put("paymentMonth", flow.paymentMonth().toString());
        });
  }

  private <T> void replaceSummaries(
      InvestmentReport report,
      ReportType reportType,
      LocalDate summaryDate,
      Map<String, T> resultsByFundCode,
      BiConsumer<Map<String, Object>, T> dataWriter) {
    summaryRepository.deleteByReportTypeAndReportDate(reportType, summaryDate);
    summaryRepository.saveAll(
        resultsByFundCode.entrySet().stream()
            .map(
                entry -> {
                  TulevaFund fund = TulevaFund.fromCode(entry.getKey());
                  Map<String, Object> data = new LinkedHashMap<>();
                  dataWriter.accept(data, entry.getValue());
                  return EpisReportSummary.builder()
                      .reportId(report.getId())
                      .reportType(reportType)
                      .reportDate(summaryDate)
                      .fund(fund)
                      .fundIsin(fund.getIsin())
                      .data(data)
                      .build();
                })
            .toList());
  }

  private void linkCycle(PevaRavaPeriod period, Consumer<PevaRavaCycleEntity> reportLinker) {
    PevaRavaCycle cycle = period.cycle();
    PevaRavaCycleEntity entity =
        cycleRepository
            .findByExecDate(cycle.execDate())
            .orElseGet(() -> PevaRavaCycleEntity.forCycle(cycle));
    entity.setPhase(period.phase());
    reportLinker.accept(entity);
    cycleRepository.save(entity);
  }

  private PevaRavaPeriod activePeriod(LocalDate today) {
    return periodService
        .getCurrentPeriod(today)
        .orElseThrow(
            () -> new IllegalStateException("No active PEVA/RAVA cycle found: today=" + today));
  }

  private Map<TulevaFund, Map<String, Object>> storedSummaries(Long reportId) {
    Map<TulevaFund, Map<String, Object>> summaries = new LinkedHashMap<>();
    summaryRepository
        .findByReportId(reportId)
        .forEach(summary -> summaries.put(summary.getFund(), summary.getData()));
    return summaries;
  }
}
