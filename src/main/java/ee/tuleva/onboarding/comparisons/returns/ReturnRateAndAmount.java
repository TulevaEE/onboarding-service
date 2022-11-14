package ee.tuleva.onboarding.comparisons.returns;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;

public record ReturnRateAndAmount(BigDecimal rate, BigDecimal amount, Currency currency) {}
