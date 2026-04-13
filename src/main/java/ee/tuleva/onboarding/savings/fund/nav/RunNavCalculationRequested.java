package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.List;

public record RunNavCalculationRequested(List<TulevaFund> funds, boolean lastOfDay) {
  public RunNavCalculationRequested(List<TulevaFund> funds) {
    this(funds, false);
  }
}
