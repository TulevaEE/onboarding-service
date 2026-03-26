package ee.tuleva.onboarding.ariregister;

import jakarta.annotation.Nullable;

public record AddressDetails(
    @Nullable String street,
    @Nullable String city,
    @Nullable String postalCode,
    @Nullable String countryCode) {}
