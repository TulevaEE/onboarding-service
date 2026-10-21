package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.QUANTITY_CHANGE;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.UNIT_CASH_FLOW;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.UNSETTLED_TRADES;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionImportService;
import ee.tuleva.onboarding.investment.position.parser.SebFundPositionParser;
import ee.tuleva.onboarding.investment.report.CsvToJsonConverter;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.report.ReportType;
import ee.tuleva.onboarding.investment.report.SebReportAsOfDate;
import ee.tuleva.onboarding.investment.transaction.ExecutedPriceSource;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  HealthCheckService.class,
  CompletenessChecker.class,
  IsinMatchChecker.class,
  OutstandingUnitsChecker.class,
  UnitReconciliationChecker.class,
  NavUnitImpactChecker.class,
  ReceivablesChecker.class,
  QuantityChangeChecker.class,
  PayablesChecker.class,
  NavFlowConsistencyChecker.class,
  MarketPnlCalculator.class,
  LiabilityRecognitionChecker.class,
  UnsettledTradeChecker.class,
  UnitCashFlowChecker.class,
  InvestmentParameterRepository.class,
  InvestmentReportService.class,
  CsvToJsonConverter.class,
  SebFundPositionParser.class,
  SebReportAsOfDate.class,
  FundPositionImportService.class,
  SebReportCrossCheckIT.SebPendingReportReading.class
})
class SebReportCrossCheckIT {

  private static final LocalDate PREVIOUS = LocalDate.of(2026, 10, 19);
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 10, 20);
  private static final char DELIMITER = ';';
  private static final int HEADER_ROW_INDEX = 5;

  private static final String PREVIOUS_POSITIONS =
      """
      Fund Management Company:;Tuleva Fondid AS;;;;;;
      Sent:;2026-10-20;;;;;;
      Report type:;Fund position report;;;;;;
      As of:;2026-10-19;;;;;;
      ;;;;;;;
      Client name;Account;ISIN;Name;Quantity;Market price;Currency;Market Value (EUR)
      TUK75;tuk75-cash;;Cash account in SEB Pank;1000000,000;1,0000;EUR;1000000,00
      TUK75;tuk75-custody;IE000QWCYQT0;Amundi MSCI World Screened UCITS ETF;100000,000;5,0000;EUR;500000,00
      TUK75;tuk75-custody;IE00BKPTWY98;iShares Emerging Market Screened Equity Index Fund;50000,000;10,0000;EUR;500000,00
      TUK75;tuk75-cash;;Total receivables of unsettled transactions;;;;0,00
      TUK75;tuk75-cash;;Total payables of unsettled transactions;;;;-300000,00
      TUK75;tuk75-cash;EE3600109435;Receivables of outstanding units;;;;0,00
      TUK75;tuk75-cash;EE3600109435;Payables of redeemed units;;;;0,00
      TUK75;Total;;;;;;1700000,00
      TUK75;Register;EE3600109435;Total outstanding units:;1000000,000;;;
      TUK00;tuk00-cash;;Cash account in SEB Pank;200000,000;1,0000;EUR;200000,00
      TUK00;tuk00-custody;IE0005032192;iShares Euro Credit Bond Index Fund Flex;100000,000;20,0000;EUR;2000000,00
      TUK00;tuk00-cash;;Total receivables of unsettled transactions;;;;0,00
      TUK00;tuk00-cash;;Total payables of unsettled transactions;;;;0,00
      TUK00;tuk00-cash;EE3600109443;Receivables of outstanding units;;;;0,00
      TUK00;tuk00-cash;EE3600109443;Payables of redeemed units;;;;0,00
      TUK00;Total;;;;;;2200000,00
      TUK00;Register;EE3600109443;Total outstanding units:;3000000,000;;;
      """;

  private static final String POSITIONS_WITH_A_SETTLED_PAYABLE_AND_WRONG_UNITS =
      """
      Fund Management Company:;Tuleva Fondid AS;;;;;;
      Sent:;2026-10-21;;;;;;
      Report type:;Fund position report;;;;;;
      As of:;2026-10-20;;;;;;
      ;;;;;;;
      Client name;Account;ISIN;Name;Quantity;Market price;Currency;Market Value (EUR)
      TUK75;tuk75-cash;;Cash account in SEB Pank;701700,000;1,0000;EUR;701700,00
      TUK75;tuk75-custody;IE000QWCYQT0;Amundi MSCI World Screened UCITS ETF;100000,000;5,1000;EUR;510000,00
      TUK75;tuk75-custody;IE00BKPTWY98;iShares Emerging Market Screened Equity Index Fund;51000,000;10,2000;EUR;520200,00
      TUK75;tuk75-cash;;Total receivables of unsettled transactions;;;;0,00
      TUK75;tuk75-cash;;Total payables of unsettled transactions;;;;-310200,00
      TUK75;tuk75-cash;EE3600109435;Receivables of outstanding units;;;;0,00
      TUK75;tuk75-cash;EE3600109435;Payables of redeemed units;;;;0,00
      TUK75;Total;;;;;;1421700,00
      TUK75;Register;EE3600109435;Total outstanding units:;1001000,000;;;
      TUK00;tuk00-cash;;Cash account in SEB Pank;200733,330;1,0000;EUR;200733,33
      TUK00;tuk00-custody;IE0005032192;iShares Euro Credit Bond Index Fund Flex;101000,000;20,0000;EUR;2020000,00
      TUK00;tuk00-cash;;Total receivables of unsettled transactions;;;;0,00
      TUK00;tuk00-cash;;Total payables of unsettled transactions;;;;-20000,00
      TUK00;tuk00-cash;EE3600109443;Receivables of outstanding units;;;;0,00
      TUK00;tuk00-cash;EE3600109443;Payables of redeemed units;;;;0,00
      TUK00;Total;;;;;;2200733,33
      TUK00;Register;EE3600109443;Total outstanding units:;2980000,000;;;
      """;

  private static final String PENDING_HEADER =
      """
      Fund Management Company:;Tuleva Fondid AS;;;;;;;;;;;;;
      Sent:;%s;;;;;;;;;;;;;
      Report type:;Unsettled transactions;;;;;;;;;;;;;
      As of:;%s;;;;;;;;;;;;;
      ;;;;;;;;;;;;;;
      Client name;Account;Client ref;Our ref;ISIN;Instrument name;Currency;Quantity;Price;Settlement amount;Broker fee;Total;Buy/Sell;Trade date;Settlement date
      """;

  private static final String PREVIOUS_PENDING =
      PENDING_HEADER.formatted("2026-10-20", "2026-10-19")
          + """
          Tuleva Maailma Aktsiate Pensionifond;tuk75-custody;00000000-0000-0000-0000-000000000001;DLA0000001;IE000QWCYQT0;Amundi MSCI World Screened UCITS ETF;EUR;60000;5;300000,00;0,00;300000,00;Buy;2026-10-16T09:37:55Z;2026-10-20
          """;

  private static final String PENDING =
      PENDING_HEADER.formatted("2026-10-21", "2026-10-20")
          + """
          Tuleva Maailma Aktsiate Pensionifond;tuk75-custody;00000000-0000-0000-0000-000000000002;DLA0000002;IE00BKPTWY98;iShares Emerging Market Screened Equity Index Fund;EUR;1000;10,2;10200,00;0,00;10200,00;Buy;2026-10-16T11:48:17Z;2026-10-22
          Tuleva Maailma Võlakirjade Pensionifond;tuk00-custody;00000000-0000-0000-0000-000000000003;DLA0000003;IE0005032192;iShares Euro Credit Bond Index Fund Flex;EUR;1000;20;20000,00;0,00;20000,00;Buy;2026-10-20T08:00:00Z;2026-10-22
          """;

  @TestConfiguration
  @ComponentScan(
      basePackages = "ee.tuleva.onboarding.investment.transaction.ingest",
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.REGEX,
              pattern =
                  ".*\\.(SebPendingTradeSource|SebPendingReportFinder"
                      + "|SebPendingTransactionExtractor|SebClientNameToFundResolver)"))
  static class SebPendingReportReading {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-21T07:30:00Z"), ZoneId.of("Europe/Tallinn"));
    }
  }

  @MockitoBean AuthoritativeUnitsSource authoritativeUnitsSource;
  @MockitoBean ExecutedPriceSource executedPriceSource;
  @MockitoBean ExitMarkResolver exitMarkResolver;

  @Autowired HealthCheckService healthCheckService;
  @Autowired InvestmentReportService reportService;
  @Autowired SebFundPositionParser parser;
  @Autowired FundPositionImportService importService;
  @Autowired ModelPortfolioAllocationRepository allocationRepository;

  @BeforeEach
  void holdOnlyModelPortfolioInstruments() {
    allocationRepository.saveAll(
        List.of(
            allocation(TUK75, "IE000QWCYQT0", "0.5"),
            allocation(TUK75, "IE00BKPTWY98", "0.5"),
            allocation(TUK00, "IE0005032192", "1")));
  }

  @Test
  void blocksTheFundWhosePositionReportStillCarriesASettledTradeAndWarnsOnUnitsWithoutCash() {
    importService.upsertPositions(positions(PREVIOUS, PREVIOUS_POSITIONS));
    save(PENDING_TRANSACTIONS, PREVIOUS, PREVIOUS_PENDING);
    save(PENDING_TRANSACTIONS, NAV_DATE, PENDING);

    var results =
        healthCheckService.check(
            positions(NAV_DATE, POSITIONS_WITH_A_SETTLED_PAYABLE_AND_WRONG_UNITS));

    assertThat(HealthCheckResult.blockedFunds(results)).containsExactly(TUK75);
    assertThat(findings(results, TUK75, UNSETTLED_TRADES))
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                UNSETTLED_TRADES,
                FAIL,
                "Trade payables 310200.00, pending buys 10200.00, diff 300000.00"
                    + " = DLA0000001, settled 2026-10-20"));
    assertThat(findings(results, TUK00, UNIT_CASH_FLOW))
        .containsExactly(
            new HealthCheckFinding(
                TUK00,
                UNIT_CASH_FLOW,
                WARNING,
                "Units -20000.000 but unit cash +733.33 EUR; cash implies 3000999.995 units,"
                    + " report 2980000.000"));
    assertThat(findings(results, TUK75, UNIT_CASH_FLOW)).isEmpty();
    assertThat(findings(results, TUK00, UNSETTLED_TRADES)).isEmpty();
    assertThat(findings(results, TUK75, QUANTITY_CHANGE)).isEmpty();
    assertThat(findings(results, TUK00, QUANTITY_CHANGE)).isEmpty();
  }

  @Test
  void saysTheTradeRowsWereNotCheckedAndBlocksNothingWhileThePendingReportIsMissing() {
    importService.upsertPositions(positions(PREVIOUS, PREVIOUS_POSITIONS));
    save(PENDING_TRANSACTIONS, PREVIOUS, PREVIOUS_PENDING);

    var results =
        healthCheckService.check(
            positions(NAV_DATE, POSITIONS_WITH_A_SETTLED_PAYABLE_AND_WRONG_UNITS));

    assertThat(HealthCheckResult.blockedFunds(results)).isEmpty();
    assertThat(findings(results, TUK75, UNSETTLED_TRADES))
        .extracting(HealthCheckFinding::severity, HealthCheckFinding::message)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                NOT_RUN, "Not checked: no SEB pending transactions report as of 2026-10-20"));
    assertThat(findings(results, TUK75, QUANTITY_CHANGE))
        .extracting(HealthCheckFinding::severity)
        .containsExactly(NOT_RUN);
  }

  private static ModelPortfolioAllocation allocation(TulevaFund fund, String isin, String weight) {
    return ModelPortfolioAllocation.builder()
        .effectiveDate(LocalDate.of(2026, 1, 1))
        .fund(fund)
        .isin(isin)
        .weight(new BigDecimal(weight))
        .build();
  }

  private List<FundPosition> positions(LocalDate asOf, String csv) {
    var report = save(POSITIONS, asOf, csv);
    return parser.parse(report.getRawData(), report.getReportDate(), report.getMetadata());
  }

  private ee.tuleva.onboarding.investment.report.InvestmentReport save(
      ReportType type, LocalDate asOf, String csv) {
    return reportService.saveReport(
        SEB,
        type,
        asOf,
        new ByteArrayInputStream(csv.getBytes(UTF_8)),
        DELIMITER,
        HEADER_ROW_INDEX,
        Map.of("asOfDate", asOf.toString()));
  }

  private static List<HealthCheckFinding> findings(
      List<HealthCheckResult> results, TulevaFund fund, HealthCheckType type) {
    return results.stream()
        .filter(result -> result.fund() == fund)
        .flatMap(result -> result.findings().stream())
        .filter(finding -> finding.checkType() == type)
        .toList();
  }
}
