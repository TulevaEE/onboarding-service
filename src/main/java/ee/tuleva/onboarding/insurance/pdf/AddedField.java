package ee.tuleva.onboarding.insurance.pdf;

public record AddedField(
    String name, int pageIndex, float left, float bottom, float right, float top) {}
