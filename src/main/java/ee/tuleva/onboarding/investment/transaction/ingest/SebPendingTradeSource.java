package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.transaction.PendingTrade;
import ee.tuleva.onboarding.investment.transaction.PendingTradeSource;
import ee.tuleva.onboarding.investment.transaction.PendingTrades;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SebPendingTradeSource implements PendingTradeSource {

  private static final String REPORTING_CURRENCY = "EUR";

  private final SebPendingReportFinder reportFinder;
  private final SebPendingTransactionExtractor extractor;
  private final SebClientNameToFundResolver fundResolver;

  @Override
  public Optional<PendingTrades> asOf(LocalDate asOfDate) {
    return reportFinder.asOf(asOfDate).map(this::pendingTrades);
  }

  private PendingTrades pendingTrades(InvestmentReport report) {
    var extraction = extractor.extractWithDiagnostics(report);
    var trades = new ArrayList<PendingTrade>();
    Map<TulevaFund, Integer> unusableRowsByFund = new EnumMap<>(TulevaFund.class);
    int unattributableRows = extraction.malformedCount();
    for (var row : extraction.rows()) {
      var fund = fundResolver.resolve(row.clientName());
      if (fund.isEmpty()) {
        unattributableRows++;
        continue;
      }
      pendingTrade(fund.get(), row)
          .ifPresentOrElse(
              trades::add, () -> unusableRowsByFund.merge(fund.get(), 1, Integer::sum));
    }
    return new PendingTrades(
        List.copyOf(trades), Map.copyOf(unusableRowsByFund), unattributableRows);
  }

  private Optional<PendingTrade> pendingTrade(TulevaFund fund, SebPendingTransactionRow row) {
    var isin = row.isin();
    var side = row.side();
    var quantity = row.quantity();
    var total = row.total();
    var tradeDate = row.tradeDate();
    var currency = row.currency();
    if (isin == null
        || side == null
        || quantity == null
        || total == null
        || tradeDate == null
        || (currency != null && !REPORTING_CURRENCY.equals(currency))) {
      return Optional.empty();
    }
    return Optional.of(
        new PendingTrade(
            fund, row.ourRef(), isin, side, quantity, total, tradeDate, row.settlementDate()));
  }
}
