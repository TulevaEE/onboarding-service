package ee.tuleva.onboarding.comparisons.returns.provider;

import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.FUND;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider;
import ee.tuleva.onboarding.comparisons.returns.ReturnCalculator;
import ee.tuleva.onboarding.comparisons.returns.Returns;
import ee.tuleva.onboarding.comparisons.returns.Returns.Return;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FundReturnProvider implements ReturnProvider {

  private final AccountOverviewProvider accountOverviewProvider;

  private final ReturnCalculator rateOfReturnCalculator;

  @Override
  public Returns getReturns(Person person, Instant startTime, Integer pillar) {
    AccountOverview accountOverview =
        accountOverviewProvider.getAccountOverview(person, startTime, pillar);

    List<Return> returns =
        getKeys().stream()
            .map(
                key -> {
                  var rateOfReturn = rateOfReturnCalculator.getReturn(accountOverview, key);
                  return new SimpleEntry<>(key, rateOfReturn);
                })
            .map(
                tuple ->
                    Return.builder()
                        .key(tuple.getKey())
                        .type(FUND)
                        .rate(tuple.getValue().rate())
                        .amount(tuple.getValue().amount())
                        .currency(tuple.getValue().currency())
                        .build())
            .toList();

    return Returns.builder()
        .from(
            accountOverview.sort().getFirstTransactionDate().orElse(accountOverview.getStartDate()))
        .returns(returns)
        .build();
  }

  @Override
  public List<String> getKeys() {
    return List.of(
        "EE3600019774",
        "EE3600019832",
        "EE3600019824",
        "EE3600019782",
        "EE3600019717",
        "EE3600019733",
        "EE3600098612",
        "EE3600019725",
        "EE3600019758",
        "EE3600019741",
        "EE3600019766");
  }
}
