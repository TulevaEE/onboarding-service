package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Optional;

@FunctionalInterface
public interface NavPublicationGate {

  Optional<String> check(TulevaFund fund, LocalDate navDate);
}
