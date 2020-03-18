package ee.tuleva.onboarding.comparisons;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EPIFundValueRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.WorldIndexValueRetriever;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider;
import ee.tuleva.onboarding.comparisons.returns.RateOfReturnCalculator;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundComparisonCalculatorService {

  private final AccountOverviewProvider accountOverviewProvider;
  private final RateOfReturnCalculator rateOfReturnCalculator;

  public FundComparison calculateComparison(Person person, Instant startTime, Integer pillar) {
    AccountOverview overview =
        accountOverviewProvider.getAccountOverview(person, startTime, pillar);

    double actualRateOfReturn = rateOfReturnCalculator.getRateOfReturn(overview);
    double estonianAverageRateOfReturn =
        rateOfReturnCalculator.getRateOfReturn(overview, EPIFundValueRetriever.KEY);
    double marketAverageRateOfReturn =
        rateOfReturnCalculator.getRateOfReturn(overview, WorldIndexValueRetriever.KEY);

    return FundComparison.builder()
        .actualReturnPercentage(actualRateOfReturn)
        .estonianAverageReturnPercentage(estonianAverageRateOfReturn)
        .marketAverageReturnPercentage(marketAverageRateOfReturn)
        .build();
  }
}
