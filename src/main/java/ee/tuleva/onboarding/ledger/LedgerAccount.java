package ee.tuleva.onboarding.ledger;

import static java.math.BigDecimal.ZERO;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "account", schema = "ledger")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerAccount {

  public enum AccountType {
    ASSET,
    LIABILITY,
    INCOME,
    EXPENSE
  }

  public enum ServiceAccountType {
    DEPOSIT_EUR,
    EMISSION_UNIT
  }

  public enum AssetType {
    EUR,
    UNIT
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(nullable = false)
  private UUID id;

  private String name;

  @Enumerated(EnumType.STRING)
  @Column(columnDefinition = "ledger.service_account_type")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private ServiceAccountType serviceAccountType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ledger.account_type")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private AccountType type;

  @ManyToOne()
  @JoinColumn(name = "owner_party_id")
  private LedgerParty ledgerParty;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AssetType assetTypeCode;

  @Column(columnDefinition = "TIMESTAMPTZ", nullable = false, updatable = false, insertable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "account")
  private List<LedgerEntry> entries = List.of();

  public BigDecimal getBalance() {
    return entries.stream().map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);
  }
}
