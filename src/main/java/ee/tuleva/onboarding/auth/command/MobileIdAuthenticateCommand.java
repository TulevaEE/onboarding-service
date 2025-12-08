package ee.tuleva.onboarding.auth.command;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;

public record MobileIdAuthenticateCommand(
    @NotBlank @Length(min = 7, max = 30) @Pattern(regexp = "^\\+?\\d{7,30}$") String phoneNumber,
    @ValidPersonalCode String personalCode)
    implements AuthenticateCommand {}
