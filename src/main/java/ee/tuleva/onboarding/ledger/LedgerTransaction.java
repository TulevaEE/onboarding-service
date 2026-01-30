package ee.tuleva.onboarding.ledger;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.UUID;
import static java.math.BigDecimal.ZERO;
import static org.hibernate.generator.EventType.INSERT;

import ee.tuleva.onboarding.ledger.validation.AssetTypeConsistency;
import ee.tuleva.onboarding.ledger.validation.BalancedTransaction;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.Type;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "transaction", schema = "ledger")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"entries"})
@BalancedTransaction
@AssetTypeConsistency
public class LedgerTransaction {

  public enum TransactionType {
    TRANSFER,
    PAYMENT_RECEIVED,
    PAYMENT_CANCEL_REQUESTED,
    PAYMENT_CANCELLED,
    UNATTRIBUTED_PAYMENT,
    PAYMENT_BOUNCE_BACK,
    PAYMENT_RESERVED,
    FUND_SUBSCRIPTION,
    FUND_TRANSFER,
    LATE_ATTRIBUTION,
    REDEMPTION_RESERVED,
    REDEMPTION_CANCELLED,
    REDEMPTION_REQUEST,
    FUND_CASH_TRANSFER,
    REDEMPTION_PAYOUT,
    INTEREST_RECEIVED,
    BANK_FEE,
    BANK_ADJUSTMENT
  }

  @Id
  @GeneratedValue(strategy = UUID)
  private UUID id;

  @Enumerated(STRING)
  @Column(columnDefinition = "ledger.transaction_type")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @NotNull
  private TransactionType transactionType;

  @NotNull private Instant transactionDate;

  @Column(name = "external_reference")
  private UUID externalReference;

  @Type(JsonType.class)
  @Column(columnDefinition = "JSONB")
  @NotNull
  @Builder.Default
  private Map<String, Object> metadata = new HashMap<>();

  /*@Column(name = "event_log_id", nullable = false)
  private Integer eventLogId; // TODO event log map*/

  @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
  @Size(min = 2, message = "Transaction must have at least 2 entries for double-entry bookkeeping")
  @Builder.Default
  private List<LedgerEntry> entries = new ArrayList<>();

  @Column(nullable = false, updatable = false, insertable = false)
  @Generated(event = INSERT)
  private Instant createdAt;

  public BigDecimal sum() {
    return entries.stream().map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);
  }

  LedgerEntry addEntry(LedgerAccount account, BigDecimal amount) {
    if (account == null) {
      throw new IllegalArgumentException("Account cannot be null");
    }
    if (amount == null) {
      throw new IllegalArgumentException("Amount cannot be null");
    }

    var entry =
        LedgerEntry.builder()
            .amount(amount)
            .assetType(account.getAssetType())
            .account(account)
            .transaction(this)
            .build();

    entries.add(entry);
    account.addEntry(entry);

    return entry;
  }
}
