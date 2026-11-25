package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import org.jspecify.annotations.NullMarked;

@NullMarked
record RevaluationPrice(BigDecimal value, PriceValuation.Kind kind) {}
