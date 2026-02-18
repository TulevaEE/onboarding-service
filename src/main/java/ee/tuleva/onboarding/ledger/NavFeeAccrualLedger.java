package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRANSFER;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NavFeeAccrualLedger {

  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;
  private final Clock clock;

  @Transactional
  public void recordFeeAccrual(
      String fund, LocalDate accrualDate, SystemAccount feeAccount, BigDecimal amount) {
    if (amount == null || amount.signum() == 0) {
      return;
    }

    UUID externalReference = generateAccrualReference(fund, accrualDate, feeAccount);
    if (ledgerTransactionService.existsByExternalReference(externalReference)) {
      log.debug(
          "Fee accrual already recorded: fund={}, date={}, feeAccount={}",
          fund,
          accrualDate,
          feeAccount.name());
      return;
    }

    Map<String, Object> metadata =
        Map.of(
            "operationType",
            "FEE_ACCRUAL",
            "fund",
            fund,
            "feeType",
            feeAccount.name(),
            "accrualDate",
            accrualDate.toString());

    ledgerTransactionService.createTransaction(
        TRANSFER,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(getNavEquityAccount(), amount),
        entry(getSystemAccount(feeAccount), amount.negate()));
  }

  @Transactional
  public void settleFeeAccrual(
      String fund, LocalDate settlementDate, SystemAccount feeAccount, BigDecimal amount) {
    if (amount == null || amount.signum() == 0) {
      return;
    }

    UUID externalReference = generateSettlementReference(fund, settlementDate, feeAccount);
    if (ledgerTransactionService.existsByExternalReference(externalReference)) {
      log.debug(
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
            fund,
            "feeType",
            feeAccount.name(),
            "settlementDate",
            settlementDate.toString());

    ledgerTransactionService.createTransaction(
        TRANSFER,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(getSystemAccount(feeAccount), amount),
        entry(getSystemAccount(CASH_POSITION), amount.negate()));
  }

  private UUID generateAccrualReference(
      String fund, LocalDate accrualDate, SystemAccount feeAccount) {
    String key = fund + ":" + accrualDate + ":" + feeAccount.name();
    return UUID.nameUUIDFromBytes(key.getBytes(UTF_8));
  }

  // TODO: should come from the settlement payment instead
  private UUID generateSettlementReference(
      String fund, LocalDate settlementDate, SystemAccount feeAccount) {
    String key = fund + ":" + settlementDate + ":" + feeAccount.name() + ":SETTLEMENT";
    return UUID.nameUUIDFromBytes(key.getBytes(UTF_8));
  }

  private LedgerAccount getNavEquityAccount() {
    return getSystemAccount(NAV_EQUITY);
  }

  private LedgerAccount getSystemAccount(SystemAccount systemAccount) {
    return ledgerAccountService
        .findSystemAccount(systemAccount)
        .orElseGet(() -> ledgerAccountService.createSystemAccount(systemAccount));
  }

  private LedgerEntryDto entry(LedgerAccount account, BigDecimal amount) {
    return new LedgerEntryDto(account, amount);
  }
}
