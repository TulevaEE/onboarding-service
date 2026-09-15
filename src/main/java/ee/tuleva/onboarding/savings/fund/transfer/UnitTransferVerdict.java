package ee.tuleva.onboarding.savings.fund.transfer;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Either what the transfer would do, or why it will not happen. Neither writes anything. */
public sealed interface UnitTransferVerdict {

  record Planned(String planHash, Plan plan) implements UnitTransferVerdict {}

  record Refused(String refused) implements UnitTransferVerdict {}

  record Plan(
      String fromCode,
      String toCode,
      BigDecimal fundUnits,
      BigDecimal transferredSubscriptionsEur,
      BigDecimal giverUnitsAfter,
      BigDecimal receiverUnitsAfter,
      @Nullable BigDecimal recipientAcquisitionCostEur) {}
}
