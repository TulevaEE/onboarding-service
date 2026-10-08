package ee.tuleva.onboarding.investment.check.limit;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StaleFundSize(
    String isin,
    String name,
    BigDecimal reportedFundSize,
    String reportedCurrency,
    LocalDate unchangedSince) {}
