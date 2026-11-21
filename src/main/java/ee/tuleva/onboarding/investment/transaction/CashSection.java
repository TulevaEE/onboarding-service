package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record CashSection(
    CashSourceReport sources,
    List<NettedForecast> forecasts,
    List<UnmatchedSettlement> unmatchedTradeSettlements,
    R16Reserve r16) {

  List<CalculationWarning> warnings() {
    return Stream.concat(
            sources.warnings().stream(),
            unmatchedTradeSettlements.stream().map(UnmatchedSettlement::warning))
        .toList();
  }

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>(sources.payload());
    BigDecimal excluded =
        unmatchedTradeSettlements.stream()
            .map(UnmatchedSettlement::excludedFromTheBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    sources
        .gatewayBalanceInUse()
        .filter(balance -> excluded.signum() != 0)
        .ifPresent(
            balance ->
                map.put("balanceUsed", balance.balance().subtract(excluded).toPlainString()));
    List<NettedForecast> inPlay = forecasts.stream().filter(NettedForecast::isInPlay).toList();
    if (!inPlay.isEmpty()) {
      map.put("forecasts", inPlay.stream().map(NettedForecast::payload).toList());
    }
    if (!unmatchedTradeSettlements.isEmpty()) {
      map.put(
          "unmatchedTradeSettlements",
          unmatchedTradeSettlements.stream().map(UnmatchedSettlement::payload).toList());
    }
    if (r16.basis() != R16Reserve.Basis.NO_R16_THIS_MONTH) {
      map.put("r16", r16.payload());
    }
    return map;
  }
}
