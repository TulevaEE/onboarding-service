package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.parser.SebFundPositionParser;
import ee.tuleva.onboarding.investment.report.CsvToJsonConverter;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.report.SebReportAsOfDate;
import ee.tuleva.onboarding.investment.transaction.OpenTradeSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  InvestmentReportService.class,
  CsvToJsonConverter.class,
  UnsettledTradesChecker.class,
  UnsettledTradesCheckIntegrationTest.SebTradeListReading.class
})
class UnsettledTradesCheckIntegrationTest {

  private static final LocalDate PREVIOUS_NAV_DATE = LocalDate.of(2026, 10, 19);
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 10, 20);

  @Autowired InvestmentReportRepository reportRepository;
  @Autowired OpenTradeSource openTradeSource;
  @Autowired UnsettledTradesChecker checker;

  private final SebFundPositionParser parser =
      new SebFundPositionParser(Clock.systemUTC(), new SebReportAsOfDate());

  @BeforeEach
  void sebTradeLists() {
    saveTradeList(
        PREVIOUS_NAV_DATE,
        List.of(
            purchase("DLA1289466", "IE000QWCYQT0", "3088796.84", "2026-10-20"),
            purchase("DLA1289465", "IE000I9HGDZ3", "2730597.59", "2026-10-20")));
    saveTradeList(
        NAV_DATE,
        List.of(
            purchase("DLA1287775", "IE00BFG1TM61", "115536.00", "2026-10-22"),
            purchase("DLA1287774", "IE00BKPTWY98", "1107572.00", "2026-10-22")));
  }

  @Test
  void failsWhileSebsPositionsStillOweForAPurchaseThatLeftItsTradeList() {
    var findings = checkPositionsWithPayables("-4311904.84");

    assertThat(findings)
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.severity()).isEqualTo(FAIL);
              assertThat(finding.message())
                  .isEqualTo(
                      "Trade payables 4311904.84, open buys 1223108.00, excess 3088796.84, NAV"
                          + " too low. Cause: DLA1289466 IE000QWCYQT0 3088796.84 left SEB's"
                          + " trade list, settlement 2026-10-20");
            });
  }

  @Test
  void clearsOnceSebsCorrectedPositionsOweOnlyThePurchasesStillOpen() {
    assertThat(checkPositionsWithPayables("-1223108.00")).isEmpty();
  }

  private List<HealthCheckFinding> checkPositionsWithPayables(String payables) {
    List<FundPosition> positions =
        parser.parse(
            List.of(
                positionRow("Cash account in SEB Pank", null, "2613401.69"),
                positionRow("Total receivables of unsettled transactions", null, "0.00"),
                positionRow("Total payables of unsettled transactions", null, payables)),
            NAV_DATE,
            Map.of("asOfDate", NAV_DATE.toString()));
    return checker.check(
        TUK75,
        NAV_DATE,
        positions.stream().filter(position -> position.getAccountType() == LIABILITY).toList(),
        positions.stream().filter(position -> position.getAccountType() == RECEIVABLES).toList(),
        openTradeSource.openTrades(NAV_DATE).orElse(null),
        openTradeSource.openTrades(PREVIOUS_NAV_DATE).orElse(null));
  }

  private Map<String, Object> positionRow(String name, String isin, String marketValue) {
    var row = new HashMap<String, Object>();
    row.put("Client name", "TUK75");
    row.put("Account", "EE000000000000000000");
    row.put("ISIN", isin == null ? "" : isin);
    row.put("Name", name);
    row.put("Market Value (EUR)", marketValue);
    return row;
  }

  private void saveTradeList(LocalDate asOf, List<Map<String, Object>> trades) {
    var rawData = new ArrayList<Map<String, Object>>();
    rawData.add(Map.of("Fund Management Company:", "As of:", "Tuleva Fondid AS", asOf.toString()));
    rawData.addAll(trades);
    reportRepository.save(
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(asOf)
            .rawData(rawData)
            .metadata(Map.of())
            .createdAt(Instant.parse("2026-10-21T06:00:00Z"))
            .build());
  }

  private Map<String, Object> purchase(
      String ourRef, String isin, String total, String settlementDate) {
    var row = new HashMap<String, Object>();
    row.put("Our ref", ourRef);
    row.put("Client name", TUK75.getDisplayName());
    row.put("ISIN", isin);
    row.put("Quantity", new BigDecimal("100"));
    row.put("Price", new BigDecimal("10"));
    row.put("Total", new BigDecimal(total));
    row.put("Buy/Sell", "Buy");
    row.put("Trade date", "2026-10-16T09:37:55Z");
    row.put("Settlement date", settlementDate);
    return row;
  }

  @TestConfiguration
  @ComponentScan(
      basePackageClasses = OpenTradeSource.class,
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.REGEX,
              pattern =
                  "ee\\.tuleva\\.onboarding\\.investment\\.transaction\\.ingest\\."
                      + "(SebOpenTradeSource|SebTradeListLookup|SebPendingTransactionExtractor"
                      + "|SebClientNameToFundResolver)"))
  static class SebTradeListReading {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-21T07:00:00Z"), ZoneId.of("Europe/Tallinn"));
    }
  }
}
