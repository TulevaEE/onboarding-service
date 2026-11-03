package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public record FundTransactionInput(
    TulevaFund fund,
    List<PositionSnapshot> positions,
    List<PositionSnapshot> excludedPositions,
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
    @Nullable BigDecimal appliedCash,
    @Nullable BigDecimal ledgerCash,
    @Nullable LocalDate positionDate,
    @Nullable LocalDate modelEffectiveDate,
    List<CalculationWarning> inputWarnings) {

  public static FundTransactionInputBuilder builder() {
    return new FundTransactionInputBuilder();
  }

  public FundTransactionInput excluding(Set<String> isins) {
    if (isins.isEmpty()) {
      return this;
    }
    requireInFund(isins);
    List<ModelWeight> remainingWeights =
        modelWeights.stream().filter(weight -> !isins.contains(weight.isin())).toList();
    return new FundTransactionInput(
        fund,
        positions.stream().filter(position -> !isExcluded(position, isins)).toList(),
        Stream.concat(
                excludedPositions.stream(),
                positions.stream().filter(position -> isExcluded(position, isins)))
            .toList(),
        spreadExcludedWeight(remainingWeights, isins),
        grossPortfolioValue,
        cashBuffer,
        liabilities,
        receivables,
        freeCash,
        minTransactionThreshold,
        positionLimits,
        fastSellIsins,
        instrumentTypes,
        orderVenues,
        liabilityBreakdown,
        reportCash,
        appliedCash,
        ledgerCash,
        positionDate,
        modelEffectiveDate,
        inputWarnings);
  }

  public BigDecimal excludedMarketValue() {
    return excludedPositions.stream()
        .map(PositionSnapshot::marketValue)
        .reduce(ZERO, BigDecimal::add);
  }

  private void requireInFund(Set<String> isins) {
    Set<String> fundIsins =
        Stream.concat(
                positions.stream().map(PositionSnapshot::isin),
                modelWeights.stream().map(ModelWeight::isin))
            .filter(Objects::nonNull)
            .collect(toSet());
    List<String> unknown =
        isins.stream().filter(isin -> !fundIsins.contains(isin)).sorted().toList();
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          "Excluded instrument is neither held nor in the model portfolio: fund=%s, isins=%s"
              .formatted(fund.name(), unknown));
    }
  }

  private List<ModelWeight> spreadExcludedWeight(
      List<ModelWeight> remainingWeights, Set<String> isins) {
    BigDecimal totalWeight = sumOfWeights(modelWeights);
    BigDecimal remainingWeight = sumOfWeights(remainingWeights);
    if (remainingWeight.signum() <= 0) {
      throw new IllegalArgumentException(
          "No instrument with a model weight is left to trade: fund=%s, excludeIsins=%s"
              .formatted(fund.name(), isins.stream().sorted().toList()));
    }
    return remainingWeights.stream()
        .map(
            weight ->
                new ModelWeight(
                    weight.isin(),
                    weight.weight().multiply(totalWeight).divide(remainingWeight, 10, HALF_UP)))
        .toList();
  }

  private static boolean isExcluded(PositionSnapshot position, Set<String> isins) {
    return position.isin() != null && isins.contains(position.isin());
  }

  private static BigDecimal sumOfWeights(List<ModelWeight> weights) {
    return weights.stream().map(ModelWeight::weight).reduce(ZERO, BigDecimal::add);
  }

  public static class FundTransactionInputBuilder {
    private @Nullable TulevaFund fund;
    private @Nullable List<PositionSnapshot> positions;
    private List<PositionSnapshot> excludedPositions = List.of();
    private @Nullable List<ModelWeight> modelWeights;
    private @Nullable BigDecimal grossPortfolioValue;
    private @Nullable BigDecimal cashBuffer;
    private @Nullable BigDecimal liabilities;
    private BigDecimal receivables = ZERO;
    private @Nullable BigDecimal freeCash;
    private @Nullable BigDecimal minTransactionThreshold;
    private Map<String, PositionLimitSnapshot> positionLimits = Map.of();
    private Set<String> fastSellIsins = Set.of();
    private Map<String, InstrumentType> instrumentTypes = Map.of();
    private Map<String, OrderVenue> orderVenues = Map.of();
    private @Nullable LiabilityBreakdown liabilityBreakdown;
    private @Nullable BigDecimal reportCash;
    private @Nullable BigDecimal appliedCash;
    private @Nullable BigDecimal ledgerCash;
    private @Nullable LocalDate positionDate;
    private @Nullable LocalDate modelEffectiveDate;
    private List<CalculationWarning> inputWarnings = List.of();

    public FundTransactionInputBuilder fund(TulevaFund fund) {
      this.fund = fund;
      return this;
    }

    public FundTransactionInputBuilder positions(List<PositionSnapshot> positions) {
      this.positions = positions;
      return this;
    }

    public FundTransactionInputBuilder excludedPositions(List<PositionSnapshot> excludedPositions) {
      this.excludedPositions = excludedPositions;
      return this;
    }

    public FundTransactionInputBuilder modelWeights(List<ModelWeight> modelWeights) {
      this.modelWeights = modelWeights;
      return this;
    }

    public FundTransactionInputBuilder grossPortfolioValue(BigDecimal grossPortfolioValue) {
      this.grossPortfolioValue = grossPortfolioValue;
      return this;
    }

    public FundTransactionInputBuilder cashBuffer(BigDecimal cashBuffer) {
      this.cashBuffer = cashBuffer;
      return this;
    }

    public FundTransactionInputBuilder liabilities(BigDecimal liabilities) {
      this.liabilities = liabilities;
      return this;
    }

    public FundTransactionInputBuilder receivables(BigDecimal receivables) {
      this.receivables = receivables;
      return this;
    }

    public FundTransactionInputBuilder freeCash(BigDecimal freeCash) {
      this.freeCash = freeCash;
      return this;
    }

    public FundTransactionInputBuilder minTransactionThreshold(BigDecimal minTransactionThreshold) {
      this.minTransactionThreshold = minTransactionThreshold;
      return this;
    }

    public FundTransactionInputBuilder positionLimits(
        Map<String, PositionLimitSnapshot> positionLimits) {
      this.positionLimits = positionLimits;
      return this;
    }

    public FundTransactionInputBuilder fastSellIsins(Set<String> fastSellIsins) {
      this.fastSellIsins = fastSellIsins;
      return this;
    }

    public FundTransactionInputBuilder instrumentTypes(
        Map<String, InstrumentType> instrumentTypes) {
      this.instrumentTypes = instrumentTypes;
      return this;
    }

    public FundTransactionInputBuilder orderVenues(Map<String, OrderVenue> orderVenues) {
      this.orderVenues = orderVenues;
      return this;
    }

    public FundTransactionInputBuilder liabilityBreakdown(
        @Nullable LiabilityBreakdown liabilityBreakdown) {
      this.liabilityBreakdown = liabilityBreakdown;
      return this;
    }

    public FundTransactionInputBuilder reportCash(@Nullable BigDecimal reportCash) {
      this.reportCash = reportCash;
      return this;
    }

    public FundTransactionInputBuilder appliedCash(@Nullable BigDecimal appliedCash) {
      this.appliedCash = appliedCash;
      return this;
    }

    public FundTransactionInputBuilder ledgerCash(@Nullable BigDecimal ledgerCash) {
      this.ledgerCash = ledgerCash;
      return this;
    }

    public FundTransactionInputBuilder positionDate(@Nullable LocalDate positionDate) {
      this.positionDate = positionDate;
      return this;
    }

    public FundTransactionInputBuilder modelEffectiveDate(@Nullable LocalDate modelEffectiveDate) {
      this.modelEffectiveDate = modelEffectiveDate;
      return this;
    }

    public FundTransactionInputBuilder inputWarnings(List<CalculationWarning> inputWarnings) {
      this.inputWarnings = inputWarnings;
      return this;
    }

    public FundTransactionInput build() {
      return new FundTransactionInput(
          requireField(fund, "fund"),
          requireField(positions, "positions"),
          excludedPositions,
          requireField(modelWeights, "modelWeights"),
          requireField(grossPortfolioValue, "grossPortfolioValue"),
          requireField(cashBuffer, "cashBuffer"),
          requireField(liabilities, "liabilities"),
          receivables,
          requireField(freeCash, "freeCash"),
          requireField(minTransactionThreshold, "minTransactionThreshold"),
          positionLimits,
          fastSellIsins,
          instrumentTypes,
          orderVenues,
          liabilityBreakdown,
          reportCash,
          appliedCash,
          ledgerCash,
          positionDate,
          modelEffectiveDate,
          inputWarnings);
    }

    private static <T> T requireField(@Nullable T value, String fieldName) {
      if (value == null) {
        throw new IllegalStateException("Missing required field: field=" + fieldName);
      }
      return value;
    }
  }
}
