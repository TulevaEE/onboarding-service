package ee.tuleva.onboarding.taxwin;

public sealed interface ConsentRequest permits ConsentDocument, ConsentAlreadyApproved {}
