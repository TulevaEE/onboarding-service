package ee.tuleva.onboarding.contribution;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

@Builder
public record SecondPillarContribution(
    Instant time,
    String sender,
    BigDecimal amount,
    Currency currency,
    Integer pillar,
    BigDecimal additionalParentalBenefit,
    BigDecimal employeeWithheldPortion,
    BigDecimal socialTaxPortion,
    BigDecimal interest)
    implements Contribution {}
