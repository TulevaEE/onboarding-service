package ee.tuleva.onboarding.kyb;

import java.math.BigDecimal;

public record KybRelatedPerson(
    PersonalCode personalCode,
    boolean boardMember,
    boolean shareholder,
    boolean beneficialOwner,
    BigDecimal ownershipPercent,
    KybKycStatus kycStatus) {}
