package ee.tuleva.onboarding.investment.check.health;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.transaction.ExecutedQuantitySummary;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TradedQuantitySource {

  private final TransactionExecutionRepository executionRepository;

  Map<String, TradedQuantity> resolve(
      TulevaFund fund, LocalDate previousNavDate, LocalDate navDate) {
    return executionRepository
        .sumExecutedQuantitiesByIsin(fund.getCode(), previousNavDate, navDate)
        .stream()
        .collect(
            Collectors.toMap(
                ExecutedQuantitySummary::getIsin,
                summary ->
                    new TradedQuantity(orZero(summary.getBought()), orZero(summary.getSold()))));
  }

  private BigDecimal orZero(@Nullable BigDecimal quantity) {
    return quantity == null ? ZERO : quantity;
  }
}
