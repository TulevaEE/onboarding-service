package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.ASSET;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.calculation.PositionPriceResolver;
import ee.tuleva.onboarding.investment.calculation.ResolvedPrice;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecuritiesValueComponent implements NavComponent {

  private final NavLedgerRepository navLedgerRepository;
  private final PositionPriceResolver positionPriceResolver;

  @Override
  public String getName() {
    return "securities";
  }

  @Override
  public NavComponentType getType() {
    return ASSET;
  }

  @Override
  public BigDecimal calculate(NavComponentContext context) {
    Map<String, BigDecimal> unitBalances = navLedgerRepository.getSecuritiesUnitBalances();
    return unitBalances.entrySet().stream()
        .map(entry -> calculateIsinValue(entry.getKey(), entry.getValue(), context))
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal calculateIsinValue(
      String isin, BigDecimal units, NavComponentContext context) {
    return positionPriceResolver
        .resolve(isin, context.getPriceDate())
        .map(ResolvedPrice::usedPrice)
        .map(price -> units.multiply(price).setScale(2, HALF_UP))
        .orElse(ZERO);
  }

  @Override
  public boolean requiresPreliminaryNav() {
    return false;
  }
}
