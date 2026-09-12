package ee.tuleva.onboarding.banking.payment;

import org.jspecify.annotations.Nullable;

/** The bank told us it will not execute a payment we submitted. */
public record PaymentRejectedEvent(String endToEndId, @Nullable String reasonCode) {}
