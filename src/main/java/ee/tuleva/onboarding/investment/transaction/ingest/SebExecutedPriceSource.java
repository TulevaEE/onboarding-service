package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableMap;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.report.SebReportHeaders;
import ee.tuleva.onboarding.investment.transaction.ExecutedPrice;
import ee.tuleva.onboarding.investment.transaction.ExecutedPriceSource;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SebExecutedPriceSource implements ExecutedPriceSource {

  private static final int PRICE_SCALE = 8;
  private static final int SEND_LAG_DAYS = 3;
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final InvestmentReportService reportService;
  private final SebPendingTransactionExtractor extractor;
  private final SebClientNameToFundResolver fundResolver;

  @Override
  public Map<TulevaFund, Map<String, ExecutedPrice>> executedSellPricesByFund(LocalDate navDate) {
    Optional<InvestmentReport> report = reportFor(navDate);
    if (report.isEmpty()) {
      log.info("No SEB pending transactions report for nav date: navDate={}", navDate);
      return Map.of();
    }
    var sells = new ArrayList<Sell>();
    for (SebPendingTransactionRow row : extractor.extract(report.get())) {
      sell(row).ifPresent(sells::add);
    }
    return sells.stream()
        .collect(
            groupingBy(
                Sell::fund,
                collectingAndThen(
                    groupingBy(Sell::isin), SebExecutedPriceSource::volumeWeightedByIsin)));
  }

  private static Map<String, ExecutedPrice> volumeWeightedByIsin(
      Map<String, List<Sell>> sellsByIsin) {
    return sellsByIsin.entrySet().stream()
        .collect(toUnmodifiableMap(Map.Entry::getKey, entry -> volumeWeighted(entry.getValue())));
  }

  private Optional<InvestmentReport> reportFor(LocalDate navDate) {
    for (int offset = 0; offset <= SEND_LAG_DAYS; offset++) {
      Optional<InvestmentReport> candidate =
          reportService
              .getReport(SEB, PENDING_TRANSACTIONS, navDate.plusDays(offset))
              .filter(report -> navDate.equals(SebReportHeaders.asOfDate(report)));
      if (candidate.isPresent()) {
        return candidate;
      }
    }
    return Optional.empty();
  }

  private Optional<Sell> sell(SebPendingTransactionRow row) {
    var isin = row.isin();
    var quantity = row.quantity();
    var price = row.price();
    var tradeDate = row.tradeDate();
    var fund = fundResolver.resolve(row.clientName());
    if (isin == null || quantity == null || price == null || tradeDate == null || fund.isEmpty()) {
      return Optional.empty();
    }
    if (row.side() != SELL || quantity.signum() <= 0) {
      return Optional.empty();
    }
    return Optional.of(
        new Sell(fund.get(), isin, quantity, price, tradeDate.atZone(TALLINN).toLocalDate()));
  }

  private static ExecutedPrice volumeWeighted(List<Sell> sells) {
    var quantity = sells.stream().map(Sell::quantity).reduce(ZERO, BigDecimal::add);
    var consideration =
        sells.stream()
            .map(sell -> sell.quantity().multiply(sell.price()))
            .reduce(ZERO, BigDecimal::add);
    var tradeDate = sells.stream().map(Sell::tradeDate).max(naturalOrder()).orElseThrow();
    return new ExecutedPrice(
        consideration.divide(quantity, PRICE_SCALE, HALF_UP), quantity, tradeDate);
  }

  private record Sell(
      TulevaFund fund, String isin, BigDecimal quantity, BigDecimal price, LocalDate tradeDate) {}
}
