package ee.tuleva.onboarding.savings.fund.transfer;

import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record UnitTransferSummary(
    @Nullable UUID id,
    String fromCode,
    PartyType fromType,
    String toCode,
    PartyType toType,
    BigDecimal fundUnits,
    @Nullable BigDecimal recipientAcquisitionCostEur,
    LocalDate notifiedAt,
    String evidence,
    BigDecimal giverPaidInEur,
    BigDecimal giverUnitsOwned,
    String planHash,
    UnitTransferState state,
    String submittedBy,
    @Nullable String approvedBy,
    @Nullable UUID ledgerTransactionId,
    @Nullable Instant createdAt,
    @Nullable Instant executedAt) {

  static UnitTransferSummary of(UnitTransfer transfer) {
    return new UnitTransferSummary(
        transfer.getId(),
        transfer.getFromPartyCode(),
        transfer.getFromPartyType(),
        transfer.getToPartyCode(),
        transfer.getToPartyType(),
        transfer.getFundUnits(),
        transfer.getRecipientAcquisitionCostEur(),
        transfer.getNotifiedAt(),
        transfer.getEvidence(),
        transfer.getGiverPaidInEur(),
        transfer.getGiverUnitsOwned(),
        transfer.getPlanHash(),
        transfer.getState(),
        transfer.getSubmittedBy(),
        transfer.getApprovedBy(),
        transfer.getLedgerTransactionId(),
        transfer.getCreatedAt(),
        transfer.getExecutedAt());
  }
}
