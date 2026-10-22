package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;

import ee.tuleva.onboarding.investment.portfolio.PositionLimit;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class PositionLimitChecker {

  List<PositionBreach> check(
      TulevaFund fund,
      List<FundPosition> positions,
      BigDecimal totalNav,
      List<PositionLimit> limits) {

    if (totalNav.signum() == 0) {
      return List.of();
    }

    Map<String, PositionLimit> limitsByIsin =
        limits.stream().collect(Collectors.toMap(PositionLimit::getIsin, Function.identity()));

    return positions.stream()
        .filter(p -> limitsByIsin.containsKey(p.getAccountId()))
        .map(
            p -> {
              String isin =
                  Objects.requireNonNull(
                      p.getAccountId(), "Position matched a limit but has no isin: position=" + p);
              var limit =
                  Objects.requireNonNull(
                      limitsByIsin.get(isin), "Position limit missing: isin=" + isin);
              var marketValue =
                  Objects.requireNonNull(
                      p.getMarketValue(), "Position has no market value: isin=" + isin);
              var actualPercent = percentOf(marketValue, totalNav);
              var severity = determineSeverity(actualPercent, limit);
              var label =
                  Objects.requireNonNull(
                      limit.getLabel(), "Position limit missing label: isin=" + limit.getIsin());
              return new PositionBreach(
                  fund,
                  isin,
                  label,
                  actualPercent,
                  limit.getSoftLimitPercent(),
                  limit.getHardLimitPercent(),
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
