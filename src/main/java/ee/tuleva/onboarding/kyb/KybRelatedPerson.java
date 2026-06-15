package ee.tuleva.onboarding.kyb;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record KybRelatedPerson(
    @Nullable PersonalCode personalCode,
    boolean boardMember,
    boolean shareholder,
    boolean beneficialOwner,
    BigDecimal ownershipPercent,
    KybKycStatus kycStatus) {}
