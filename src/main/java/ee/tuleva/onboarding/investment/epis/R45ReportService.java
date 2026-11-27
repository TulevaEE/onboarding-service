package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.epis.SummaryData.dates;
import static ee.tuleva.onboarding.investment.epis.SummaryData.number;
import static ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser.findValue;
import static ee.tuleva.onboarding.investment.report.ReportProvider.EPIS;
import static ee.tuleva.onboarding.investment.report.ReportType.R45;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.investment.epis.R45TransactionType.Direction;
import ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser;
import ee.tuleva.onboarding.investment.epis.parser.R45ParseResult;
import ee.tuleva.onboarding.investment.epis.parser.R45ReportParser;
import ee.tuleva.onboarding.investment.epis.parser.R45UnknownRow;
import ee.tuleva.onboarding.investment.epis.parser.R45UnvaluedRow;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  private static final String ROWS = "rows";

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
        Stream.concat(
                parsed.unvaluedRows().stream().map(R45UnvaluedRow::fundCode),
                parsed.unknownRows().stream().map(R45UnknownRow::fundCode).filter(Objects::nonNull))
            .collect(Collectors.toUnmodifiableSet());
    if (!parsed.unvaluedRows().isEmpty()) {
      log.warn(
          "R45 rows without NAV could not be valued: reportId={}, unvaluedRows={}",
          reportId,
          parsed.unvaluedRows());
      notificationService.sendMessage(unvaluedAlertMessage(reportId, parsed), INVESTMENT);
    }
    if (!parsed.unknownRows().isEmpty()) {
      log.warn(
          "R45 rows with unknown transaction type or unmapped ISIN: reportId={}, unknownRows={}",
          reportId,
          parsed.unknownRows());
      notificationService.sendMessage(unknownRowsAlertMessage(reportId, parsed), INVESTMENT);
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
                        parsed.rowsByFundCode().getOrDefault(entry.getKey().getCode(), List.of()),
                        !incompleteFundCodes.contains(entry.getKey().getCode())))
            .toList());
    return results;
  }

  public Map<TulevaFund, R45Result> getLatestFlows() {
    Map<TulevaFund, R45Result> flows = new LinkedHashMap<>();
    for (TulevaFund fund : TulevaFund.values()) {
      latestSummary(fund).ifPresent(summary -> flows.put(fund, toResult(summary)));
    }
    return flows;
  }

  public boolean hasReportOn(LocalDate reportDate) {
    return reportRepository.existsByProviderAndReportTypeAndReportDate(EPIS, R45, reportDate);
  }

  public Optional<LocalDate> latestReportDateBefore(LocalDate date) {
    return reportRepository.findLatestReportDateBefore(EPIS, R45, date);
  }

  public Map<TulevaFund, R45Result> getFlowsOn(LocalDate reportDate) {
    Map<TulevaFund, R45Result> flows = new LinkedHashMap<>();
    summariesOn(reportDate).forEach(summary -> flows.put(summary.getFund(), toResult(summary)));
    return flows;
  }

  public List<TulevaFund> getIncompleteFundsOn(LocalDate reportDate) {
    return summariesOn(reportDate).stream()
        .filter(summary -> !summary.getComplete())
        .map(EpisReportSummary::getFund)
        .toList();
  }

  private List<EpisReportSummary> summariesOn(LocalDate reportDate) {
    return summaryRepository.findByReportTypeAndReportDate(R45, reportDate);
  }

  private static R45Result toResult(EpisReportSummary summary) {
    return new R45Result(
        number(summary.getData(), "inflowEur"),
        number(summary.getData(), "outflowEur"),
        number(summary.getData(), "netEur"));
  }

  public List<LocalDate> getLatestRedRowSettlementDates(TulevaFund fund) {
    return latestSummary(fund)
        .map(summary -> dates(summary.getData(), "redRowSettlementDates"))
        .orElse(List.of());
  }

  public List<LocalDate> getRedRowSettlementDatesOn(TulevaFund fund, LocalDate reportDate) {
    return reportRepository
        .findByProviderAndReportTypeAndReportDate(EPIS, R45, reportDate)
        .map(report -> redRowSettlementDatesByFundCode(rawCsv(report)))
        .map(datesByFundCode -> datesByFundCode.getOrDefault(fund.getCode(), List.of()))
        .orElse(List.of());
  }

  public Optional<List<R45Row>> getRowsOn(TulevaFund fund, LocalDate reportDate) {
    return summariesOn(reportDate).stream()
        .filter(summary -> summary.getFund() == fund)
        .findFirst()
        .map(R45ReportService::rowsOf)
        .orElse(Optional.of(List.of()));
  }

  private static Optional<List<R45Row>> rowsOf(EpisReportSummary summary) {
    if (!(summary.getData().get(ROWS) instanceof Collection<?> rows)) {
      return Optional.empty();
    }
    try {
      return Optional.of(rows.stream().map(row -> rowOf((Map<?, ?>) row)).toList());
    } catch (RuntimeException e) {
      log.warn("Stored R45 rows cannot be read: summaryId={}", summary.getId(), e);
      return Optional.empty();
    }
  }

  private static R45Row rowOf(Map<?, ?> row) {
    Object settlementDate = row.get("settlementDate");
    return new R45Row(
        Direction.valueOf(String.valueOf(row.get("direction"))),
        new BigDecimal(String.valueOf(row.get("valueEur"))),
        settlementDate == null ? null : LocalDate.parse(String.valueOf(settlementDate)));
  }

  private static Map<String, Object> storedRow(R45Row row) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("direction", row.direction().name());
    map.put("valueEur", row.valueEur().toPlainString());
    if (row.settlementDate() != null) {
      map.put("settlementDate", row.settlementDate().toString());
    }
    return map;
  }

  private Optional<EpisReportSummary> latestSummary(TulevaFund fund) {
    return summaryRepository.findTopByReportTypeAndFundOrderByReportDateDescIdDesc(R45, fund);
  }

  private EpisReportSummary toSummary(
      InvestmentReport report,
      TulevaFund fund,
      R45Result result,
      Map<String, List<LocalDate>> redRowDates,
      List<R45Row> rows,
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
                "inflowEur",
                result.inflowEur(),
                "outflowEur",
                result.outflowEur(),
                "netEur",
                result.netEur(),
                "redRowSettlementDates",
                redRowDates.getOrDefault(fund.getCode(), List.of()).stream()
                    .map(LocalDate::toString)
                    .toList(),
                ROWS,
                rows.stream().map(R45ReportService::storedRow).toList()))
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

  private static String unknownRowsAlertMessage(Long reportId, R45ParseResult parsed) {
    StringBuilder message =
        new StringBuilder(
            "⚠️ R45 tundmatu tehingu liik või kaardistamata ISIN (likviidsus võib olla alahinnatud): reportId=%s"
                .formatted(reportId));
    for (R45UnknownRow row : parsed.unknownRows()) {
      message.append(
          "\ntehingu liik %s, ISIN %s, fond %s"
              .formatted(
                  row.typeCode(), row.isin(), row.fundCode() == null ? "?" : row.fundCode()));
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
      LocalDate settlementDate = R45ReportParser.settlementDate(row);
      if (fund.isEmpty() || settlementDate == null) {
        continue;
      }
      dates.computeIfAbsent(fund.get().getCode(), code -> new ArrayList<>()).add(settlementDate);
    }
    dates.replaceAll((fundCode, fundDates) -> fundDates.stream().distinct().sorted().toList());
    return dates;
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
