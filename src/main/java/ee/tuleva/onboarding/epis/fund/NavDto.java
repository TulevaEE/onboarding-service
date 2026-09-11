package ee.tuleva.onboarding.epis.fund;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NavDto(String isin, LocalDate date, BigDecimal value) {}
