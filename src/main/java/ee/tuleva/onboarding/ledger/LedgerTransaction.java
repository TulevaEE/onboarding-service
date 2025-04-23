package ee.tuleva.onboarding.ledger;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.Type;

@Entity
@Getter
public class LedgerTransaction {

  @Id
  @Column(nullable = false)
  private UUID id;

  @Column(nullable = false)
  private String description;

  @Column(name = "transaction_type_id", nullable = false)
  private String transactionTypeId; // Consider mapping to a TransactionType entity if needed

  @Column(name = "transaction_date", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant transactionDate;

  @Type(JsonType.class)
  @Column(columnDefinition = "JSONB", nullable = false)
  private Map<String, Object> metadata;

  @Column(name = "event_log_id", nullable = false)
  private Integer eventLogId; // TODO event log map

  @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
  private Instant createdAt;


  @OneToMany(mappedBy = "transaction")
  private List<LedgerEntry> entries;

  public BigDecimal sum() {
    return entries.stream().map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);
  }
}
