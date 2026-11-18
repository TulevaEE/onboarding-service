package ee.tuleva.onboarding.party.admin;

import ee.tuleva.onboarding.personalcode.ValidPersonalCode;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record ChildGuardianLookupRequest(
    @ValidPersonalCode String requesterPersonalCode, @ValidPersonalCode String childPersonalCode) {}
