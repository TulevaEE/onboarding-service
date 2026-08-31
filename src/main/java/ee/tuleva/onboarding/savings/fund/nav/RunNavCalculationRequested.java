package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;

public record RunNavCalculationRequested(List<TulevaFund> funds) {}
