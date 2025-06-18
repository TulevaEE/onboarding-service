package ee.tuleva.onboarding.contribution;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

@Builder
public record ThirdPillarContribution(
    Instant time, String sender, BigDecimal amount, Currency currency, Integer pillar)
    implements Contribution {}
