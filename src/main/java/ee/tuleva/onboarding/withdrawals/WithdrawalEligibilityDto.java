package ee.tuleva.onboarding.withdrawals;

import java.time.LocalDate;
import lombok.Builder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Builder
@NullMarked
public record WithdrawalEligibilityDto(
    boolean hasReachedEarlyRetirementAge,
    boolean canWithdrawThirdPillarWithReducedTax,
    @Nullable LocalDate canWithdrawThirdPillarWithReducedTaxFrom,
    LocalDate earlyRetirementDate,
    int age,
    int recommendedDurationYears,
    boolean arrestsOrBankruptciesPresent) {}
