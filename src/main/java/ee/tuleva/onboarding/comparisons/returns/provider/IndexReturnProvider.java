package ee.tuleva.onboarding.comparisons.returns.provider;

import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.INDEX;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.CPIValueRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EPIFundValueRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider;
import ee.tuleva.onboarding.comparisons.returns.RateOfReturnCalculator;
import ee.tuleva.onboarding.comparisons.returns.Returns;
import ee.tuleva.onboarding.comparisons.returns.Returns.Return;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IndexReturnProvider implements ReturnProvider {

  private static final String EPI = EPIFundValueRetriever.KEY;

  private static final String UNION_STOCK_INDEX = UnionStockIndexRetriever.KEY;

  private static final String CPI = CPIValueRetriever.KEY;

  private final AccountOverviewProvider accountOverviewProvider;

  private final RateOfReturnCalculator rateOfReturnCalculator;

  @Override
  public Returns getReturns(Person person, Instant startTime, Integer pillar) {
    AccountOverview accountOverview =
        accountOverviewProvider.getAccountOverview(person, startTime, pillar);

    List<Return> returns =
        getKeys().stream()
            .map(
                key ->
                    new AbstractMap.SimpleEntry<>(
                        key, rateOfReturnCalculator.getRateOfReturn(accountOverview, key)))
            .map(
                tuple ->
                    Return.builder()
                        .key(tuple.getKey())
                        .type(INDEX)
                        .value(tuple.getValue())
                        .build())
            .collect(toList());

    return Returns.builder()
        .from(startTime.atZone(ZoneOffset.UTC).toLocalDate()) // TODO: Get real start time
        .returns(returns)
        .build();
  }

  @Override
  public List<String> getKeys() {
    return Arrays.asList(EPI, UNION_STOCK_INDEX, CPI);
  }
}
