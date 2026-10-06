package ee.tuleva.onboarding.auth.command;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import ee.tuleva.onboarding.personalcode.ValidPersonalCode;
import org.jspecify.annotations.Nullable;

public record MobileIdAuthenticateCommand(
    @Nullable String phoneNumber,
    @ValidPersonalCode String personalCode,
    @JsonSetter(nulls = Nulls.AS_EMPTY) boolean rememberMe)
    implements AuthenticateCommand {}
