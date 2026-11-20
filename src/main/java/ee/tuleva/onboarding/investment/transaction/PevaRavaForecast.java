package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.investment.epis.PevaRavaCycle;
import java.math.BigDecimal;
import org.jspecify.annotations.NullMarked;

@NullMarked
record PevaRavaForecast(
    BigDecimal inForce, BigDecimal inForceWithoutSwitchingInflow, PevaRavaCycle cycle) {}
