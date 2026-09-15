package ee.tuleva.onboarding.nudge;

import java.math.BigDecimal;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder(toBuilder = true)
record NudgeInputs(
    boolean adult,
    boolean reachedRetirementAge,
    boolean member,
    boolean secondPillarActive,
    boolean thirdPillarActive,
    boolean secondPillarPartiallyConverted,
    boolean secondPillarFullyConverted,
    @Nullable BigDecimal secondPillarFee,
    boolean thirdPillarPartiallyConverted,
    boolean thirdPillarFullyConverted,
    @Nullable BigDecimal thirdPillarFee,
    boolean canIncreasePaymentRate,
    boolean pendingSecondPillarTransfer,
    boolean pendingSecondPillarWithdrawal,
    Known leftSecondPillar,
    Known thirdPillarRecurring,
    Known savingsFundRecurring,
    Known savesInSavingsFund,
    Known savingsFundSaver,
    Known taxHeadroom,
    @Nullable FeeComparison feeComparison,
    @Nullable BigDecimal savingsFundFeePercent,
    PaymentRateSeason paymentRateSeason) {}
