package ee.tuleva.onboarding.savings.fund.gift;

import ee.tuleva.onboarding.personalcode.ValidPersonalCode;

public record OpenGiftLinkRequest(@ValidPersonalCode String childPersonalCode) {}
