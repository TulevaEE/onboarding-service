package ee.tuleva.onboarding.party;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;

public record CreateChildCommand(@ValidPersonalCode String childPersonalCode) {}
