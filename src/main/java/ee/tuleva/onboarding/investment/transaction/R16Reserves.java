package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record R16Reserves(BigDecimal soft, @Nullable BigDecimal hard) {}
