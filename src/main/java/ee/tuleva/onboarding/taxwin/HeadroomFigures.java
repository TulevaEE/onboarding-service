package ee.tuleva.onboarding.taxwin;

record HeadroomFigures(
    int limit,
    LimitCertainty limitCertainty,
    int remaining,
    int refund,
    int monthlyPayment,
    PaymentSuggestion paymentSuggestion) {}
