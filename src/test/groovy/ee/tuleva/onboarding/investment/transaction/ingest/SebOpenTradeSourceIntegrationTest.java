package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.report.CsvToJsonConverter;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.OpenTrade;
import ee.tuleva.onboarding.investment.transaction.OpenTrades;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
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
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  InvestmentReportService.class,
  CsvToJsonConverter.class,
  SebTradeListLookup.class,
  SebPendingTransactionExtractor.class,
  SebClientNameToFundResolver.class,
  SebOpenTradeSource.class,
  SebOpenTradeSourceIntegrationTest.FixedClock.class
})
class SebOpenTradeSourceIntegrationTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 10, 20);

  @Autowired InvestmentReportRepository reportRepository;
  @Autowired SebOpenTradeSource source;

  @Test
  void readsEachFundsOpenTradesFromTheTradeListThatDescribesTheNavDate() {
    saveTradeList(
        NAV_DATE.plusDays(1),
        NAV_DATE,
        List.of(
            trade(TUK75, "DLA1287775", "IE00BFG1TM61", "Buy", "115536.00", "2026-10-22"),
            trade(TUK75, "DLA1287774", "IE00BKPTWY98", "Buy", "1107572.00", "2026-10-22"),
            trade(TUV100, "DLA1287773", "IE00BFG1TM61", "Sell", "871918.00", "2026-10-22")));

    var openTrades = source.openTrades(NAV_DATE).orElseThrow();

    assertThat(openTrades.trades())
        .containsExactly(
            new OpenTrade(
                TUK75,
                BUY,
                new java.math.BigDecimal("115536.00"),
                "DLA1287775",
                "IE00BFG1TM61",
                LocalDate.of(2026, 10, 22)),
            new OpenTrade(
                TUK75,
                BUY,
                new java.math.BigDecimal("1107572.00"),
                "DLA1287774",
                "IE00BKPTWY98",
                LocalDate.of(2026, 10, 22)),
            new OpenTrade(
                TUV100,
                SELL,
                new java.math.BigDecimal("871918.00"),
                "DLA1287773",
                "IE00BFG1TM61",
                LocalDate.of(2026, 10, 22)));
    assertThat(openTrades.total(TUK75, BUY)).isEqualByComparingTo("1223108.00");
    assertThat(openTrades.total(TUK75, SELL)).isEqualByComparingTo("0");
    assertThat(openTrades.unattributedClientNames()).isEmpty();
  }

  @Test
  void countsTradeRowsThatCannotBeAttributedToAFund() {
    var withoutClientName = trade(TUK75, "DLA1", "IE00BFG1TM61", "Buy", "100.00", "2026-10-22");
    withoutClientName.remove("Client name");
    var unknownFund = trade(TUK75, "DLA2", "IE00BFG1TM61", "Buy", "200.00", "2026-10-22");
    unknownFund.put("Client name", "Some Other Fund");
    var withoutTotal = trade(TUV100, "DLA3", "IE00BFG1TM61", "Buy", "300.00", "2026-10-22");
    withoutTotal.remove("Total");
    var unreadableTotal = trade(TUK75, "DLA4", "IE00BFG1TM61", "Buy", "400.00", "2026-10-22");
    unreadableTotal.put("Total", "4OO.OO");
    var negativeTotal = trade(TUK00, "DLA5", "IE0005032192", "Sell", "-500.00", "2026-10-22");
    saveTradeList(
        NAV_DATE.plusDays(1),
        NAV_DATE,
        List.of(withoutClientName, unknownFund, withoutTotal, unreadableTotal, negativeTotal));

    OpenTrades openTrades = source.openTrades(NAV_DATE).orElseThrow();

    assertThat(openTrades.trades()).isEmpty();
    assertThat(openTrades.unattributedClientNames())
        .containsExactlyInAnyOrder("(no client name)", "Some Other Fund");
    assertThat(openTrades.fundsWithIncompleteRows())
        .containsExactlyInAnyOrder(TUV100, TUK75, TUK00);
  }

  @Test
  void hasNoTradeListForANavDateNoStoredReportDescribes() {
    saveTradeList(
        NAV_DATE,
        NAV_DATE.minusDays(1),
        List.of(trade(TUK75, "DLA1", "IE00BFG1TM61", "Buy", "100.00", "2026-10-22")));

    assertThat(source.openTrades(NAV_DATE)).isEmpty();
  }

  private void saveTradeList(
      LocalDate reportDate, LocalDate asOf, List<Map<String, Object>> tradeRows) {
    var rawData = new ArrayList<Map<String, Object>>();
    rawData.add(Map.of("Fund Management Company:", "As of:", "Tuleva Fondid AS", asOf.toString()));
    rawData.addAll(tradeRows);
    reportRepository.save(
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(reportDate)
            .rawData(rawData)
            .metadata(Map.of())
            .createdAt(Instant.parse("2026-10-21T06:00:00Z"))
            .build());
  }

  private Map<String, Object> trade(
      TulevaFund fund,
      String ourRef,
      String isin,
      String side,
      String total,
      String settlementDate) {
    var row = new HashMap<String, Object>();
    row.put("Our ref", ourRef);
    row.put("Client name", fund.getDisplayName());
    row.put("ISIN", isin);
    row.put("Quantity", new java.math.BigDecimal("100"));
    row.put("Price", new java.math.BigDecimal("10"));
    row.put("Total", new java.math.BigDecimal(total));
    row.put("Buy/Sell", side);
    row.put("Trade date", "2026-10-16T11:48:17Z");
    row.put("Settlement date", settlementDate);
    return row;
  }

  @TestConfiguration
  static class FixedClock {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-21T07:00:00Z"), ZoneId.of("Europe/Tallinn"));
    }
  }
}
