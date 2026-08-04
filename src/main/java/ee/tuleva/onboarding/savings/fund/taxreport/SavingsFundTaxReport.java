package ee.tuleva.onboarding.savings.fund.taxreport;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

@Builder
public record SavingsFundTaxReport(
    int year, CostBasisMethod method, BigDecimal totalGain, List<RealisedGain> redemptions) {}
