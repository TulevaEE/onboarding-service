package ee.tuleva.onboarding.investment.fees;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyAccrualAmount(LocalDate date, BigDecimal amount) {}
