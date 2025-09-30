package ee.tuleva.onboarding.ledger;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.*;
import static java.math.BigDecimal.ZERO;
import static org.hibernate.generator.EventType.INSERT;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "transaction", schema = "ledger")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"entries"})
public class LedgerTransaction {

  public enum TransactionType {
    TRANSFER
  }

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private UUID id;

  @Enumerated(STRING)
  @NotNull
  private TransactionType transactionType;

  @NotNull private Instant transactionDate;

  @Type(JsonType.class)
  @Column(columnDefinition = "JSONB")
  @NotNull
  private Map<String, Object> metadata = new HashMap<>();

  /*@Column(name = "event_log_id", nullable = false)
  private Integer eventLogId; // TODO event log map*/

  @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
  private List<LedgerEntry> entries = new ArrayList<>();

  @Column(nullable = false, updatable = false, insertable = false)
  @Generated(event = INSERT)
  private Instant createdAt;

  public BigDecimal sum() {
    return entries.stream().map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);
  }

  public LedgerEntry addEntry(LedgerAccount account, BigDecimal amount) {
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

  @Builder
  public LedgerTransaction(
      TransactionType transactionType, Instant transactionDate, Map<String, Object> metadata) {
    this.transactionType = transactionType;
    this.transactionDate = transactionDate;
    this.metadata = metadata;
  }
}
