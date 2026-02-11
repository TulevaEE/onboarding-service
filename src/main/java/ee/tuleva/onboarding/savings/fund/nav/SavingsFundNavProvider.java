package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.investment.TulevaFund.TKF100;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsFundNavProvider {

  private final FundValueRepository fundValueRepository;

  public BigDecimal getCurrentNav() {
    return fundValueRepository
        .findLastValueForFund(TKF100.getIsin())
        .map(FundValue::value)
        .orElse(BigDecimal.ONE);
  }
}
