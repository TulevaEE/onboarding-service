package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsFundNavProvider {

  private final FundValueRepository fundValueRepository;
  private final SavingsFundConfiguration configuration;

  public BigDecimal getCurrentNav() {
    return fundValueRepository
        .findLastValueForFund(configuration.getIsin())
        .map(FundValue::value)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "NAV not found for savings fund: isin=" + configuration.getIsin()));
  }
}
