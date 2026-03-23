package ee.tuleva.onboarding.kyb;

import java.math.BigDecimal;

public record KybRelatedPerson(
    String personalCode,
    boolean boardMember,
    boolean shareholder,
    boolean beneficialOwner,
    BigDecimal ownershipPercent) {}
