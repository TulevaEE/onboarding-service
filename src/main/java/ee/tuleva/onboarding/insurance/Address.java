package ee.tuleva.onboarding.insurance;

import ee.tuleva.onboarding.country.ValidIso2CountryCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Address(
    @NotBlank @Size(max = 200) String street,
    @NotBlank @Size(max = 100) String city,
    @NotBlank @Size(max = 20) String postalCode,
    @NotBlank @ValidIso2CountryCode String countryCode) {}
