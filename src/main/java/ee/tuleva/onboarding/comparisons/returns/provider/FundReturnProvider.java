package ee.tuleva.onboarding.comparisons.returns.provider;

import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.FUND;
import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE;

import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider;
import ee.tuleva.onboarding.comparisons.returns.ReturnCalculator;
import ee.tuleva.onboarding.comparisons.returns.ReturnDto;
import ee.tuleva.onboarding.comparisons.returns.Returns;
import ee.tuleva.onboarding.comparisons.returns.Returns.Return;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FundReturnProvider implements ReturnProvider {

  private final AccountOverviewProvider accountOverviewProvider;

  private final ReturnCalculator rateOfReturnCalculator;

  private final FundRepository fundRepository;

  @Override
  public Returns getReturns(ReturnCalculationParameters parameters) {

    AccountOverview accountOverview =
        accountOverviewProvider.getAccountOverview(
            parameters.person(), parameters.startTime(), parameters.pillar());

    List<Return> returns =
        fundIsins().stream()
            .filter(it -> parameters.keys().contains(it))
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
    return fundRepository.findAllByStatus(ACTIVE).stream().map(Fund::getIsin).toList();
  }
}
