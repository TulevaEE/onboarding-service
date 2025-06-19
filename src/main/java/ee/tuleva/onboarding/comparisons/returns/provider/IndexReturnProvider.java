package ee.tuleva.onboarding.comparisons.returns.provider;

import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.INDEX;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.CpiValueRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EpiFundValueRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever;
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
public class IndexReturnProvider implements ReturnProvider {

  private static final String EPI = EpiFundValueRetriever.KEY;

  private static final String UNION_STOCK_INDEX = UnionStockIndexRetriever.KEY;

  private static final String CPI = CpiValueRetriever.KEY;

  private final AccountOverviewProvider accountOverviewProvider;

  private final ReturnCalculator rateOfReturnCalculator;

  @Override
  public Returns getReturns(ReturnCalculationParameters parameters) {
    AccountOverview accountOverview =
        accountOverviewProvider.getAccountOverview(
            parameters.person(), parameters.startTime(), parameters.endTime(), parameters.pillar());

    List<Return> returns =
        comparisonIndexes().stream()
            .map(
                comparisonIndex -> {
                  ReturnDto aReturn =
                      rateOfReturnCalculator.getSimulatedReturn(accountOverview, comparisonIndex);
                  return new Tuple(comparisonIndex, aReturn);
                })
            .map(
                tuple ->
                    Return.builder()
                        .key(tuple.comparisonIndex)
                        .type(INDEX)
                        .rate(tuple.aReturn.rate())
                        .amount(tuple.aReturn.amount())
                        .paymentsSum(tuple.aReturn.paymentsSum())
                        .currency(tuple.aReturn.currency())
                        .from(tuple.aReturn.from())
                        .build())
            .toList();

    return Returns.builder().returns(returns).build();
  }

  private record Tuple(String comparisonIndex, ReturnDto aReturn) {}

  @Override
  public List<String> getKeys() {
    return comparisonIndexes();
  }

  @NotNull
  private static List<String> comparisonIndexes() {
    return List.of(EPI, UNION_STOCK_INDEX, CPI);
  }
}
