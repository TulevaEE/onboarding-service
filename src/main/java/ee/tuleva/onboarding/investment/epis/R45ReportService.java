package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.epis.SummaryData.dates;
import static ee.tuleva.onboarding.investment.epis.SummaryData.number;
import static ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser.findDate;
import static ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser.findValue;
import static ee.tuleva.onboarding.investment.report.ReportType.R45;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser;
import ee.tuleva.onboarding.investment.epis.parser.R45ParseResult;
import ee.tuleva.onboarding.investment.epis.parser.R45ReportParser;
import ee.tuleva.onboarding.investment.epis.parser.R45UnvaluedRow;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class R45ReportService {

  private static final String RED_TRANSACTION_TYPE = "RED";
  private static final String HEADER_MARKER = "Tehingu liik";

  private final InvestmentReportRepository reportRepository;
  private final EpisReportSummaryRepository summaryRepository;
  private final OwnFundNavProvider ownFundNavProvider;
  private final R45ReportParser r45ReportParser;
  private final EpisCsvParser csvParser;
  private final OperationsNotificationService notificationService;

  @Transactional
  public Map<TulevaFund, R45Result> processAndStore(Long reportId) {
    InvestmentReport report =
        reportRepository
            .findById(reportId)
            .orElseThrow(
                () -> new IllegalArgumentException("R45 report not found: reportId=" + reportId));
    if (report.getReportType() != R45) {
      throw new IllegalArgumentException(
          "Report is not R45: reportId=" + reportId + ", reportType=" + report.getReportType());
    }

    String csv = rawCsv(report);
    LocalDate reportDate = report.getReportDate();
    R45ParseResult parsed = r45ReportParser.parse(csv, reportDate, fallbackNavsByIsin(reportDate));
    Set<String> incompleteFundCodes =
        parsed.unvaluedRows().stream()
            .map(R45UnvaluedRow::fundCode)
            .collect(Collectors.toUnmodifiableSet());
    if (!parsed.unvaluedRows().isEmpty()) {
      log.warn(
          "R45 rows without NAV could not be valued: reportId={}, unvaluedRows={}",
          reportId,
          parsed.unvaluedRows());
      notificationService.sendMessage(unvaluedAlertMessage(reportId, parsed), INVESTMENT);
    }
    Map<String, List<LocalDate>> redRowDates = redRowSettlementDatesByFundCode(csv);

    Map<TulevaFund, R45Result> results = new LinkedHashMap<>();
    parsed
        .fundResults()
        .forEach((fundCode, result) -> results.put(TulevaFund.fromCode(fundCode), result));

    summaryRepository.deleteByReportId(reportId);
    summaryRepository.saveAll(
        results.entrySet().stream()
            .map(
                entry ->
                    toSummary(
                        report,
                        entry.getKey(),
                        entry.getValue(),
                        redRowDates,
                        !incompleteFundCodes.contains(entry.getKey().getCode())))
            .toList());
    return results;
  }

  public Map<TulevaFund, R45Result> getLatestFlows() {
    Map<TulevaFund, R45Result> flows = new LinkedHashMap<>();
    for (TulevaFund fund : TulevaFund.values()) {
      latestSummary(fund)
          .ifPresent(
              summary ->
                  flows.put(
                      fund,
                      new R45Result(
                          number(summary.getData(), "inflowEur"),
                          number(summary.getData(), "outflowEur"),
                          number(summary.getData(), "netEur"))));
    }
    return flows;
  }

  public List<TulevaFund> getIncompleteFunds() {
    return Arrays.stream(TulevaFund.values())
        .filter(fund -> latestSummary(fund).map(summary -> !summary.getComplete()).orElse(false))
        .toList();
  }

  public List<LocalDate> getLatestRedRowSettlementDates(TulevaFund fund) {
    return latestSummary(fund)
        .map(summary -> dates(summary.getData(), "redRowSettlementDates"))
        .orElse(List.of());
  }

  private Optional<EpisReportSummary> latestSummary(TulevaFund fund) {
    return summaryRepository.findTopByReportTypeAndFundOrderByReportDateDescIdDesc(R45, fund);
  }

  private EpisReportSummary toSummary(
      InvestmentReport report,
      TulevaFund fund,
      R45Result result,
      Map<String, List<LocalDate>> redRowDates,
      boolean complete) {
    return EpisReportSummary.builder()
        .reportId(report.getId())
        .reportType(R45)
        .reportDate(report.getReportDate())
        .fund(fund)
        .fundIsin(fund.getIsin())
        .complete(complete)
        .data(
            Map.of(
                "inflowEur", result.inflowEur(),
                "outflowEur", result.outflowEur(),
                "netEur", result.netEur(),
                "redRowSettlementDates",
                    redRowDates.getOrDefault(fund.getCode(), List.of()).stream()
                        .map(LocalDate::toString)
                        .toList()))
        .build();
  }

  private static String unvaluedAlertMessage(Long reportId, R45ParseResult parsed) {
    StringBuilder message =
        new StringBuilder(
            "⚠️ R45 ridu ilma NAV-ita ei saanud hinnata (vooge blokeeritud kuni NAV lisatakse): reportId=%s"
                .formatted(reportId));
    for (R45UnvaluedRow row : parsed.unvaluedRows()) {
      message.append(
          "\n%s %s: %s osakut, ISIN %s"
              .formatted(row.fundCode(), row.transactionType(), row.units(), row.isin()));
    }
    return message.toString();
  }

  private Map<String, List<LocalDate>> redRowSettlementDatesByFundCode(String csv) {
    Map<String, List<LocalDate>> dates = new LinkedHashMap<>();
    for (Map<String, String> row : csvParser.parse(csv, HEADER_MARKER).rows()) {
      String type = trimmedUpperCase(findValue(row, "tehingu liik"));
      if (!RED_TRANSACTION_TYPE.equals(type)) {
        continue;
      }
      Optional<TulevaFund> fund = TulevaFund.findByIsin(trimmedUpperCase(findValue(row, "isin")));
      LocalDate settlementDate = settlementDate(row);
      if (fund.isEmpty() || settlementDate == null) {
        continue;
      }
      dates.computeIfAbsent(fund.get().getCode(), code -> new ArrayList<>()).add(settlementDate);
    }
    dates.replaceAll((fundCode, fundDates) -> fundDates.stream().distinct().sorted().toList());
    return dates;
  }

  private static @Nullable LocalDate settlementDate(Map<String, String> row) {
    String value = findValue(row, "täitmise kuupäev", "taitmise kuupaev");
    return value == null ? null : findDate(value);
  }

  private Map<String, BigDecimal> fallbackNavsByIsin(LocalDate asOfDate) {
    Map<String, BigDecimal> navs = new LinkedHashMap<>();
    Arrays.stream(TulevaFund.values())
        .forEach(
            fund ->
                ownFundNavProvider
                    .findLatestNav(fund, asOfDate)
                    .ifPresent(nav -> navs.put(fund.getIsin(), nav)));
    return navs;
  }

  private static String rawCsv(InvestmentReport report) {
    return report.getRawData().stream()
        .findFirst()
        .map(row -> String.valueOf(row.get("csv")))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "R45 report has no raw CSV payload: reportId=" + report.getId()));
  }

  private static String trimmedUpperCase(@Nullable String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
