package ee.tuleva.onboarding.auth.command;

import ee.tuleva.onboarding.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotBlank;

public record MobileIdAuthenticateCommand(
    @NotBlank String phoneNumber, @ValidPersonalCode String personalCode)
    implements AuthenticateCommand {}
