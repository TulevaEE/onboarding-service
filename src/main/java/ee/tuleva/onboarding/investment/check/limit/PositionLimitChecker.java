package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.InvestmentPositionCalculation;
import ee.tuleva.onboarding.investment.portfolio.PositionLimit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class PositionLimitChecker {

  List<PositionBreach> check(
      TulevaFund fund,
      List<InvestmentPositionCalculation> positions,
      BigDecimal totalNav,
      List<PositionLimit> limits) {

    if (totalNav.signum() == 0) {
      return List.of();
    }

    var result = new ArrayList<>(checkIndividualLimits(fund, positions, totalNav, limits));
    result.addAll(checkIndexGroupLimits(fund, positions, totalNav, limits));
    return List.copyOf(result);
  }

  private List<PositionBreach> checkIndividualLimits(
      TulevaFund fund,
      List<InvestmentPositionCalculation> positions,
      BigDecimal totalNav,
      List<PositionLimit> limits) {

    Map<String, PositionLimit> limitsByIsin =
        limits.stream()
            .filter(l -> l.getIsin() != null)
            .collect(Collectors.toMap(PositionLimit::getIsin, Function.identity()));

    return positions.stream()
        .filter(p -> limitsByIsin.containsKey(p.getIsin()))
        .map(
            p -> {
              var limit = limitsByIsin.get(p.getIsin());
              var actualPercent = percentOf(p.getCalculatedMarketValue(), totalNav);
              var severity = determineSeverity(actualPercent, limit);
              return new PositionBreach(
                  fund,
                  p.getIsin(),
                  limit.getLabel(),
                  actualPercent,
                  limit.getSoftLimitPercent(),
                  limit.getHardLimitPercent(),
                  severity);
            })
        .toList();
  }

  private List<PositionBreach> checkIndexGroupLimits(
      TulevaFund fund,
      List<InvestmentPositionCalculation> positions,
      BigDecimal totalNav,
      List<PositionLimit> limits) {

    var indexLimits =
        limits.stream().filter(l -> l.getIsin() == null && l.getIndexGroup() != null).toList();

    if (indexLimits.isEmpty()) {
      return List.of();
    }

    Map<String, Set<String>> groupIsins =
        limits.stream()
            .filter(l -> l.getIsin() != null && l.getIndexGroup() != null)
            .collect(
                Collectors.groupingBy(
                    PositionLimit::getIndexGroup,
                    Collectors.mapping(PositionLimit::getIsin, Collectors.toSet())));

    Map<String, BigDecimal> positionsByIsin =
        positions.stream()
            .collect(
                Collectors.toMap(
                    InvestmentPositionCalculation::getIsin,
                    InvestmentPositionCalculation::getCalculatedMarketValue,
                    BigDecimal::add));

    return indexLimits.stream()
        .map(
            indexLimit -> {
              var isins = groupIsins.getOrDefault(indexLimit.getIndexGroup(), Set.of());
              var aggregateValue =
                  isins.stream()
                      .map(isin -> positionsByIsin.getOrDefault(isin, BigDecimal.ZERO))
                      .reduce(BigDecimal.ZERO, BigDecimal::add);
              var actualPercent = percentOf(aggregateValue, totalNav);
              var severity = determineSeverity(actualPercent, indexLimit);
              return new PositionBreach(
                  fund,
                  indexLimit.getIndexGroup(),
                  indexLimit.getLabel(),
                  actualPercent,
                  indexLimit.getSoftLimitPercent(),
                  indexLimit.getHardLimitPercent(),
                  severity);
            })
        .toList();
  }

  private BigDecimal percentOf(BigDecimal value, BigDecimal total) {
    return value.multiply(BigDecimal.valueOf(100)).divide(total, 4, RoundingMode.HALF_UP);
  }

  private BreachSeverity determineSeverity(BigDecimal actualPercent, PositionLimit limit) {
    if (actualPercent.compareTo(limit.getHardLimitPercent()) > 0) {
      return HARD;
    }
    if (actualPercent.compareTo(limit.getSoftLimitPercent()) > 0) {
      return SOFT;
    }
    return OK;
  }
}
