package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.transaction.DeductedSale.DeductedBy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class UnreportedSaleDeduction {

  private static final int CENTS = 2;

  record Deducted(List<PositionSnapshot> positions, List<DeductedSale> deductedSales) {}

  private final Map<String, SellableUnits> sellableUnits;
  private final Map<String, BigDecimal> saleValuesTheReportDoesNotShow;
  private final Map<String, BigDecimal> leftToDeduct = new HashMap<>();
  private final Map<String, BigDecimal> valueDeducted = new HashMap<>();
  private final Map<String, BigDecimal> unitsDeducted = new HashMap<>();

  private UnreportedSaleDeduction(
      Map<String, SellableUnits> sellableUnits,
      Map<String, BigDecimal> saleValuesTheReportDoesNotShow) {
    this.sellableUnits = sellableUnits;
    this.saleValuesTheReportDoesNotShow = saleValuesTheReportDoesNotShow;
  }

  static Deducted deduct(
      List<PositionSnapshot> report,
      Map<String, SellableUnits> sellableUnits,
      Map<String, BigDecimal> saleValuesTheReportDoesNotShow) {
    UnreportedSaleDeduction deduction =
        new UnreportedSaleDeduction(sellableUnits, saleValuesTheReportDoesNotShow);
    List<PositionSnapshot> positions = report.stream().map(deduction::deductFrom).toList();
    return new Deducted(positions, deduction.deductedSales(report));
  }

  private PositionSnapshot deductFrom(PositionSnapshot row) {
    String isin = row.isin();
    SellableUnits units = isin == null ? null : sellableUnits.get(isin);
    if (isin == null || units == null) {
      return row;
    }
    return units.held() == null ? byValue(isin, row) : byUnits(isin, row, units.pendingSells());
  }

  private PositionSnapshot byUnits(String isin, PositionSnapshot row, BigDecimal unitsInSales) {
    BigDecimal quantity = row.quantity();
    BigDecimal left = leftToDeduct.getOrDefault(isin, unitsInSales);
    if (quantity == null || quantity.signum() <= 0 || left.signum() <= 0) {
      return row;
    }
    BigDecimal remaining = quantity.subtract(left.min(quantity));
    BigDecimal value =
        row.marketValue()
            .multiply(remaining)
            .divide(quantity, Math.max(row.marketValue().scale(), CENTS), HALF_UP);
    leftToDeduct.put(isin, left.subtract(quantity.subtract(remaining)));
    valueDeducted.merge(isin, row.marketValue().subtract(value), BigDecimal::add);
    unitsDeducted.merge(isin, quantity.subtract(remaining), BigDecimal::add);
    return new PositionSnapshot(isin, value, remaining, row.unitPrice());
  }

  private PositionSnapshot byValue(String isin, PositionSnapshot row) {
    BigDecimal left =
        leftToDeduct.getOrDefault(isin, saleValuesTheReportDoesNotShow.getOrDefault(isin, ZERO));
    BigDecimal deducted = left.min(row.marketValue());
    if (deducted.signum() <= 0) {
      return row;
    }
    leftToDeduct.put(isin, left.subtract(deducted));
    valueDeducted.merge(isin, deducted, BigDecimal::add);
    return new PositionSnapshot(
        isin, row.marketValue().subtract(deducted), row.quantity(), row.unitPrice());
  }

  private List<DeductedSale> deductedSales(List<PositionSnapshot> report) {
    return report.stream()
        .map(PositionSnapshot::isin)
        .filter(Objects::nonNull)
        .distinct()
        .filter(
            isin ->
                valueDeducted.getOrDefault(isin, ZERO).signum() > 0
                    || unitsDeducted.getOrDefault(isin, ZERO).signum() > 0)
        .map(isin -> deductedSale(isin, report))
        .toList();
  }

  private DeductedSale deductedSale(String isin, List<PositionSnapshot> report) {
    SellableUnits units = Objects.requireNonNull(sellableUnits.get(isin));
    return new DeductedSale(
        isin,
        units.held(),
        report.stream()
            .filter(row -> isin.equals(row.isin()))
            .map(PositionSnapshot::marketValue)
            .reduce(ZERO, BigDecimal::add),
        units.pendingSells(),
        valueDeducted.getOrDefault(isin, ZERO),
        units.held() == null ? DeductedBy.VALUE : DeductedBy.UNITS);
  }
}
