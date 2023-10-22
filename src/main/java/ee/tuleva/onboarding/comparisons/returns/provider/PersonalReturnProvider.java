package ee.tuleva.onboarding.comparisons.returns.provider;

import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.PERSONAL;

import ee.tuleva.onboarding.auth.principal.Person;
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
public class PersonalReturnProvider implements ReturnProvider {

  public static final String SECOND_PILLAR = "SECOND_PILLAR";
  public static final String THIRD_PILLAR = "THIRD_PILLAR";

  private final AccountOverviewProvider accountOverviewProvider;

  private final ReturnCalculator rateOfReturnCalculator;

  @Override
  public Returns getReturns(Person person, Instant startTime, Integer pillar) {
    AccountOverview accountOverview =
        accountOverviewProvider.getAccountOverview(person, startTime, pillar);
    ReturnDto rateOfReturn = rateOfReturnCalculator.getReturn(accountOverview);

    Return aReturn =
        Return.builder()
            .key(getKey(pillar))
            .type(PERSONAL)
            .rate(rateOfReturn.rate())
            .amount(rateOfReturn.amount())
            .currency(rateOfReturn.currency())
            .build();

    return Returns.builder()
        .from(
            accountOverview.sort().getFirstTransactionDate().orElse(accountOverview.getStartDate()))
        .returns(List.of(aReturn))
        .build();
  }

  @Override
  public List<String> getKeys() {
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
