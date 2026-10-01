package ee.tuleva.onboarding.auth.command;

import ee.tuleva.onboarding.personalcode.ValidPersonalCode;
import org.jspecify.annotations.Nullable;

public record MobileIdAuthenticateCommand(
    @Nullable String phoneNumber, @ValidPersonalCode String personalCode)
    implements AuthenticateCommand {}
