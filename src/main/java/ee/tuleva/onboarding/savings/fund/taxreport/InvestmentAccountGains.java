package ee.tuleva.onboarding.savings.fund.taxreport;

import java.math.BigDecimal;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record InvestmentAccountGains(@Nullable BigDecimal totalGain) {}
