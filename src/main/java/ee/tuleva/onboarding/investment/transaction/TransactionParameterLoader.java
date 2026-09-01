package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.investment.portfolio.FundLimit;
import ee.tuleva.onboarding.investment.portfolio.FundLimitRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.portfolio.PositionLimit;
import ee.tuleva.onboarding.investment.portfolio.PositionLimitRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class TransactionParameterLoader {

  private static final BigDecimal MODEL_WEIGHT_SUM_TOLERANCE = new BigDecimal("0.0001");

  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final FundLimitRepository fundLimitRepository;
  private final PositionLimitRepository positionLimitRepository;

  TransactionParameters load(TulevaFund fund, LocalDate asOfDate) {
    List<ModelPortfolioAllocation> allocations = getModelAllocations(fund, asOfDate);
    List<ModelPortfolioAllocation> previousAllocations = getPreviousAllocations(fund, asOfDate);
    List<ModelWeight> modelWeights = toModelWeights(allocations);
    assertModelWeightsSumToOne(fund, modelWeights);
    LocalDate modelEffectiveDate = getModelEffectiveDate(allocations);
    BigDecimal cashBuffer = getCashBuffer(fund, asOfDate);
    BigDecimal minTransaction = getMinTransaction(fund, asOfDate);
    Map<String, PositionLimitSnapshot> positionLimits = getPositionLimits(fund, asOfDate);
    Set<String> fastSellIsins = getFastSellIsins(allocations, previousAllocations);
    Map<String, InstrumentType> instrumentTypes =
        getInstrumentTypes(allocations, previousAllocations);
    Map<String, OrderVenue> orderVenues = getOrderVenues(allocations, previousAllocations);
    return new TransactionParameters(
        modelWeights,
        modelEffectiveDate,
        cashBuffer,
        minTransaction,
        positionLimits,
        fastSellIsins,
        instrumentTypes,
        orderVenues);
  }

  private List<ModelPortfolioAllocation> getModelAllocations(TulevaFund fund, LocalDate asOfDate) {
    return modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, asOfDate).stream()
        .filter(allocation -> allocation.getIsin() != null)
        .toList();
  }

  private List<ModelPortfolioAllocation> getPreviousAllocations(
      TulevaFund fund, LocalDate asOfDate) {
    return modelPortfolioAllocationRepository.findPreviousByFundAsOf(fund, asOfDate).stream()
        .filter(a -> a.getIsin() != null)
        .toList();
  }

  private List<ModelWeight> toModelWeights(List<ModelPortfolioAllocation> allocations) {
    return allocations.stream()
        .map(
            allocation ->
                new ModelWeight(
                    Objects.requireNonNull(
                        allocation.getIsin(), "Missing isin: allocationId=" + allocation.getId()),
                    allocation.getWeight()))
        .toList();
  }

  private void assertModelWeightsSumToOne(TulevaFund fund, List<ModelWeight> modelWeights) {
    if (modelWeights.isEmpty()) {
      return;
    }
    BigDecimal totalWeight =
        modelWeights.stream().map(ModelWeight::weight).reduce(ZERO, BigDecimal::add);
    if (totalWeight.subtract(ONE).abs().compareTo(MODEL_WEIGHT_SUM_TOLERANCE) > 0) {
      throw new IllegalStateException(
          "Model weights do not sum to 1: fund=" + fund + ", sum=" + totalWeight.toPlainString());
    }
  }

  @Nullable
  private static LocalDate getModelEffectiveDate(List<ModelPortfolioAllocation> allocations) {
    return allocations.stream()
        .map(ModelPortfolioAllocation::getEffectiveDate)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private BigDecimal getCashBuffer(TulevaFund fund, LocalDate asOfDate) {
    return getFundLimitValue(fund, asOfDate, FundLimit::getReserveSoft, "reserveSoft");
  }

  private BigDecimal getMinTransaction(TulevaFund fund, LocalDate asOfDate) {
    return getFundLimitValue(fund, asOfDate, FundLimit::getMinTransaction, "minTransaction");
  }

  private BigDecimal getFundLimitValue(
      TulevaFund fund,
      LocalDate asOfDate,
      Function<FundLimit, @Nullable BigDecimal> extractor,
      String fieldName) {
    FundLimit limit =
        fundLimitRepository
            .findLatestByFundAsOf(fund, asOfDate)
            .orElseThrow(() -> new IllegalStateException("No fund limit found: fund=" + fund));
    BigDecimal value = extractor.apply(limit);
    if (value == null) {
      throw new IllegalStateException(
          "Fund limit field is missing: fund=" + fund + ", field=" + fieldName);
    }
    return value;
  }

  private Map<String, PositionLimitSnapshot> getPositionLimits(
      TulevaFund fund, LocalDate asOfDate) {
    return positionLimitRepository.findLatestByFundAsOf(fund, asOfDate).stream()
        .collect(
            Collectors.toMap(
                PositionLimit::getIsin,
                limit ->
                    new PositionLimitSnapshot(
                        limit.getSoftLimitPercent(), limit.getHardLimitPercent()),
                (a, b) -> b));
  }

  private Set<String> getFastSellIsins(
      List<ModelPortfolioAllocation> current, List<ModelPortfolioAllocation> previous) {
    var merged =
        new HashSet<>(
            previous.stream()
                .filter(ModelPortfolioAllocation::isFastSell)
                .map(ModelPortfolioAllocation::getIsin)
                .collect(toSet()));
    current.stream()
        .filter(ModelPortfolioAllocation::isFastSell)
        .map(ModelPortfolioAllocation::getIsin)
        .forEach(merged::add);
    return merged;
  }

  private Map<String, InstrumentType> getInstrumentTypes(
      List<ModelPortfolioAllocation> current, List<ModelPortfolioAllocation> previous) {
    var merged =
        new HashMap<>(
            previous.stream()
                .filter(a -> a.getInstrumentType() != null)
                .collect(
                    Collectors.toMap(
                        ModelPortfolioAllocation::getIsin,
                        ModelPortfolioAllocation::getInstrumentType,
                        (a, b) -> b)));
    current.stream()
        .filter(a -> a.getInstrumentType() != null)
        .forEach(a -> merged.put(a.getIsin(), a.getInstrumentType()));
    return merged;
  }

  private Map<String, OrderVenue> getOrderVenues(
      List<ModelPortfolioAllocation> current, List<ModelPortfolioAllocation> previous) {
    var merged =
        new HashMap<>(
            previous.stream()
                .filter(a -> a.getOrderVenue() != null)
                .collect(
                    Collectors.toMap(
                        ModelPortfolioAllocation::getIsin,
                        ModelPortfolioAllocation::getOrderVenue,
                        (a, b) -> b)));
    current.stream()
        .filter(a -> a.getOrderVenue() != null)
        .forEach(a -> merged.put(a.getIsin(), a.getOrderVenue()));
    return merged;
  }
}
