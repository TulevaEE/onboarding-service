package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.party.PartyId;
import java.math.BigDecimal;

public record IncomingSavingsPayment(
    String remitterName,
    String remitterIban,
    String description,
    BigDecimal amount,
    Currency currency,
    PartyId recipient) {}
