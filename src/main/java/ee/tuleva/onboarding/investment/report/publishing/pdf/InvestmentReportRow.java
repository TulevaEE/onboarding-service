package ee.tuleva.onboarding.investment.report.publishing.pdf;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record InvestmentReportRow(
    String displayName,
    @Nullable String institution,
    @Nullable String isin,
    @Nullable String country,
    String currency,
    @Nullable BigDecimal avgCostPerUnit,
    @Nullable BigDecimal avgCostTotal,
    @Nullable BigDecimal marketPricePerUnit,
    BigDecimal marketValueTotal,
    BigDecimal navSharePercent,
    @Nullable BigDecimal changeVsPreviousMonth) {}
