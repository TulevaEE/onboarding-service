package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;

record UnitReceivable(
    BigDecimal reported, BigDecimal receivedSinceReport, boolean countedBeforeItArrives) {

  static final UnitReceivable NONE = new UnitReceivable(ZERO, ZERO, false);

  BigDecimal dueOnTopOfReportCash() {
    return countedBeforeItArrives ? reported : reported.min(receivedSinceReport);
  }
}
