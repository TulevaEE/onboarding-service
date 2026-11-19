package ee.tuleva.onboarding.taxwin;

import java.math.BigDecimal;
import java.time.YearMonth;

public record ExpectedMonthlyIncome(
    YearMonth month, BigDecimal amount, ExpectedIncomeSource source) {}
