package ee.tuleva.onboarding.savings.fund.transfer;

import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import ee.tuleva.onboarding.ledger.PartyRef;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** What an operator was asked to transfer, and the notice they are acting on. */
public record UnitTransferCommand(
    @NotNull String fromCode,
    @NotNull PartyType fromType,
    @NotNull String toCode,
    @NotNull PartyType toType,
    @NotNull BigDecimal fundUnits,
    @NotNull LocalDate notifiedAt,
    @NotNull String evidence,
    @Nullable BigDecimal recipientAcquisitionCostEur) {

  public PartyRef from() {
    return new PartyRef(fromType, fromCode);
  }

  public PartyRef to() {
    return new PartyRef(toType, toCode);
  }
}
