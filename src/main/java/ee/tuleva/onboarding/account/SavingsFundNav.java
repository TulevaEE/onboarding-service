package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface SavingsFundNav {

  BigDecimal getDisplayNav(TulevaFund fund);

  LocalDate safeMaxNavDate();
}
