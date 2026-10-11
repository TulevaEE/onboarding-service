package ee.tuleva.onboarding.auth.mobileid;

import ee.tuleva.onboarding.personalcode.ValidPersonalCode;

public record RememberedPhoneQuery(@ValidPersonalCode String personalCode) {}
