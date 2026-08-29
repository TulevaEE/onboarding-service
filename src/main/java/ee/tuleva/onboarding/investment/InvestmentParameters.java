package ee.tuleva.onboarding.investment;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface InvestmentParameters {

  BigDecimal navImpactVolumeThreshold(LocalDate asOf);
}
