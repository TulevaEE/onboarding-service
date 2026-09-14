package ee.tuleva.onboarding.banking.check.payment;

record PaymentCheckRecorded(
    Long eventId, PaymentCheckType checkType, PaymentCheckSeverity severity, String detail) {}
