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
  @Column(columnDefinition = "ledger.account_type")
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
  @Column(columnDefinition = "ledger.asset_type")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @NotNull
  private AssetType assetType;

  @Getter
  @RequiredArgsConstructor
  public enum AssetType {
    EUR(0, 2), // Allows 0, 1, or 2 decimal places
    FUND_UNIT(5, 5); // Requires exactly 5 decimal places

    private final int minPrecision;
    private final int maxPrecision;

    public boolean requiresExactPrecision() {
      return minPrecision == maxPrecision;
    }
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

  public BigDecimal getBalanceAt(Instant date) {
    if (entries == null || entries.isEmpty()) return ZERO;
    return entries.stream()
        .filter(entry -> !entry.getTransaction().getTransactionDate().isAfter(date))
        .map(LedgerEntry::getAmount)
        .reduce(ZERO, BigDecimal::add);
  }

  public BigDecimal getBalanceBetween(Instant startDate, Instant endDate) {
    if (entries == null || entries.isEmpty()) return ZERO;
    if (startDate.isAfter(endDate)) {
      throw new IllegalArgumentException("Start date must be before or equal to end date");
    }
    return entries.stream()
        .filter(
            entry -> {
              Instant txDate = entry.getTransaction().getTransactionDate();
              return !txDate.isBefore(startDate) && !txDate.isAfter(endDate);
            })
        .map(LedgerEntry::getAmount)
        .reduce(ZERO, BigDecimal::add);
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
