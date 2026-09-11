package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExecutedPrice(BigDecimal price, BigDecimal quantity, LocalDate tradeDate) {}
