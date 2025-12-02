package ee.tuleva.onboarding.savings.fund.nav;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class SavingsFundNavProvider {

  public BigDecimal getCurrentNav() {
    // TODO: Fetch the actual latest NAV from the index_values table, once it's available there
    return BigDecimal.ONE;
  }
}
