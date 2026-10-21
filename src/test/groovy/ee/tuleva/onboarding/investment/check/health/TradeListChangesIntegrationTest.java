package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.QUANTITY_CHANGE;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
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
  QuantityChangeChecker.class,
  TradeListChangesIntegrationTest.SebTradeListReading.class
})
class TradeListChangesIntegrationTest {

  private static final LocalDate PREVIOUS_NAV_DATE = LocalDate.of(2026, 10, 19);
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 10, 20);
  private static final String ISIN = "IE00BFG1TM61";

  @Autowired InvestmentReportRepository reportRepository;
  @Autowired OpenTradeSource openTradeSource;
  @Autowired QuantityChangeChecker quantityChangeChecker;

  private final SebFundPositionParser parser =
      new SebFundPositionParser(Clock.systemUTC(), new SebReportAsOfDate());

  @Test
  void aQuantityChangeThatATradeNewOnSebsTradeListExplainsIsNotReported() {
    saveTradeList(PREVIOUS_NAV_DATE, List.of());
    saveTradeList(NAV_DATE, List.of(purchase("DLA0000001", "500", "5000.00")));

    assertThat(quantityChangeFindings()).isEmpty();
  }

  @Test
  void aQuantityChangeNoNewTradeExplainsIsReported() {
    saveTradeList(PREVIOUS_NAV_DATE, List.of(purchase("DLA0000001", "500", "5000.00")));
    saveTradeList(NAV_DATE, List.of(purchase("DLA0000001", "500", "5000.00")));

    assertThat(quantityChangeFindings())
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                QUANTITY_CHANGE,
                WARNING,
                "IE00BFG1TM61 quantity +500 (1000 → 1500), new on SEB's trade list +0"));
  }

  private List<HealthCheckFinding> quantityChangeFindings() {
    var changes =
        OpenTradeChanges.between(
            TUK75,
            PREVIOUS_NAV_DATE,
            openTradeSource.openTrades(PREVIOUS_NAV_DATE).orElse(null),
            NAV_DATE,
            openTradeSource.openTrades(NAV_DATE).orElse(null));
    return quantityChangeChecker.check(
        TUK75, securities(NAV_DATE, "1500"), securities(PREVIOUS_NAV_DATE, "1000"), changes);
  }

  private List<FundPosition> securities(LocalDate navDate, String quantity) {
    var row = new HashMap<String, Object>();
    row.put("Client name", "TUK75");
    row.put("Account", "EE000000000000000000");
    row.put("ISIN", ISIN);
    row.put("Name", "iShares Developed World Index Fund");
    row.put("Quantity", quantity);
    row.put("Market Value (EUR)", "15000.00");
    return parser.parse(List.of(row), navDate, Map.of("asOfDate", navDate.toString())).stream()
        .filter(position -> position.getAccountType() == SECURITY)
        .toList();
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

  private Map<String, Object> purchase(String ourRef, String quantity, String total) {
    var row = new HashMap<String, Object>();
    row.put("Our ref", ourRef);
    row.put("Client name", TUK75.getDisplayName());
    row.put("ISIN", ISIN);
    row.put("Quantity", new BigDecimal(quantity));
    row.put("Price", new BigDecimal("10"));
    row.put("Total", new BigDecimal(total));
    row.put("Buy/Sell", "Buy");
    row.put("Trade date", "2026-10-16T09:37:55Z");
    row.put("Settlement date", "2026-10-22");
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
