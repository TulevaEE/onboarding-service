package ee.tuleva.onboarding.admin;

import java.math.BigDecimal;
import java.util.UUID;

record AdjustmentRequest(
    String debitAccount,
    String debitPersonalCode,
    String creditAccount,
    String creditPersonalCode,
    BigDecimal amount,
    UUID externalReference,
    String description) {}
