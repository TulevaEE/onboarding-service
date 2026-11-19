package ee.tuleva.onboarding.taxwin;

record TaxDataConsentResponse(Long id, TaxDataConsentStatus status) {

  static TaxDataConsentResponse from(TaxDataConsent consent) {
    return new TaxDataConsentResponse(consent.getIdOrThrow(), consent.getStatus());
  }
}
