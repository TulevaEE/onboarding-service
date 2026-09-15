package ee.tuleva.onboarding.savings.fund.transfer;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Either what the transfer would do, or why it will not happen. Neither writes anything. */
public sealed interface UnitTransferVerdict {

  record Planned(String planHash, Plan plan) implements UnitTransferVerdict {}

  record Refused(String refused) implements UnitTransferVerdict {}

  /**
   * Only units move. The giver's paid-in amount stays with the giver, because they did pay it and
   * the recipient did not; what the recipient may deduct for tax is their own expense and is
   * recorded on the transfer instead.
   */
  record Plan(
      String fromCode,
      String toCode,
      BigDecimal fundUnits,
      BigDecimal giverUnitsAfter,
      BigDecimal receiverUnitsAfter,
      @Nullable BigDecimal recipientAcquisitionCostEur) {}
}
