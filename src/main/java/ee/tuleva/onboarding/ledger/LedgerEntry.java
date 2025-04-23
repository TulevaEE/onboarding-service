package ee.tuleva.onboarding.ledger;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Getter;

@Entity
@Table(name = "entry", schema = "ledger")
@Getter
public class LedgerEntry {
  @Id
  @Column(nullable = false)
  private UUID id;

  @ManyToOne()
  @JoinColumn(name = "account_id", nullable = false)
  private LedgerAccount account;

  @ManyToOne()
  @JoinColumn(name = "transaction_id", nullable = false)
  private LedgerTransaction transaction;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ", nullable = false)
  private Instant createdAt;
}
