package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.putIfPresent;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record CashSection(
    CashSourceReport sources,
    List<NettedForecast> forecasts,
    List<TradeSettlementBooking> unmatchedTradeSettlements,
    R16Reserve r16) {

  List<CalculationWarning> warnings() {
    return sources.warnings();
  }

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>(sources.payload());
    List<NettedForecast> inPlay = forecasts.stream().filter(NettedForecast::isInPlay).toList();
    if (!inPlay.isEmpty()) {
      map.put("forecasts", inPlay.stream().map(NettedForecast::payload).toList());
    }
    if (!unmatchedTradeSettlements.isEmpty()) {
      map.put(
          "unmatchedTradeSettlements",
          unmatchedTradeSettlements.stream().map(CashSection::unmatched).toList());
    }
    if (r16.basis() != R16Reserve.Basis.NO_R16_THIS_MONTH) {
      map.put("r16", r16.payload());
    }
    return map;
  }

  private static Map<String, Object> unmatched(TradeSettlementBooking booking) {
    Map<String, Object> map = new LinkedHashMap<>();
    putIfPresent(map, "brokerReference", booking.brokerReference());
    putIfPresent(map, "isin", booking.isin());
    map.put("bookingDate", booking.bookingDate().toString());
    BigDecimal cash = booking.cashAmount();
    putIfPresent(map, "cash", cash == null ? null : cash.setScale(2, HALF_UP).toPlainString());
    return map;
  }
}
