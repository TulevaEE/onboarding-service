package ee.tuleva.onboarding.ledger;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.*;
import static org.hibernate.generator.EventType.INSERT;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "entry", schema = "ledger")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"account", "transaction"})
public class LedgerEntry {
  @Id
  @GeneratedValue(strategy = IDENTITY)
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "account_id", nullable = false)
  @JsonIgnore
  @Setter(AccessLevel.PACKAGE)
  private LedgerAccount account;

  @ManyToOne
  @JoinColumn(name = "transaction_id", nullable = false)
  @JsonIgnore
  @Setter(AccessLevel.PACKAGE)
  private LedgerTransaction transaction;

  @NotNull private BigDecimal amount;

  @NotNull
  @Setter(AccessLevel.PACKAGE)
  @Enumerated(STRING)
  private AssetType assetType;

  @Column(nullable = false, updatable = false, insertable = false)
  @Generated(event = INSERT)
  private Instant createdAt;
}
