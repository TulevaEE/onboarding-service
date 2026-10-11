package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.ExecutedPrice;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebExecutedPriceSourceTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 8, 26);

  @Mock InvestmentReportService reportService;

  private SebExecutedPriceSource source() {
    return new SebExecutedPriceSource(
        reportService, new SebPendingTransactionExtractor(), new SebClientNameToFundResolver());
  }

  @Test
  void takesTheSellPriceForTheFundFromTheReportWhoseAsOfIsTheNavDate() {
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, NAV_DATE))
        .willReturn(Optional.of(report(NAV_DATE, List.of(sell(TUK75, "IE00A", "1000", "15.5")))));

    assertThat(sellsFor(TUK75))
        .containsOnlyKeys("IE00A")
        .extractingByKey("IE00A")
        .satisfies(
            executed -> {
              assertThat(executed.price()).isEqualByComparingTo(new BigDecimal("15.5"));
              assertThat(executed.quantity()).isEqualByComparingTo(new BigDecimal("1000"));
              assertThat(executed.tradeDate()).isEqualTo(NAV_DATE);
            });
  }

  // Partial fills of one order arrive as separate rows, so the mark has to be the volume-weighted
  // price - taking the first row's price values the whole holding at part of the trade.
  @Test
  void weighsPartialFillsOfTheSameHoldingByVolume() {
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, NAV_DATE))
        .willReturn(
            Optional.of(
                report(
                    NAV_DATE,
                    List.of(
                        sell(TUK75, "IE00A", "1000", "10"), sell(TUK75, "IE00A", "3000", "14")))));

    assertThat(sellsFor(TUK75))
        .extractingByKey("IE00A")
        .satisfies(executed -> assertThat(executed.price()).isEqualByComparingTo("13"));
  }

  // The report carries every fund and both sides. A buy is not an exit, and another fund's sell
  // belongs to that fund's reconciliation.
  @Test
  void ignoresBuysAndOtherFundsRows() {
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, NAV_DATE))
        .willReturn(
            Optional.of(
                report(
                    NAV_DATE,
                    List.of(
                        buy(TUK75, "IE00B", "500", "20"), sell(TUV100, "IE00C", "700", "30")))));

    assertThat(sellsFor(TUK75)).isEmpty();
  }

  // The file lands after the day it describes, and a re-send lands later still, so the report is
  // found by its own As of rather than by the date in its name.
  @Test
  void findsAReportSentAfterTheNavDateItDescribes() {
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, NAV_DATE))
        .willReturn(Optional.empty());
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, NAV_DATE.plusDays(1)))
        .willReturn(Optional.of(report(NAV_DATE, List.of(sell(TUK75, "IE00A", "1000", "15.5")))));

    assertThat(sellsFor(TUK75)).containsOnlyKeys("IE00A");
  }

  // A report whose As of is a different day describes different trades, so it must not be used.
  @Test
  void ignoresAReportDescribingAnotherDay() {
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, NAV_DATE))
        .willReturn(
            Optional.of(
                report(NAV_DATE.minusDays(1), List.of(sell(TUK75, "IE00A", "1000", "15.5")))));

    assertThat(sellsFor(TUK75)).isEmpty();
  }

  @Test
  void hasNoPricesWhenNoReportCoversTheNavDate() {
    assertThat(sellsFor(TUK75)).isEmpty();
  }

  private Map<String, ExecutedPrice> sellsFor(TulevaFund fund) {
    return source().executedSellPricesByFund(NAV_DATE).getOrDefault(fund, Map.of());
  }

  private InvestmentReport report(LocalDate asOf, List<Map<String, Object>> rows) {
    var rawData = new ArrayList<Map<String, Object>>();
    rawData.add(Map.of("Fund Management Company:", "As of:", "Tuleva Fondid AS", asOf.toString()));
    rawData.addAll(rows);
    return InvestmentReport.builder()
        .provider(SEB)
        .reportType(PENDING_TRANSACTIONS)
        .reportDate(asOf)
        .rawData(rawData)
        .build();
  }

  private Map<String, Object> sell(TulevaFund fund, String isin, String quantity, String price) {
    return row(fund, isin, quantity, price, "SELL");
  }

  private Map<String, Object> buy(TulevaFund fund, String isin, String quantity, String price) {
    return row(fund, isin, quantity, price, "BUY");
  }

  private Map<String, Object> row(
      TulevaFund fund, String isin, String quantity, String price, String side) {
    return Map.of(
        "Our ref", "REF-" + isin + "-" + quantity,
        "Client name", fund.getDisplayName(),
        "ISIN", isin,
        "Quantity", quantity,
        "Price", price,
        "Buy/Sell", side,
        "Trade date", NAV_DATE.atStartOfDay(ZoneOffset.UTC).toInstant().toString());
  }
}
