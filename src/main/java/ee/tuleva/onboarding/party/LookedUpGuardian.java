package ee.tuleva.onboarding.party;

import static jakarta.persistence.EnumType.STRING;

import ee.tuleva.onboarding.populationregister.CustodyRight;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Enumerated;

@Embeddable
public record LookedUpGuardian(
    @Column(nullable = false) String guardianPersonalCode,
    @Enumerated(STRING) @Column(nullable = false) CustodyRight.Type custodyType) {}
