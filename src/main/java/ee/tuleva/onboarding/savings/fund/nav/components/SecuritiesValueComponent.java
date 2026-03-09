package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.ASSET;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.calculation.PositionPriceResolver;
import ee.tuleva.onboarding.investment.calculation.ResolvedPrice;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import java.util.HashMap;
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
    Map<String, BigDecimal> unitBalances =
        navLedgerRepository.getSecuritiesUnitBalancesAt(context.getCutoff(), context.getFund());
    Map<String, ResolvedPrice> securityPrices = new HashMap<>();
    BigDecimal total =
        unitBalances.entrySet().stream()
            .map(
                entry ->
                    calculateIsinValue(entry.getKey(), entry.getValue(), context, securityPrices))
            .reduce(ZERO, BigDecimal::add);
    context.setSecurityPrices(securityPrices);
    return total;
  }

  private BigDecimal calculateIsinValue(
      String isin,
      BigDecimal units,
      NavComponentContext context,
      Map<String, ResolvedPrice> securityPrices) {
    return positionPriceResolver
        .resolve(isin, context.getPriceDate(), context.getPriceCutoff())
        .map(
            resolved -> {
              securityPrices.put(isin, resolved);
              return units.multiply(resolved.usedPrice()).setScale(2, HALF_UP);
            })
        .orElse(ZERO);
  }
}
