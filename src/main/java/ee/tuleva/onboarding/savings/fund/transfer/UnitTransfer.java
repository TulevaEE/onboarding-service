package ee.tuleva.onboarding.savings.fund.transfer;

import static ee.tuleva.onboarding.savings.fund.transfer.UnitTransferState.AWAITING_APPROVAL;
import static ee.tuleva.onboarding.savings.fund.transfer.UnitTransferState.CANCELLED;
import static ee.tuleva.onboarding.savings.fund.transfer.UnitTransferState.EXECUTED;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.UUID;

import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import ee.tuleva.onboarding.ledger.PartyRef;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "savings_fund_unit_transfer")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitTransfer {

  @Id
  @GeneratedValue(strategy = UUID)
  private @Nullable UUID id;

  @Column(name = "from_party_code", nullable = false)
  private String fromPartyCode;

  @Enumerated(STRING)
  @Column(name = "from_party_type", columnDefinition = "ledger.party_type", nullable = false)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private PartyType fromPartyType;

  @Column(name = "to_party_code", nullable = false)
  private String toPartyCode;

  @Enumerated(STRING)
  @Column(name = "to_party_type", columnDefinition = "ledger.party_type", nullable = false)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private PartyType toPartyType;

  @Column(name = "fund_units", nullable = false)
  private BigDecimal fundUnits;

  @Column(name = "recipient_acquisition_cost_eur")
  private @Nullable BigDecimal recipientAcquisitionCostEur;

  @Column(name = "notified_at", nullable = false)
  private LocalDate notifiedAt;

  @Column(nullable = false)
  private String evidence;

  @Column(name = "plan_hash", nullable = false)
  private String planHash;

  @Enumerated(STRING)
  @Column(columnDefinition = "savings_fund_unit_transfer_state", nullable = false)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Builder.Default
  private UnitTransferState state = AWAITING_APPROVAL;

  @Column(name = "submitted_by", nullable = false)
  private String submittedBy;

  @Column(name = "approved_by")
  private @Nullable String approvedBy;

  @Column(name = "ledger_transaction_id")
  private @Nullable UUID ledgerTransactionId;

  @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
  private @Nullable Instant createdAt;

  @Column(name = "executed_at")
  private @Nullable Instant executedAt;

  @Column(name = "cancelled_at")
  private @Nullable Instant cancelledAt;

  public PartyRef from() {
    return new PartyRef(fromPartyType, fromPartyCode);
  }

  public PartyRef to() {
    return new PartyRef(toPartyType, toPartyCode);
  }

  void executedBy(String approver, UUID ledgerTransaction, Instant when) {
    state = EXECUTED;
    approvedBy = approver;
    ledgerTransactionId = ledgerTransaction;
    executedAt = when;
  }

  void cancelled(Instant when) {
    state = CANCELLED;
    cancelledAt = when;
  }

  boolean isAwaitingApproval() {
    return state == AWAITING_APPROVAL;
  }
}
