package ee.tuleva.onboarding.savings.fund.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record UnitTransferSummary(
    @Nullable UUID id,
    String fromCode,
    String toCode,
    BigDecimal fundUnits,
    LocalDate notifiedAt,
    String evidence,
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
        transfer.getToPartyCode(),
        transfer.getFundUnits(),
        transfer.getNotifiedAt(),
        transfer.getEvidence(),
        transfer.getState(),
        transfer.getSubmittedBy(),
        transfer.getApprovedBy(),
        transfer.getLedgerTransactionId(),
        transfer.getCreatedAt(),
        transfer.getExecutedAt());
  }
}
