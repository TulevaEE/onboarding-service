package ee.tuleva.onboarding.admin;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateGuardianLinkRequest(
    @ValidPersonalCode String guardianCode,
    @ValidPersonalCode String wardCode,
    @NotBlank String wardFirstName,
    @NotBlank String wardLastName,
    @NotNull LocalDate validUntil) {}
