package ee.tuleva.onboarding.investment.epis;

import ee.tuleva.onboarding.investment.epis.R45TransactionType.Direction;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record R45Row(
    Direction direction, BigDecimal valueEur, @Nullable LocalDate settlementDate) {}
