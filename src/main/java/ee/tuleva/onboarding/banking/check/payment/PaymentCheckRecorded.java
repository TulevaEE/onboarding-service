package ee.tuleva.onboarding.banking.check.payment;

/** A finding was persisted. The alert for it goes out only once that write commits. */
record PaymentCheckRecorded(
    Long eventId, PaymentCheckType checkType, PaymentCheckSeverity severity, String detail) {}
