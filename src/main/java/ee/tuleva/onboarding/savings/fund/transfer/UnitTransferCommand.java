package ee.tuleva.onboarding.savings.fund.transfer;

import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UnitTransferCommand(
    @NotNull String fromCode,
    @NotNull PartyType fromType,
    @NotNull String toCode,
    @NotNull PartyType toType,
    @NotNull BigDecimal fundUnits,
    @NotNull LocalDate notifiedAt,
    @NotNull String evidence,
    @NotNull BigDecimal recipientAcquisitionCostEur) {}
