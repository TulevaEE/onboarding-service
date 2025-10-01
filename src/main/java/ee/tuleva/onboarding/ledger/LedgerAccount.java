package ee.tuleva.onboarding.ledger;

import static jakarta.persistence.EnumType.STRING;
import static java.math.BigDecimal.ZERO;
import static org.hibernate.generator.EventType.INSERT;

import ee.tuleva.onboarding.ledger.validation.AccountEntryConsistency;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.jetbrains.annotations.Nullable;

@Entity
@Table(name = "account", schema = "ledger")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"entries"})
@AccountEntryConsistency
public class LedgerAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  @Nullable
  @Size(max = 255, message = "Account name cannot exceed 255 characters")
  private String name;

  @Enumerated(STRING)
  @Column(columnDefinition = "ledger.account_purpose")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @NotNull
  private AccountPurpose purpose;

  public enum AccountPurpose {
    USER_ACCOUNT,
    SYSTEM_ACCOUNT
  }

  @Enumerated(STRING)
  @Column(nullable = false, columnDefinition = "ledger.account_type")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @NotNull
  private AccountType accountType;

  public enum AccountType {
    ASSET,
    LIABILITY,
    INCOME,
    EXPENSE
  }

  @ManyToOne
  @JoinColumn(name = "owner_party_id")
  private LedgerParty owner;

  @Enumerated(STRING)
  @NotNull
  private AssetType assetType;

  public enum AssetType {
    EUR,
    FUND_UNIT
  }

  @OneToMany(mappedBy = "account")
  private List<LedgerEntry> entries = new ArrayList<>();

  @Column(nullable = false, updatable = false, insertable = false)
  @Generated(event = INSERT)
  private Instant createdAt;

  public BigDecimal getBalance() {
    if (entries == null || entries.isEmpty()) return ZERO;
    return entries.stream().map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);
  }

  void addEntry(LedgerEntry entry) {
    if (entry == null) {
      throw new IllegalArgumentException("Entry cannot be null");
    }
    if (entry.getAssetType() != null && !entry.getAssetType().equals(this.assetType)) {
      throw new IllegalArgumentException(
          "Entry asset type "
              + entry.getAssetType()
              + " does not match account asset type "
              + this.assetType);
    }
    entry.setAccount(this);
    entry.setAssetType(this.assetType);
    entries.add(entry);
  }

  @Builder
  private LedgerAccount(
      @Nullable String name,
      AccountPurpose purpose,
      AccountType accountType,
      LedgerParty owner,
      AssetType assetType) {
    this.name = name;
    this.purpose = purpose;
    this.accountType = accountType;
    this.owner = owner;
    this.assetType = assetType;
  }
}
