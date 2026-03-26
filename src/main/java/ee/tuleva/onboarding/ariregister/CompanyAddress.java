package ee.tuleva.onboarding.ariregister;

import jakarta.annotation.Nullable;

public record CompanyAddress(
    @Nullable String fullAddress, @Nullable AddressDetails addressDetails) {}
