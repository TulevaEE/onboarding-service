package ee.tuleva.onboarding.taxwin;

public record ConsentDocument(String confirmReference, byte[] pdf) implements ConsentRequest {}
