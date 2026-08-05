package ee.tuleva.onboarding.investment.check.health;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.ExecutedQuantitySummary;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TradedQuantitySource {

  private static final int LATE_SETTLEMENT_DAYS = 5;

  private final TransactionExecutionRepository executionRepository;

  Map<String, TradedQuantity> resolve(
      TulevaFund fund, LocalDate previousNavDate, LocalDate navDate) {
    LocalDate fromExclusive = previousNavDate.minusDays(LATE_SETTLEMENT_DAYS);

    return executionRepository
        .sumExecutedQuantitiesByIsin(fund.getCode(), fromExclusive, navDate)
        .stream()
        .collect(
            Collectors.toMap(
                ExecutedQuantitySummary::getIsin,
                summary ->
                    new TradedQuantity(orZero(summary.getBought()), orZero(summary.getSold()))));
  }

  private BigDecimal orZero(BigDecimal quantity) {
    return quantity == null ? ZERO : quantity;
  }
}
