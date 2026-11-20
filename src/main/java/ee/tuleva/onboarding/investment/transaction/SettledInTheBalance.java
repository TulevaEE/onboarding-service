package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import org.jspecify.annotations.NullMarked;

@NullMarked
record SettledInTheBalance(TradeSettlementBooking booking, Carrier carrier) {

  enum Carrier {
    REPORT_ROWS,
    PENDING_ORDER
  }
}
