package ee.tuleva.onboarding.savings.fund.nav;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record NavPosition(String accountName, @Nullable BigDecimal marketValue) {}
