package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.InvestmentPositionCalculation;
import ee.tuleva.onboarding.investment.portfolio.Provider;
import ee.tuleva.onboarding.investment.portfolio.ProviderLimit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class ProviderLimitChecker {

  List<ProviderBreach> check(
      TulevaFund fund,
      List<InvestmentPositionCalculation> positions,
      BigDecimal totalNav,
      Map<String, Provider> isinToProvider,
      List<ProviderLimit> limits) {

    if (totalNav.signum() == 0) {
      return List.of();
    }

    Map<Provider, BigDecimal> providerWeights =
        positions.stream()
            .filter(p -> isinToProvider.containsKey(p.getIsin()))
            .collect(
                Collectors.groupingBy(
                    p -> isinToProvider.get(p.getIsin()),
                    Collectors.reducing(
                        BigDecimal.ZERO,
                        InvestmentPositionCalculation::getCalculatedMarketValue,
                        BigDecimal::add)));

    Map<Provider, ProviderLimit> limitsByProvider =
        limits.stream().collect(Collectors.toMap(ProviderLimit::getProvider, Function.identity()));

    return providerWeights.entrySet().stream()
        .filter(e -> limitsByProvider.containsKey(e.getKey()))
        .map(
            e -> {
              var provider = e.getKey();
              var limit = limitsByProvider.get(provider);
              var actualPercent =
                  e.getValue()
                      .multiply(BigDecimal.valueOf(100))
                      .divide(totalNav, 4, RoundingMode.HALF_UP);
              var severity = determineSeverity(actualPercent, limit);
              return new ProviderBreach(
                  fund,
                  provider,
                  actualPercent,
                  limit.getSoftLimitPercent(),
                  limit.getHardLimitPercent(),
                  severity);
            })
        .toList();
  }

  private BreachSeverity determineSeverity(BigDecimal actualPercent, ProviderLimit limit) {
    if (actualPercent.compareTo(limit.getHardLimitPercent()) > 0) {
      return HARD;
    }
    if (actualPercent.compareTo(limit.getSoftLimitPercent()) > 0) {
      return SOFT;
    }
    return OK;
  }
}
