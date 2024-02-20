package ee.tuleva.onboarding.comparisons.returns.provider;

import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.FUND;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider;
import ee.tuleva.onboarding.comparisons.returns.ReturnCalculator;
import ee.tuleva.onboarding.comparisons.returns.ReturnDto;
import ee.tuleva.onboarding.comparisons.returns.Returns;
import ee.tuleva.onboarding.comparisons.returns.Returns.Return;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FundReturnProvider implements ReturnProvider {

  private final AccountOverviewProvider accountOverviewProvider;

  private final ReturnCalculator rateOfReturnCalculator;

  private final FundValueRepository fundValueRepository;

  @Override
  public Returns getReturns(Person person, Instant startTime, Integer pillar) {
    AccountOverview accountOverview =
        accountOverviewProvider.getAccountOverview(person, startTime, pillar);

    List<Return> returns =
        fundIsins().stream()
            .map(
                fundIsin -> {
                  ReturnDto aReturn =
                      rateOfReturnCalculator.getSimulatedReturn(accountOverview, fundIsin);
                  return new Tuple(fundIsin, aReturn);
                })
            .map(
                tuple ->
                    Return.builder()
                        .key(tuple.fundIsin)
                        .type(FUND)
                        .rate(tuple.aReturn.rate())
                        .amount(tuple.aReturn.amount())
                        .paymentsSum(tuple.aReturn.paymentsSum())
                        .currency(tuple.aReturn.currency())
                        .from(tuple.aReturn.from())
                        .build())
            .toList();

    return Returns.builder().returns(returns).build();
  }

  private record Tuple(String fundIsin, ReturnDto aReturn) {}

  @Override
  public List<String> getKeys() {
    return fundIsins();
  }

  private List<String> fundIsins() {
    return fundValueRepository.findActiveFundKeys();
  }
}
