package ee.tuleva.onboarding.fund;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NavValueResponse(LocalDate date, BigDecimal value) {}
