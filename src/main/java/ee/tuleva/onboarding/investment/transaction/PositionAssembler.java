package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
class PositionAssembler {

  private final FundPositionRepository fundPositionRepository;

  record Assembled(
      List<PositionSnapshot> positions,
      Map<String, SellableUnits> sellableUnits,
      PositionAdjustments adjustments) {}

  Assembled assemble(
      TulevaFund fund,
      LocalDate positionDate,
      PendingOrderImpact pendingOrders,
      TradePrepPricing pricing) {
    List<PositionSnapshot> report = reportPositions(fund, positionDate);
    Map<String, SellableUnits> sellableUnits =
        sellableUnits(report, pendingOrders.unreportedSellQuantities());
    UnreportedSaleDeduction.Deducted deducted =
        UnreportedSaleDeduction.deduct(report, sellableUnits, pendingOrders.unreportedSellValues());
    PriceRevaluation.Revalued revalued =
        PriceRevaluation.revalue(
            applyUnreportedPurchases(deducted.positions(), pendingOrders),
            pricing.revaluationPrices(),
            pendingOrders.unreportedBuyValuesWithoutUnits());
    return new Assembled(
        revalued.positions(),
        sellableUnits,
        new PositionAdjustments(deducted.deductedSales(), revalued.valuations()));
  }

  private static Map<String, SellableUnits> sellableUnits(
      List<PositionSnapshot> report, Map<String, BigDecimal> unitsInSales) {
    return report.stream()
        .filter(position -> position.isin() != null)
        .collect(
            toMap(
                position -> Objects.requireNonNull(position.isin()),
                position ->
                    new SellableUnits(
                        position.quantity(), unitsInSales.getOrDefault(position.isin(), ZERO)),
                SellableUnits::togetherWith));
  }

  List<PositionSnapshot> reportPositions(TulevaFund fund, LocalDate date) {
    List<PositionSnapshot> positions = new ArrayList<>();
    for (FundPosition position :
        fundPositionRepository.findByNavDateAndFundAndAccountType(date, fund, SECURITY)) {
      if (position.getMarketValue() == null) {
        continue;
      }
      if (!isTradeableHolding(fund, date, position)) {
        continue;
      }
      positions.add(
          new PositionSnapshot(
              position.getAccountId(),
              Objects.requireNonNull(position.getMarketValue()),
              position.getQuantity(),
              position.getMarketPrice()));
    }
    return List.copyOf(positions);
  }

  private boolean isTradeableHolding(TulevaFund fund, LocalDate date, FundPosition position) {
    BigDecimal marketValue =
        Objects.requireNonNull(
            position.getMarketValue(),
            "Security position has no market value: fund=" + fund + ", positionDate=" + date);
    if (marketValue.signum() >= 0) {
      return true;
    }
    log.warn(
        "Skipping security row with a negative market value: fund={}, positionDate={}, isin={},"
            + " marketValue={}",
        fund,
        date,
        position.getAccountId(),
        marketValue.toPlainString());
    return false;
  }

  private List<PositionSnapshot> applyUnreportedPurchases(
      List<PositionSnapshot> positions, PendingOrderImpact pendingOrders) {
    Map<String, BigDecimal> values = pendingOrders.unreportedBuyValues();
    if (values.isEmpty()) {
      return positions;
    }
    Map<String, BigDecimal> quantities = pendingOrders.unreportedBuyQuantities();

    List<PositionSnapshot> adjusted = new ArrayList<>();
    Set<String> reported = new HashSet<>();
    for (PositionSnapshot position : positions) {
      adjusted.add(
          adjustPosition(
              position, deltaFor(values, position.isin()), deltaFor(quantities, position.isin())));
      reported.add(position.isin());
    }

    for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
      if (reported.contains(entry.getKey()) || entry.getValue().signum() <= 0) {
        continue;
      }
      adjusted.add(
          new PositionSnapshot(
              entry.getKey(), entry.getValue(), clampToZero(quantities.get(entry.getKey())), null));
    }
    return List.copyOf(adjusted);
  }

  private PositionSnapshot adjustPosition(
      PositionSnapshot position, BigDecimal valueDelta, BigDecimal quantityDelta) {
    if (valueDelta.signum() == 0 && quantityDelta.signum() == 0) {
      return position;
    }
    BigDecimal quantity =
        position.quantity() == null ? null : position.quantity().add(quantityDelta).max(ZERO);
    return new PositionSnapshot(
        position.isin(),
        position.marketValue().add(valueDelta).max(ZERO),
        quantity,
        position.unitPrice());
  }

  private static BigDecimal deltaFor(Map<String, BigDecimal> deltas, @Nullable String isin) {
    return isin == null ? ZERO : deltas.getOrDefault(isin, ZERO);
  }

  @Nullable
  private static BigDecimal clampToZero(@Nullable BigDecimal quantity) {
    return quantity == null ? null : quantity.max(ZERO);
  }
}
