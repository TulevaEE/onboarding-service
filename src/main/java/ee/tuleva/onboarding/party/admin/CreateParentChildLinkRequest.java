package ee.tuleva.onboarding.party.admin;

import ee.tuleva.onboarding.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

public record CreateParentChildLinkRequest(
    @ValidPersonalCode String parentCode,
    @ValidPersonalCode String childCode,
    @NotBlank String childFirstName,
    @NotBlank String childLastName,
    @Nullable String documentReference) {}
