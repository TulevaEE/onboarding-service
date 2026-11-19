package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Set;

public interface PositionReconciliation {

  PositionReconciliationResult againstPositionReport(
      TulevaFund fund, LocalDate positionDate, Set<String> excludedIsins);
}
