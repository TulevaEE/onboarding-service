package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_SETTLEMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.RoundingMode.HALF_UP;
import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.investment.fees.FeeAccrual;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
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
  public void recordFeeAccrual(FeeAccrual accrual, SystemAccount feeAccount) {
    BigDecimal ledgerAmount = roundForLedger(accrual.dailyAmountNet());
    if (ledgerAmount == null || ledgerAmount.signum() == 0) {
      return;
    }

    String fund = accrual.fund().name();
    LocalDate accrualDate = accrual.accrualDate();

    UUID externalReference = generateAccrualReference(fund, accrualDate, feeAccount);
    if (ledgerTransactionService.existsByExternalReferenceAndTransactionType(
        externalReference, FEE_ACCRUAL)) {
      log.debug(
          "Fee accrual already recorded: fund={}, date={}, feeAccount={}",
          fund,
          accrualDate,
          feeAccount.name());
      return;
    }

    Map<String, Object> metadata = buildAccrualMetadata(accrual, feeAccount, ledgerAmount);

    ledgerTransactionService.createTransaction(
        FEE_ACCRUAL,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(getNavEquityAccount(), ledgerAmount),
        entry(getSystemAccount(feeAccount), ledgerAmount.negate()));
  }

  private Map<String, Object> buildAccrualMetadata(
      FeeAccrual accrual, SystemAccount feeAccount, BigDecimal ledgerAmount) {
    var metadata = new HashMap<String, Object>();
    metadata.put("operationType", "FEE_ACCRUAL");
    metadata.put("fund", accrual.fund().name());
    metadata.put("feeType", feeAccount.name());
    metadata.put("accrualDate", accrual.accrualDate());
    metadata.put("baseValue", accrual.baseValue());
    metadata.put("annualRate", accrual.annualRate());
    metadata.put("daysInYear", accrual.daysInYear());
    metadata.put("referenceDate", accrual.referenceDate());
    metadata.put("feeMonth", accrual.feeMonth());
    metadata.put("dailyAmountNet", accrual.dailyAmountNet());
    if (accrual.vatRate() != null) {
      metadata.put("vatRate", accrual.vatRate());
      metadata.put("dailyAmountGross", accrual.dailyAmountGross());
    }
    metadata.put("ledgerAmount", ledgerAmount);
    return metadata;
  }

  private BigDecimal roundForLedger(BigDecimal amount) {
    return amount != null ? amount.setScale(2, HALF_UP) : null;
  }

  @Transactional
  public void settleFeeAccrual(
      String fund, LocalDate settlementDate, SystemAccount feeAccount, BigDecimal amount) {
    if (amount == null || amount.signum() == 0) {
      return;
    }

    UUID externalReference = generateSettlementReference(fund, settlementDate, feeAccount);
    if (ledgerTransactionService.existsByExternalReferenceAndTransactionType(
        externalReference, FEE_SETTLEMENT)) {
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
        FEE_SETTLEMENT,
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
