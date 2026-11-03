package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.UnitReceivable.Counted.ONCE_IT_ARRIVES;
import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;

record UnitReceivable(BigDecimal reported, BigDecimal receivedSinceReport, Counted counted) {

  enum Counted {
    BEFORE_IT_ARRIVES,
    ONCE_IT_ARRIVES
  }

  static final UnitReceivable NONE = new UnitReceivable(ZERO, ZERO, ONCE_IT_ARRIVES);

  BigDecimal dueOnTopOfReportCash() {
    return switch (counted) {
      case BEFORE_IT_ARRIVES -> reported;
      case ONCE_IT_ARRIVES -> reported.min(receivedSinceReport);
    };
  }
}
