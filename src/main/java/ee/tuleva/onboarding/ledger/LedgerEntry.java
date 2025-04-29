package ee.tuleva.onboarding.ledger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "entry", schema = "ledger")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(nullable = false)
  private UUID id;

  @ManyToOne()
  @JoinColumn(name = "account_id", nullable = false)
  @JsonIgnore
  private LedgerAccount account;

  @ManyToOne()
  @JoinColumn(name = "transaction_id", nullable = false)
  @JsonIgnore
  private LedgerTransaction transaction;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(columnDefinition = "TIMESTAMPTZ", nullable = false, updatable = false, insertable = false)
  private Instant createdAt;
}
