package ee.tuleva.onboarding.ledger;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;


@Entity
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
