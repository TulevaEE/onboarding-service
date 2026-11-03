package ee.tuleva.onboarding.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ThirdPillarContribution(LocalDate reportedOn, BigDecimal amount) {}
