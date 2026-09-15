package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.party.PartyId;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record IncomingSavingsPayment(
    @Nullable String remitterName,
    @Nullable String remitterIban,
    String description,
    BigDecimal amount,
    Currency currency,
    PartyId recipient) {}
