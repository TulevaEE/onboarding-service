package ee.tuleva.onboarding.ledger;

import static java.math.BigDecimal.ZERO;
import static org.hibernate.generator.EventType.INSERT;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import jakarta.validation.constraints.NotNull;
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
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type_id")
  @NotNull
  private TransactionType transactionType;

  @NotNull
  private Instant transactionDate;

  @Type(JsonType.class)
  @Column(columnDefinition = "JSONB")
  @NotNull
  private Map<String, Object> metadata = new HashMap<>();

  /*@Column(name = "event_log_id", nullable = false)
  private Integer eventLogId; // TODO event log map*/

  @OneToMany(
      mappedBy = "transaction",
      cascade = CascadeType.ALL
  )
  private List<LedgerEntry> entries = new ArrayList<>();

  @Column(nullable = false, updatable = false, insertable = false)
  @Generated(event = INSERT)
  private Instant createdAt;

  public BigDecimal sum() {
    return entries.stream().map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);
  }

  public LedgerEntry addEntry(LedgerAccount account, BigDecimal amount) {
    var entry = LedgerEntry.builder()
        .amount(amount)
        .transaction(this)
        .account(account)
        .build();

    entries.add(entry);
    account.addEntry(entry);

    return entry;
  }

  @Builder
  public LedgerTransaction(String description, TransactionType transactionType, Instant transactionDate, Map<String, Object> metadata) {
    this.description = description;
    this.transactionType = transactionType;
    this.transactionDate = transactionDate;
    this.metadata = metadata;
  }
}
