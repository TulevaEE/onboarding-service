package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
import org.jspecify.annotations.NullMarked;

@NullMarked
sealed interface SettledInTheBalance {

  TradeSettlementBooking booking();

  record AgainstTheReportRows(TradeSettlementBooking booking) implements SettledInTheBalance {}

  record CarriedByAPendingFill(TradeSettlementBooking booking, Long executionId)
      implements SettledInTheBalance {}

  record Unmatched(TradeSettlementBooking booking) implements SettledInTheBalance {}
}
