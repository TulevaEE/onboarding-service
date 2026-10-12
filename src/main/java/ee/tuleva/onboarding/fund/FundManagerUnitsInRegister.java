package ee.tuleva.onboarding.fund;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface FundManagerUnitsInRegister {

  Optional<BigDecimal> fundManagerUnitsOn(String isin, LocalDate date);
}
