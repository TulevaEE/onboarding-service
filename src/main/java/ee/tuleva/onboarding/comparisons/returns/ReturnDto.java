package ee.tuleva.onboarding.comparisons.returns;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ReturnDto(BigDecimal rate, BigDecimal amount, Currency currency, LocalDate from) {}
