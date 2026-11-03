package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.transaction.OpenTrade;
import ee.tuleva.onboarding.investment.transaction.OpenTradeSource;
import ee.tuleva.onboarding.investment.transaction.OpenTrades;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SebOpenTradeSource implements OpenTradeSource {

  private static final String NO_CLIENT_NAME = "(no client name)";

  private final SebTradeListLookup tradeListLookup;
  private final SebPendingTransactionExtractor extractor;
  private final SebClientNameToFundResolver fundResolver;

  @Override
  public Optional<OpenTrades> openTrades(LocalDate navDate) {
    return tradeListLookup.describing(navDate).map(this::openTrades);
  }

  private OpenTrades openTrades(InvestmentReport tradeList) {
    var extraction = extractor.extractWithDiagnostics(tradeList);
    List<ReadRow> rows =
        Stream.concat(
                extraction.rows().stream().map(this::read),
                extraction.malformedRows().stream().map(this::readMalformed))
            .toList();
    return new OpenTrades(
        rows.stream().map(ReadRow::trade).flatMap(Optional::stream).toList(),
        rows.stream()
            .filter(row -> row.fund() != null && row.trade().isEmpty())
            .map(ReadRow::fund)
            .collect(toSet()),
        rows.stream().filter(row -> row.fund() == null).map(ReadRow::clientName).toList());
  }

  private ReadRow read(SebPendingTransactionRow row) {
    var clientName = row.clientName();
    var fund = resolve(clientName);
    var side = row.side();
    var total = row.total();
    if (fund == null || side == null || total == null || total.signum() <= 0) {
      return new ReadRow(fund, clientName(clientName), Optional.empty());
    }
    return new ReadRow(
        fund,
        clientName(clientName),
        Optional.of(
            new OpenTrade(
                fund,
                side,
                total,
                row.ourRef(),
                row.isin(),
                row.settlementDate(),
                row.quantity(),
                row.tradeDate())));
  }

  private ReadRow readMalformed(Map<String, Object> raw) {
    var clientName = raw.get("Client name");
    var name = clientName == null ? null : clientName.toString();
    return new ReadRow(resolve(name), clientName(name), Optional.empty());
  }

  private @Nullable TulevaFund resolve(@Nullable String clientName) {
    return fundResolver.resolve(clientName).orElse(null);
  }

  private static String clientName(@Nullable String clientName) {
    return clientName == null || clientName.isBlank() ? NO_CLIENT_NAME : clientName;
  }

  private record ReadRow(@Nullable TulevaFund fund, String clientName, Optional<OpenTrade> trade) {}
}
