package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.PendingTrade;
import ee.tuleva.onboarding.investment.transaction.PendingTrades;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebPendingTradeSourceTest {

  private static final LocalDate AS_OF = LocalDate.of(2026, 10, 20);
  private static final Instant TRADED = Instant.parse("2026-10-16T11:48:17Z");

  @Mock InvestmentReportService reportService;

  private SebPendingTradeSource source() {
    return new SebPendingTradeSource(
        new SebPendingReportFinder(reportService),
        new SebPendingTransactionExtractor(),
        new SebClientNameToFundResolver());
  }

  @Test
  void readsEveryFundsTradesFromTheReportWhoseAsOfIsTheDate() {
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, AS_OF))
        .willReturn(
            Optional.of(
                report(
                    AS_OF,
                    List.of(
                        row(TUK75, "DLA0000001", "IE00A", "2925.38", "115536", "Buy"),
                        row(TUK00, "DLA0000002", "IE00B", "100", "2000.50", "Sell")))));

    assertThat(source().asOf(AS_OF))
        .contains(
            new PendingTrades(
                List.of(
                    new PendingTrade(
                        TUK75,
                        "DLA0000001",
                        "IE00A",
                        BUY,
                        new BigDecimal("2925.38"),
                        new BigDecimal("115536"),
                        TRADED,
                        LocalDate.of(2026, 10, 22)),
                    new PendingTrade(
                        TUK00,
                        "DLA0000002",
                        "IE00B",
                        SELL,
                        new BigDecimal("100"),
                        new BigDecimal("2000.50"),
                        TRADED,
                        LocalDate.of(2026, 10, 22))),
                Map.of(),
                0));
  }

  @Test
  void findsAReportSentUpToThreeDaysAfterItsAsOfDate() {
    given(reportService.getReport(any(), any(), any())).willReturn(Optional.empty());
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, AS_OF.plusDays(3)))
        .willReturn(
            Optional.of(
                report(AS_OF, List.of(row(TUK75, "DLA0000001", "IE00A", "1", "10", "Buy")))));

    assertThat(source().asOf(AS_OF))
        .hasValueSatisfying(trades -> assertThat(trades.trades()).hasSize(1));
  }

  @Test
  void aReportFiledUnderTheDateButCarryingAnotherAsOfIsNotThatDatesReport() {
    given(reportService.getReport(any(), any(), any())).willReturn(Optional.empty());
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, AS_OF))
        .willReturn(
            Optional.of(
                report(
                    AS_OF.minusDays(1),
                    List.of(row(TUK75, "DLA0000001", "IE00A", "1", "10", "Buy")))));

    assertThat(source().asOf(AS_OF)).isEmpty();
  }

  @Test
  void anEmptyReportHasNoTradesAndIsComplete() {
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, AS_OF))
        .willReturn(Optional.of(report(AS_OF, List.of())));

    assertThat(source().asOf(AS_OF)).contains(new PendingTrades(List.of(), Map.of(), 0));
  }

  @Test
  void countsRowsItCannotUseAgainstTheirFundOrAgainstEveryFundWhenNoneCanBeNamed() {
    var unknownFund = new HashMap<>(row(TUK75, "DLA0000002", "IE00A", "1", "10", "Buy"));
    unknownFund.put("Client name", "Some Other Fund");
    var noTotal = new HashMap<>(row(TUK75, "DLA0000003", "IE00A", "1", "10", "Buy"));
    noTotal.remove("Total");
    var unknownSide = row(TUK75, "DLA0000004", "IE00A", "1", "10", "Swap");
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, AS_OF))
        .willReturn(
            Optional.of(
                report(
                    AS_OF,
                    List.of(
                        row(TUK75, "DLA0000001", "IE00A", "1", "10", "Buy"),
                        unknownFund,
                        noTotal,
                        unknownSide))));

    assertThat(source().asOf(AS_OF))
        .hasValueSatisfying(
            trades -> {
              assertThat(trades.trades())
                  .extracting(PendingTrade::reference)
                  .containsExactly("DLA0000001");
              assertThat(trades.unusableRowsByFund()).containsExactly(Map.entry(TUK75, 1));
              assertThat(trades.unattributableRows()).isEqualTo(2);
            });
  }

  @Test
  void doesNotUseATradeOutsideTheReportingCurrency() {
    var dollars = new HashMap<>(row(TUK75, "DLA0000001", "IE00A", "1", "10", "Buy"));
    dollars.put("Currency", "USD");
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, AS_OF))
        .willReturn(Optional.of(report(AS_OF, List.of(dollars))));

    assertThat(source().asOf(AS_OF)).contains(new PendingTrades(List.of(), Map.of(TUK75, 1), 0));
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

  private Map<String, Object> row(
      TulevaFund fund, String ourRef, String isin, String quantity, String total, String side) {
    return Map.of(
        "Our ref", ourRef,
        "Client name", fund.getDisplayName(),
        "ISIN", isin,
        "Quantity", quantity,
        "Total", total,
        "Buy/Sell", side,
        "Trade date", TRADED.toString(),
        "Settlement date", "2026-10-22",
        "Currency", "EUR");
  }
}
