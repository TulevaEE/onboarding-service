package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.*;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.UUID;
import static org.hibernate.generator.EventType.INSERT;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.validation.EntryAccountConsistency;
import ee.tuleva.onboarding.ledger.validation.ValidAmountPrecision;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "entry", schema = "ledger")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"account", "transaction"})
@ValidAmountPrecision
@EntryAccountConsistency
public class LedgerEntry {
  @Id
  @GeneratedValue(strategy = UUID)
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

  @NotNull(message = "Entry amount cannot be null")
  @Digits(
      integer = 15,
      fraction = 5,
      message = "Amount must have max 15 integer digits and 5 decimal places")
  private BigDecimal amount;

  @NotNull
  @Setter(AccessLevel.PACKAGE)
  @Enumerated(STRING)
  @Column(columnDefinition = "ledger.asset_type")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private AssetType assetType;

  @Column(nullable = false, updatable = false, insertable = false)
  @Generated(event = INSERT)
  private Instant createdAt;

  boolean isUserFundUnit() {
    return assetType == FUND_UNIT && account.isUserAccount();
  }
}
