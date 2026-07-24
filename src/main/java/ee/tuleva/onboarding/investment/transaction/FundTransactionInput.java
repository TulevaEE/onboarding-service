package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record FundTransactionInput(
    TulevaFund fund,
    List<PositionSnapshot> positions,
    List<ModelWeight> modelWeights,
    BigDecimal grossPortfolioValue,
    BigDecimal cashBuffer,
    BigDecimal liabilities,
    BigDecimal receivables,
    BigDecimal freeCash,
    BigDecimal minTransactionThreshold,
    Map<String, PositionLimitSnapshot> positionLimits,
    Set<String> fastSellIsins,
    Map<String, InstrumentType> instrumentTypes,
    Map<String, OrderVenue> orderVenues,
    @Nullable LiabilityBreakdown liabilityBreakdown,
    @Nullable BigDecimal reportCash,
    @Nullable BigDecimal ledgerCash,
    @Nullable LocalDate positionDate,
    @Nullable LocalDate modelEffectiveDate) {

  public static class FundTransactionInputBuilder {
    private BigDecimal receivables = ZERO;
    private Map<String, InstrumentType> instrumentTypes = Map.of();
    private Map<String, OrderVenue> orderVenues = Map.of();
    private Set<String> fastSellIsins = Set.of();
    private Map<String, PositionLimitSnapshot> positionLimits = Map.of();
  }
}
