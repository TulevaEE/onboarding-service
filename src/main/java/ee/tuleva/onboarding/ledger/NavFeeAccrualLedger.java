package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NavFeeAccrualLedger {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;
  private final JdbcClient jdbcClient;

  @Transactional
  public void recordFeeAccrual(
      TulevaFund fund,
      LocalDate accrualDate,
      SystemAccount feeAccount,
      BigDecimal amount,
      Map<String, Object> metadata) {
    if (amount == null || amount.signum() == 0) {
      log.info(
          "Skipping zero fee accrual: fund={}, date={}, feeAccount={}",
          fund,
          accrualDate,
          feeAccount.name());
      return;
    }

    UUID externalReference = generateAccrualReference(fund, accrualDate, feeAccount);
    if (ledgerTransactionService.existsByExternalReferenceAndTransactionType(
        externalReference, FEE_ACCRUAL)) {
      log.info(
          "Fee accrual already recorded: fund={}, date={}, feeAccount={}",
          fund,
          accrualDate,
          feeAccount.name());
      return;
    }

    log.info(
        "Creating fee accrual ledger entry: fund={}, date={}, feeAccount={}, amount={}",
        fund,
        accrualDate,
        feeAccount.name(),
        amount);
    Instant transactionDate = accrualDate.atTime(9, 0).atZone(ESTONIAN_ZONE).toInstant();
    ledgerTransactionService.createTransaction(
        FEE_ACCRUAL,
        transactionDate,
        externalReference,
        metadata,
        entry(getSystemAccount(NAV_EQUITY, fund), amount),
        entry(getSystemAccount(feeAccount, fund), amount.negate()));
  }

  public FeeMonthSettlement settlementOf(
      TulevaFund fund, LocalDate feeMonth, SystemAccount feeAccount) {
    return new FeeMonthSettlement(
        jdbcClient
            .sql(
                """
                SELECT t.transaction_date, e.amount
                FROM ledger.entry e
                JOIN ledger.account a ON e.account_id = a.id
                JOIN ledger.transaction t ON e.transaction_id = t.id
                WHERE t.external_reference IN (:externalReferences)
                  AND t.transaction_type = CAST('FEE_SETTLEMENT' AS ledger.transaction_type)
                  AND a.name = :accountName
                ORDER BY t.transaction_date, t.id
                """)
            .param("externalReferences", FeeMonthSettlement.references(fund, feeMonth, feeAccount))
            .param("accountName", feeAccount.getAccountName(fund))
            .query(
                (rs, rowNum) ->
                    new FeeMonthSettlement.Revision(
                        rs.getTimestamp("transaction_date")
                            .toInstant()
                            .atZone(ESTONIAN_ZONE)
                            .toLocalDate(),
                        rs.getBigDecimal("amount")))
            .list());
  }

  public BigDecimal accruedIn(TulevaFund fund, LocalDate feeMonth, SystemAccount feeAccount) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(e.amount), 0)
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            JOIN ledger.transaction t ON e.transaction_id = t.id
            WHERE a.name = :accountName
              AND t.transaction_type <> CAST('FEE_SETTLEMENT' AS ledger.transaction_type)
              AND t.transaction_date >= :fromInclusive
              AND t.transaction_date < :toExclusive
            """)
        .param("accountName", feeAccount.getAccountName(fund))
        .param("fromInclusive", Timestamp.from(startOf(feeMonth)))
        .param("toExclusive", Timestamp.from(startOf(feeMonth.plusMonths(1))))
        .query(BigDecimal.class)
        .single()
        .negate();
  }

  @Transactional
  public void settleFeeAccrual(
      TulevaFund fund,
      LocalDate feeMonth,
      LocalDate settledOn,
      SystemAccount feeAccount,
      BigDecimal amount,
      int revision) {
    if (amount.signum() == 0) {
      log.info(
          "Skipping zero fee settlement: fund={}, feeMonth={}, feeAccount={}",
          fund,
          feeMonth,
          feeAccount.name());
      return;
    }
    if (revision >= FeeMonthSettlement.MAX_REVISIONS) {
      log.error(
          "Fee settlement revisions exhausted, left for the settlement check: fund={}, feeMonth={}, feeAccount={}, revision={}, amount={}",
          fund,
          feeMonth,
          feeAccount.name(),
          revision,
          amount);
      return;
    }

    UUID externalReference = FeeMonthSettlement.reference(fund, feeMonth, feeAccount, revision);
    if (ledgerTransactionService.existsByExternalReferenceAndTransactionType(
        externalReference, FEE_SETTLEMENT)) {
      log.info(
          "Fee settlement already recorded: fund={}, feeMonth={}, feeAccount={}, revision={}",
          fund,
          feeMonth,
          feeAccount.name(),
          revision);
      return;
    }

    Map<String, Object> metadata =
        Map.of(
            "operationType",
            "FEE_SETTLEMENT",
            "fund",
            fund.name(),
            "feeType",
            feeAccount.name(),
            "feeMonth",
            feeMonth.toString(),
            "settlementDate",
            settledOn.toString(),
            "revision",
            revision);

    Instant transactionDate = settledOn.atTime(12, 0).atZone(ESTONIAN_ZONE).toInstant();
    ledgerTransactionService.createTransaction(
        FEE_SETTLEMENT,
        transactionDate,
        externalReference,
        metadata,
        entry(getSystemAccount(feeAccount, fund), amount),
        entry(getSystemAccount(NAV_EQUITY, fund), amount.negate()));
  }

  @Transactional
  public void reviseFeeAccrual(
      TulevaFund fund,
      LocalDate accrualDate,
      SystemAccount feeAccount,
      BigDecimal delta,
      int revision,
      Map<String, Object> metadata) {
    if (delta.signum() == 0) {
      log.info(
          "Skipping zero fee accrual revision: fund={}, date={}, feeAccount={}",
          fund,
          accrualDate,
          feeAccount.name());
      return;
    }

    UUID externalReference = generateRevisionReference(fund, accrualDate, feeAccount, revision);
    if (ledgerTransactionService.existsByExternalReferenceAndTransactionType(
        externalReference, FEE_ACCRUAL)) {
      log.warn(
          "Fee accrual revision already recorded, skipping: fund={}, date={}, feeAccount={}, revision={}, requestedDelta={}",
          fund,
          accrualDate,
          feeAccount.name(),
          revision,
          delta);
      return;
    }

    log.info(
        "Creating fee accrual revision ledger entry: fund={}, date={}, feeAccount={}, revision={}, delta={}",
        fund,
        accrualDate,
        feeAccount.name(),
        revision,
        delta);
    Instant transactionDate = accrualDate.atTime(9, 0).atZone(ESTONIAN_ZONE).toInstant();
    ledgerTransactionService.createTransaction(
        FEE_ACCRUAL,
        transactionDate,
        externalReference,
        metadata,
        entry(getSystemAccount(NAV_EQUITY, fund), delta),
        entry(getSystemAccount(feeAccount, fund), delta.negate()));
  }

  @Transactional
  public BlackrockAdjustmentResult recordBlackrockAdjustment(
      TulevaFund fund, LocalDate date, BigDecimal targetBalance) {
    Instant cutoff = date.atTime(fund.getNavCutoffTime()).atZone(ESTONIAN_ZONE).toInstant();
    LedgerAccount adjustmentAccount = getSystemAccount(BLACKROCK_ADJUSTMENT, fund);
    BigDecimal currentBalance = adjustmentAccount.getBalanceAt(cutoff);
    BigDecimal delta = targetBalance.subtract(currentBalance);

    if (delta.signum() == 0) {
      log.info(
          "BlackRock adjustment already at target: fund={}, date={}, target={}",
          fund,
          date,
          targetBalance);
      return new BlackrockAdjustmentResult(fund, date, currentBalance, targetBalance, delta, false);
    }

    Instant transactionDate = date.atTime(8, 0).atZone(ESTONIAN_ZONE).toInstant();

    Map<String, Object> metadata =
        Map.of(
            "operationType", "BLACKROCK_ADJUSTMENT",
            "fund", fund.name(),
            "date", date.toString(),
            "targetBalance", targetBalance.toPlainString());

    ledgerTransactionService.createTransaction(
        ADJUSTMENT,
        transactionDate,
        UUID.randomUUID(),
        metadata,
        entry(adjustmentAccount, delta),
        entry(getSystemAccount(NAV_EQUITY, fund), delta.negate()));

    log.info(
        "Recorded BlackRock adjustment: fund={}, date={}, previous={}, target={}, delta={}",
        fund,
        date,
        currentBalance,
        targetBalance,
        delta);

    return new BlackrockAdjustmentResult(fund, date, currentBalance, targetBalance, delta, true);
  }

  @Transactional
  public int deleteFeeTransactionsByFund(TulevaFund fund) {
    String fundName = fund.name();
    int entriesDeleted =
        jdbcClient
            .sql(
                """
                DELETE FROM ledger.entry
                WHERE transaction_id IN (
                  SELECT id FROM ledger.transaction
                  WHERE transaction_type IN ('FEE_ACCRUAL', 'FEE_SETTLEMENT')
                    AND CAST(metadata AS VARCHAR) LIKE :fundPattern
                )
                """)
            .param("fundPattern", "%\"fund\":%\"" + fundName + "\"%")
            .update();

    int txDeleted =
        jdbcClient
            .sql(
                """
                DELETE FROM ledger.transaction
                WHERE transaction_type IN ('FEE_ACCRUAL', 'FEE_SETTLEMENT')
                  AND CAST(metadata AS VARCHAR) LIKE :fundPattern
                """)
            .param("fundPattern", "%\"fund\":%\"" + fundName + "\"%")
            .update();

    log.info(
        "Deleted fee transactions: fund={}, transactions={}, entries={}",
        fund,
        txDeleted,
        entriesDeleted);
    return txDeleted;
  }

  private UUID generateAccrualReference(
      TulevaFund fund, LocalDate accrualDate, SystemAccount feeAccount) {
    String key = fund.name() + ":" + accrualDate + ":" + feeAccount.name();
    return UUID.nameUUIDFromBytes(key.getBytes(UTF_8));
  }

  private UUID generateRevisionReference(
      TulevaFund fund, LocalDate accrualDate, SystemAccount feeAccount, int revision) {
    String key =
        fund.name() + ":" + accrualDate + ":" + feeAccount.name() + ":REVISION:" + revision;
    return UUID.nameUUIDFromBytes(key.getBytes(UTF_8));
  }

  private static Instant startOf(LocalDate date) {
    return date.atStartOfDay(ESTONIAN_ZONE).toInstant();
  }

  private LedgerAccount getSystemAccount(SystemAccount systemAccount, TulevaFund fund) {
    return ledgerAccountService
        .findSystemAccount(systemAccount, fund)
        .orElseGet(() -> ledgerAccountService.createSystemAccount(systemAccount, fund));
  }

  private LedgerEntryDto entry(LedgerAccount account, BigDecimal amount) {
    return new LedgerEntryDto(account, amount);
  }
}
