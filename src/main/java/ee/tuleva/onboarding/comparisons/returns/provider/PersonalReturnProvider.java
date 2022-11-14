package ee.tuleva.onboarding.comparisons.returns.provider;

import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.PERSONAL;
import static java.util.Collections.singletonList;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider;
import ee.tuleva.onboarding.comparisons.returns.RateOfReturnCalculator;
import ee.tuleva.onboarding.comparisons.returns.ReturnRateAndAmount;
import ee.tuleva.onboarding.comparisons.returns.Returns;
import ee.tuleva.onboarding.comparisons.returns.Returns.Return;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PersonalReturnProvider implements ReturnProvider {

  public static final String SECOND_PILLAR = "SECOND_PILLAR";
  public static final String THIRD_PILLAR = "THIRD_PILLAR";

  private final AccountOverviewProvider accountOverviewProvider;

  private final RateOfReturnCalculator rateOfReturnCalculator;

  @Override
  public Returns getReturns(Person person, Instant startTime, Integer pillar) {
    AccountOverview accountOverview =
        accountOverviewProvider.getAccountOverview(person, startTime, pillar);
    ReturnRateAndAmount returnRateAndAmount = rateOfReturnCalculator.getReturn(accountOverview);

    Return aReturn =
        Return.builder()
            .key(getKey(pillar))
            .type(PERSONAL)
            .rate(returnRateAndAmount.rate())
            .amount(returnRateAndAmount.amount())
            .currency(returnRateAndAmount.currency())
            .build();

    return Returns.builder()
        .from(startTime.atZone(ZoneOffset.UTC).toLocalDate()) // TODO: Get real start time
        .returns(singletonList(aReturn))
        .build();
  }

  @Override
  public List<String> getKeys() {
    return Arrays.asList(SECOND_PILLAR, THIRD_PILLAR);
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
