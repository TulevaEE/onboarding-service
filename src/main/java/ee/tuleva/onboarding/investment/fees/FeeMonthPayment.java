package ee.tuleva.onboarding.investment.fees;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FeeMonthPayment(LocalDate bookedOn, BigDecimal owedAfter) {}
