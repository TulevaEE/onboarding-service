package ee.tuleva.onboarding.contribution;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import java.time.Instant;

public record Contribution(
    Instant time, String sender, BigDecimal amount, Currency currency, Integer pillar) {}
