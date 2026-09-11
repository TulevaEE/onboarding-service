package ee.tuleva.onboarding.savings.fund.taxreport;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record SavingsFundTaxReport(
    int year,
    CostBasisMethod method,
    BigDecimal totalGain,
    List<RealisedGain> redemptions,
    @Nullable InvestmentAccountGains investmentAccount) {}
