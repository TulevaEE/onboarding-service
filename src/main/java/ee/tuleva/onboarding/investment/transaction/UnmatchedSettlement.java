package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.CalculationWarningType.UNMATCHED_TRADE_SETTLEMENT;
import static ee.tuleva.onboarding.investment.transaction.TransactionAuditPayloads.putIfPresent;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
record UnmatchedSettlement(TradeSettlementBooking booking) {

  enum Treatment {
    EXCLUDED_FROM_THE_BALANCE,
    NOT_NETTED
  }

  BigDecimal cash() {
    return Objects.requireNonNull(booking.cashAmount()).setScale(2, HALF_UP);
  }

  Treatment treatment() {
    return cash().signum() > 0 ? Treatment.EXCLUDED_FROM_THE_BALANCE : Treatment.NOT_NETTED;
  }

  BigDecimal excludedFromTheBalance() {
    return treatment() == Treatment.EXCLUDED_FROM_THE_BALANCE ? cash() : ZERO;
  }

  CalculationWarning warning() {
    String consequence =
        switch (treatment()) {
          case EXCLUDED_FROM_THE_BALANCE -> "its cash is left out of the balance";
          case NOT_NETTED -> "the report's payable for it stays reserved";
        };
    return new CalculationWarning(
        UNMATCHED_TRADE_SETTLEMENT,
        ("⚠️ SEB booked a trade settlement no registered trade explains, %s: fund=%s, isin=%s,"
                + " bookingDate=%s, amount=%s")
            .formatted(
                consequence,
                booking.fund(),
                booking.isin(),
                booking.bookingDate(),
                cash().toPlainString()));
  }

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>();
    putIfPresent(map, "brokerReference", booking.brokerReference());
    putIfPresent(map, "isin", booking.isin());
    map.put("bookingDate", booking.bookingDate().toString());
    map.put("cash", cash().toPlainString());
    map.put("treatment", treatment().name());
    return map;
  }
}
