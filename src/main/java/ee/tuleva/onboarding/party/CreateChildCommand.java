package ee.tuleva.onboarding.party;

import ee.tuleva.onboarding.personalcode.ValidPersonalCode;

public record CreateChildCommand(@ValidPersonalCode String childPersonalCode) {}
