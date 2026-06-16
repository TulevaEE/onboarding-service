package ee.tuleva.onboarding.kyb;

import java.math.BigDecimal;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record KybRelatedPerson(
    @Nullable PersonalCode personalCode,
    boolean naturalPerson,
    boolean boardMember,
    boolean shareholder,
    boolean beneficialOwner,
    BigDecimal ownershipPercent,
    KybKycStatus kycStatus) {}
