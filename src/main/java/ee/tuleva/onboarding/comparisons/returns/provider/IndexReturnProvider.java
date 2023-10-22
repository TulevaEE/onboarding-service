package ee.tuleva.onboarding.comparisons.returns.provider;

import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.INDEX;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.CPIValueRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EPIFundValueRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider;
import ee.tuleva.onboarding.comparisons.returns.ReturnCalculator;
import ee.tuleva.onboarding.comparisons.returns.ReturnDto;
import ee.tuleva.onboarding.comparisons.returns.Returns;
import ee.tuleva.onboarding.comparisons.returns.Returns.Return;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
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

  private final ReturnCalculator rateOfReturnCalculator;

  @Override
  public Returns getReturns(Person person, Instant startTime, Integer pillar) {
    AccountOverview accountOverview =
        accountOverviewProvider.getAccountOverview(person, startTime, pillar);

    List<Return> returns =
        getKeys().stream()
            .map(
                key -> {
                  ReturnDto rateOfReturn = rateOfReturnCalculator.getReturn(accountOverview, key);
                  return new SimpleEntry<>(key, rateOfReturn);
                })
            .map(
                tuple ->
                    Return.builder()
                        .key(tuple.getKey())
                        .type(INDEX)
                        .rate(tuple.getValue().rate())
                        .amount(tuple.getValue().amount())
                        .currency(tuple.getValue().currency())
                        .from(tuple.getValue().from())
                        .build())
            .collect(toList());

    return Returns.builder().returns(returns).build();
  }

  @Override
  public List<String> getKeys() {
    return List.of(EPI, UNION_STOCK_INDEX, CPI);
  }
}
