package ee.tuleva.onboarding.auth.command;

import ee.tuleva.onboarding.personalcode.ValidPersonalCode;

public record SmartIdAuthenticateCommand(@ValidPersonalCode String personalCode)
    implements AuthenticateCommand {}
