package ee.tuleva.onboarding.kyb.survey;

import ee.tuleva.onboarding.ariregister.CompanyAddress;
import jakarta.annotation.Nullable;

record LegalEntityAddress(
    @Nullable String fullAddress,
    @Nullable String street,
    @Nullable String city,
    @Nullable String postalCode,
    @Nullable String countryCode) {

  static @Nullable LegalEntityAddress fromCompanyAddress(@Nullable CompanyAddress companyAddress) {
    if (companyAddress == null) {
      return null;
    }
    var details = companyAddress.addressDetails();
    return new LegalEntityAddress(
        companyAddress.fullAddress(),
        details != null ? details.street() : null,
        details != null ? details.city() : null,
        details != null ? details.postalCode() : null,
        details != null ? details.countryCode() : null);
  }
}
