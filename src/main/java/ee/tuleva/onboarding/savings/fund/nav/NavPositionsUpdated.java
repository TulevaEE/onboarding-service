package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;

public record NavPositionsUpdated(TulevaFund fund, LocalDate navDate, int changedRows) {}
