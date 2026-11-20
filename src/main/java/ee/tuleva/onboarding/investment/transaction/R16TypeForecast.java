package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.ledger.RegistrarPayoutReason;
import java.math.BigDecimal;
import org.jspecify.annotations.NullMarked;

@NullMarked
record R16TypeForecast(
    RegistrarPayoutReason type, BigDecimal units, BigDecimal eur, Status status) {

  enum Status {
    NOTHING_FORECAST,
    BOOKED,
    NOT_BOOKED
  }

  boolean stillToPay() {
    return status == Status.NOT_BOOKED;
  }
}
