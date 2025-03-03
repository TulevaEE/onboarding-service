package ee.tuleva.onboarding.epis.withdrawals;

import lombok.Builder;

@Builder
public record ArrestsBankruptciesDto(
    boolean activeArrestsPresent, boolean activeBankruptciesPresent) {}
