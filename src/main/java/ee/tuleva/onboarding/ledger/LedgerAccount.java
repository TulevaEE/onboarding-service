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

  public enum AccountPurpose {
    USER_ACCOUNT,
    SYSTEM_ACCOUNT
  }

  public enum AssetType {
    EUR,
    FUND_UNIT
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(nullable = false)
  private UUID id;

  private String name;

  @Enumerated(EnumType.STRING)
  @Column(columnDefinition = "ledger.account_purpose")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private AccountPurpose accountPurpose;

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

  // TODO fetchType only needed for integration tests that don't interact via requests
  @OneToMany(mappedBy = "account", fetch = FetchType.EAGER)
  private List<LedgerEntry> entries = List.of();

  public BigDecimal getBalance() {
    return entries.stream().map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);
  }
}
