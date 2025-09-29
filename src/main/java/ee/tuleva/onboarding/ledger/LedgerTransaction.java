package ee.tuleva.onboarding.ledger;

import static java.math.BigDecimal.ZERO;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "transaction", schema = "ledger")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerTransaction {

  public enum TransactionType {
    TRANSFER
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(nullable = false)
  private UUID id;

  @Column(nullable = false)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type_id", nullable = false)
  private TransactionType transactionTypeId;

  @Column(name = "transaction_date", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant transactionDate;

  @Type(JsonType.class)
  @Column(columnDefinition = "JSONB", nullable = false)
  private Map<String, Object> metadata;

  /*@Column(name = "event_log_id", nullable = false)
  private Integer eventLogId; // TODO event log map*/

  @Column(columnDefinition = "TIMESTAMPTZ", nullable = false, updatable = false, insertable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "transaction")
  private List<LedgerEntry> entries;

  public BigDecimal sum() {
    return entries.stream().map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);
  }
}
