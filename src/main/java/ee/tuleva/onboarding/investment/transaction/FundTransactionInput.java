package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

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
    @Nullable BigDecimal appliedCash,
    @Nullable BigDecimal ledgerCash,
    @Nullable LocalDate positionDate,
    @Nullable LocalDate modelEffectiveDate) {

  public static FundTransactionInputBuilder builder() {
    return new FundTransactionInputBuilder();
  }

  public static class FundTransactionInputBuilder {
    private @Nullable TulevaFund fund;
    private @Nullable List<PositionSnapshot> positions;
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

    public FundTransactionInputBuilder fund(TulevaFund fund) {
      this.fund = fund;
      return this;
    }

    public FundTransactionInputBuilder positions(List<PositionSnapshot> positions) {
      this.positions = positions;
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

    public FundTransactionInput build() {
      return new FundTransactionInput(
          requireField(fund, "fund"),
          requireField(positions, "positions"),
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
          modelEffectiveDate);
    }

    private static <T> T requireField(@Nullable T value, String fieldName) {
      if (value == null) {
        throw new IllegalStateException("Missing required field: field=" + fieldName);
      }
      return value;
    }
  }
}
