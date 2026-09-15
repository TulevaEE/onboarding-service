package ee.tuleva.onboarding.savings.fund.transfer;

import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Carries everything that decides what the transfer does, because the second person approves from
 * this and nothing else. Leaving out the party types, the recipient's acquisition cost or the plan
 * hash would ask them to approve a thing they were never shown.
 */
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
        transfer.getPlanHash(),
        transfer.getState(),
        transfer.getSubmittedBy(),
        transfer.getApprovedBy(),
        transfer.getLedgerTransactionId(),
        transfer.getCreatedAt(),
        transfer.getExecutedAt());
  }
}
