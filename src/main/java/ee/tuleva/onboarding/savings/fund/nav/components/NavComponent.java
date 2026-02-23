package ee.tuleva.onboarding.savings.fund.nav.components;

import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;

public interface NavComponent {

  String getName();

  NavComponentType getType();

  BigDecimal calculate(NavComponentContext context);

  enum NavComponentType {
    ASSET,
    LIABILITY
  }
}
