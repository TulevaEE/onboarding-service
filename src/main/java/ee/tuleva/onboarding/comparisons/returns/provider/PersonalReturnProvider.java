package ee.tuleva.onboarding.comparisons.returns.provider;

import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.PERSONAL;

import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider;
import ee.tuleva.onboarding.comparisons.returns.ReturnCalculator;
import ee.tuleva.onboarding.comparisons.returns.ReturnDto;
import ee.tuleva.onboarding.comparisons.returns.Returns;
import ee.tuleva.onboarding.comparisons.returns.Returns.Return;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PersonalReturnProvider implements ReturnProvider {

  public static final String SECOND_PILLAR = "SECOND_PILLAR";
  public static final String THIRD_PILLAR = "THIRD_PILLAR";

  private final AccountOverviewProvider accountOverviewProvider;

  private final ReturnCalculator rateOfReturnCalculator;

  @Override
  public Returns getReturns(ReturnCalculationParameters parameters) {

    AccountOverview accountOverview =
        accountOverviewProvider.getAccountOverview(
            parameters.person(), parameters.startTime(), parameters.endTime(), parameters.pillar());
    ReturnDto aReturn = rateOfReturnCalculator.getReturn(accountOverview);

    var returns =
        List.of(
            Return.builder()
                .key(getKey(parameters.pillar()))
                .type(PERSONAL)
                .rate(aReturn.rate())
                .amount(aReturn.amount())
                .paymentsSum(aReturn.paymentsSum())
                .currency(aReturn.currency())
                .from(aReturn.from())
                .build());

    return Returns.builder().returns(returns).build();
  }

  @Override
  public List<String> getKeys() {
    return pillars();
  }

  @NotNull
  private static List<String> pillars() {
    return List.of(SECOND_PILLAR, THIRD_PILLAR);
  }

  private String getKey(Integer pillar) {
    if (pillar == 2) {
      return SECOND_PILLAR;
    } else if (pillar == 3) {
      return THIRD_PILLAR;
    }
    throw new IllegalArgumentException("Unknown pillar: " + pillar);
  }
}
