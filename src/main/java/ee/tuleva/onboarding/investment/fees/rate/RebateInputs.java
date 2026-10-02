package ee.tuleva.onboarding.investment.fees.rate;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

record RebateInputs(
    BigDecimal publishedOcf, @Nullable BigDecimal volumeEur, @Nullable BigDecimal eurUsd) {}
