package ee.tuleva.onboarding.investment.fees;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FeeBaseValue(LocalDate accrualDate, FeeType feeType, BigDecimal baseValue) {}
