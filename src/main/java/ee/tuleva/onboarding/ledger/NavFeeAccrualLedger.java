package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
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

  @Transactional
  public void settleFeeAccrual(
      TulevaFund fund, LocalDate settlementDate, SystemAccount feeAccount, BigDecimal amount) {
    if (amount == null || amount.signum() == 0) {
      log.info(
          "Skipping zero fee settlement: fund={}, date={}, feeAccount={}",
          fund,
          settlementDate,
          feeAccount.name());
      return;
    }

    UUID externalReference = generateSettlementReference(fund, settlementDate, feeAccount);
    if (ledgerTransactionService.existsByExternalReferenceAndTransactionType(
        externalReference, FEE_SETTLEMENT)) {
      log.info(
          "Fee settlement already recorded: fund={}, date={}, feeAccount={}",
          fund,
          settlementDate,
          feeAccount.name());
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
            "settlementDate",
            settlementDate.toString());

    Instant transactionDate = settlementDate.atTime(12, 0).atZone(ESTONIAN_ZONE).toInstant();
    ledgerTransactionService.createTransaction(
        FEE_SETTLEMENT,
        transactionDate,
        externalReference,
        metadata,
        entry(getSystemAccount(feeAccount, fund), amount),
        entry(getSystemAccount(NAV_EQUITY, fund), amount.negate()));
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

  // TODO: should come from the settlement payment instead
  private UUID generateSettlementReference(
      TulevaFund fund, LocalDate settlementDate, SystemAccount feeAccount) {
    String key = fund.name() + ":" + settlementDate + ":" + feeAccount.name() + ":SETTLEMENT";
    return UUID.nameUUIDFromBytes(key.getBytes(UTF_8));
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
