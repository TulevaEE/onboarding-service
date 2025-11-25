package ee.tuleva.onboarding.capital;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_DOWN;

import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;

public record CapitalRow(
    // TODO remove profit when migrated
    MemberCapitalEventType type,
    BigDecimal contributions,
    BigDecimal profit,
    BigDecimal unitCount,
    BigDecimal unitPrice,
    Currency currency) {

  public BigDecimal getValue() {
    return CapitalCalculations.calculateCapitalValue(unitCount, unitPrice);
  }

  public static CapitalRow rounded(CapitalRow row) {
    return new CapitalRow(
        row.type(),
        row.contributions().setScale(2, HALF_DOWN),
        row.profit().setScale(2, HALF_DOWN),
        row.unitCount().setScale(5, HALF_DOWN),
        row.unitPrice(),
        row.currency());
  }

  public static CapitalRow sum(CapitalRow a, CapitalRow b) {
    var type = a.type() != null ? a.type() : b.type();

    var contributions = a.contributions().add(b.contributions());
    var profit = a.profit().add(b.profit());

    var unitCount = a.unitCount().add(b.unitCount());
    var unitPrice = a.unitPrice().compareTo(ZERO) != 0 ? a.unitPrice() : b.unitPrice();

    return new CapitalRow(type, contributions, profit, unitCount, unitPrice, EUR);
  }

  public static CapitalRow from(
      MemberCapitalEvent event, BigDecimal latestUnitPrice, BigDecimal computedProfit) {
    return new CapitalRow(
        event.getType(),
        event.getFiatValue(),
        computedProfit,
        event.getOwnershipUnitAmount(),
        latestUnitPrice,
        EUR);
  }

  public static CapitalRow empty() {
    return new CapitalRow(null, ZERO, ZERO, ZERO, ZERO, EUR);
  }
}
