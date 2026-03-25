package ee.tuleva.onboarding.kyb.survey;

import ee.tuleva.onboarding.kyb.KybKycStatus;
import java.math.BigDecimal;

record RelatedPersonData(
    String personalCode,
    String name,
    boolean boardMember,
    boolean shareholder,
    boolean beneficialOwner,
    BigDecimal ownershipPercent,
    KybKycStatus kycStatus) {}
