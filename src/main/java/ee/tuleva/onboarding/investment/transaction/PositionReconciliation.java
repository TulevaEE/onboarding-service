package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;

public interface PositionReconciliation {

  PositionReconciliationResult againstPositionReport(TulevaFund fund, LocalDate positionDate);
}
