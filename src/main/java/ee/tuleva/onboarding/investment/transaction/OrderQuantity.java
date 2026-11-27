package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

record OrderQuantity(
    @Nullable BigDecimal quantity,
    @Nullable String stalePriceComment,
    @Nullable TradePrepPrice price) {}
