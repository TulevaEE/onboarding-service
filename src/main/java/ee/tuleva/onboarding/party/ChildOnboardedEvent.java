package ee.tuleva.onboarding.party;

record ChildOnboardedEvent(
    String parentPersonalCode,
    String childPersonalCode,
    String childFirstName,
    String childLastName) {}
