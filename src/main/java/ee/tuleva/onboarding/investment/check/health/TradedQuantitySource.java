package ee.tuleva.onboarding.investment.check.health;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.ExecutedQuantitySummary;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TradedQuantitySource {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final int SETTLEMENT_LOOKBACK_DAYS = 14;

  private final TransactionExecutionRepository executionRepository;

  Map<String, TradedQuantity> resolve(
      TulevaFund fund, LocalDate previousNavDate, LocalDate navDate) {
    Instant fromInclusive =
        previousNavDate.minusDays(SETTLEMENT_LOOKBACK_DAYS).atStartOfDay(ESTONIAN_ZONE).toInstant();
    Instant toExclusive = navDate.plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant();

    return executionRepository
        .sumExecutedQuantitiesByIsin(fund.getCode(), fromInclusive, toExclusive)
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
