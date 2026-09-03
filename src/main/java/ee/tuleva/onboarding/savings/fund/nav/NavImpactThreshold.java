package ee.tuleva.onboarding.savings.fund.nav;

import java.math.BigDecimal;
import java.time.LocalDate;

@FunctionalInterface
public interface NavImpactThreshold {

  BigDecimal navImpactVolumeThreshold(LocalDate asOf);
}
