package ee.tuleva.onboarding.admin;

import ee.tuleva.onboarding.party.RepresentationType;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateParentChildLinkRequest(
    @ValidPersonalCode String parentCode,
    @ValidPersonalCode String childCode,
    @NotBlank String childFirstName,
    @NotBlank String childLastName,
    @NotNull RepresentationType relationshipType) {}
