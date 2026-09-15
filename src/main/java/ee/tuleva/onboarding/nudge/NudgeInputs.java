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
    PaymentRateSeason paymentRateSeason) {

  static final BigDecimal HIGH_FEE_FROM = new BigDecimal("0.003");

  boolean secondPillarInLowFeeFund() {
    return secondPillarFee != null && secondPillarFee.compareTo(HIGH_FEE_FROM) < 0;
  }

  boolean secondPillarInHighFeeFund() {
    return isHigh(secondPillarFee);
  }

  boolean thirdPillarInHighFeeFund() {
    return isHigh(thirdPillarFee);
  }

  private static boolean isHigh(@Nullable BigDecimal fee) {
    return fee != null && fee.compareTo(HIGH_FEE_FROM) >= 0;
  }
}
