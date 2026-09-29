package ee.tuleva.onboarding.ledger;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.UUID;
import static java.math.BigDecimal.ZERO;
import static org.hibernate.generator.EventType.INSERT;
import static org.hibernate.type.SqlTypes.JSON;

import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey;
import ee.tuleva.onboarding.ledger.validation.AssetTypeConsistency;
import ee.tuleva.onboarding.ledger.validation.BalancedTransaction;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "transaction", schema = "ledger")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"entries"})
@BalancedTransaction
@AssetTypeConsistency
public class LedgerTransaction {

  public enum TransactionType {
    @Deprecated // Use more specific types instead of TRANSFER
    TRANSFER,
    PAYMENT_RECEIVED,
    PAYMENT_CANCEL_REQUESTED,
    PAYMENT_CANCELLED,
    UNATTRIBUTED_PAYMENT,
    PAYMENT_BOUNCE_BACK,
    UNATTRIBUTED_PAYMENT_RECONCILED,
    PAYMENT_RESERVED,
    FUND_SUBSCRIPTION,
    FUND_TRANSFER,
    REDEMPTION_RESERVED,
    REDEMPTION_CANCELLED,
    REDEMPTION_REQUEST,
    FUND_CASH_TRANSFER,
    REDEMPTION_PAYOUT,
    INTEREST_RECEIVED,
    BANK_FEE,
    BANK_ADJUSTMENT,
    ADJUSTMENT,
    TRADE_SETTLEMENT,
    POSITION_UPDATE,
    MANAGEMENT_FEE_PAYMENT,
    FEE_ACCRUAL,
    FEE_SETTLEMENT,
    UNIT_COUNT_UPDATE,
    MANAGEMENT_FEE_REBATE,
    REGISTRAR_CONTRIBUTION,
    REGISTRAR_PAYOUT,
    UNCLASSIFIED_BANK_ENTRY,
    OPENING_BALANCE,
    OWN_ACCOUNT_TRANSFER,
    UNIT_TRANSFER
  }

  @Id
  @GeneratedValue(strategy = UUID)
  private UUID id;

  @Enumerated(STRING)
  @Column(columnDefinition = "ledger.transaction_type")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @NotNull
  private TransactionType transactionType;

  @NotNull private Instant transactionDate;

  @Column(name = "external_reference")
  @Nullable
  private UUID externalReference;

  @JdbcTypeCode(JSON)
  @NotNull
  @Builder.Default
  private Map<String, Object> metadata = new HashMap<>();

  /*@Column(name = "event_log_id", nullable = false)
  private Integer eventLogId; // TODO event log map*/

  @OneToMany(mappedBy = "transaction", cascade = ALL)
  @Size(min = 2, message = "Transaction must have at least 2 entries for double-entry bookkeeping")
  @Builder.Default
  private List<LedgerEntry> entries = new ArrayList<>();

  @Column(nullable = false, updatable = false, insertable = false)
  @Generated(event = INSERT)
  private Instant createdAt;

  public BigDecimal sum() {
    return entries.stream().map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);
  }

  LedgerEntry addEntry(LedgerAccount account, BigDecimal amount) {
    if (account == null) {
      throw new IllegalArgumentException("Account cannot be null");
    }
    if (amount == null) {
      throw new IllegalArgumentException("Amount cannot be null");
    }

    BigDecimal normalizedAmount = normalizeScale(amount, account.getAssetType());

    var entry =
        LedgerEntry.builder()
            .amount(normalizedAmount)
            .assetType(account.getAssetType())
            .account(account)
            .transaction(this)
            .build();

    entries.add(entry);
    account.addEntry(entry);

    return entry;
  }

  public Optional<BigDecimal> findUserFundUnits() {
    return entries.stream()
        .filter(LedgerEntry::isUserFundUnit)
        .findFirst()
        .map(entry -> entry.getAmount().abs());
  }

  Optional<BigDecimal> findFundUnitsChangeOf(@Nullable LedgerParty party) {
    return entries.stream()
        .filter(LedgerEntry::isUserFundUnit)
        .filter(entry -> entry.getAccount().isOwnedBy(party))
        .findFirst()
        .map(entry -> entry.getAmount().negate());
  }

  public Optional<BigDecimal> findNavPerUnit() {
    return findMetadataAmount(MetadataKey.NAV_PER_UNIT);
  }

  Optional<TransactionType> findOperationType() {
    return Optional.ofNullable(metadata.get(MetadataKey.OPERATION_TYPE.getKey()))
        .map(Object::toString)
        .flatMap(LedgerTransaction::parseTransactionType);
  }

  private static Optional<TransactionType> parseTransactionType(String name) {
    return Arrays.stream(TransactionType.values())
        .filter(transactionType -> transactionType.name().equals(name))
        .findFirst();
  }

  public Optional<BigDecimal> findRecipientAcquisitionCost() {
    return findMetadataAmount(MetadataKey.RECIPIENT_ACQUISITION_COST_EUR);
  }

  private Optional<BigDecimal> findMetadataAmount(MetadataKey key) {
    Object value = metadata.get(key.getKey());
    return Optional.ofNullable(value).map(amount -> new BigDecimal(amount.toString()));
  }

  public Optional<LocalDate> findNavDate() {
    Object navDate = metadata.get("navDate");
    return Optional.ofNullable(navDate).map(value -> LocalDate.parse(value.toString()));
  }

  private static BigDecimal normalizeScale(BigDecimal amount, AssetType assetType) {
    int strippedScale = amount.stripTrailingZeros().scale();
    if (strippedScale <= assetType.getMaxPrecision()) {
      return amount.setScale(assetType.getMaxPrecision());
    }
    return amount;
  }
}
