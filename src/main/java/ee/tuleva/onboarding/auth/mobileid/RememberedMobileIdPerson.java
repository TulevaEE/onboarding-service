package ee.tuleva.onboarding.auth.mobileid;

record RememberedMobileIdPerson(
    String personalCode, String firstName, RememberedMobileIdPhone phone) {}
