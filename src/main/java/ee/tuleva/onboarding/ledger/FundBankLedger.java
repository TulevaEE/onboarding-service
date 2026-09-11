package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.*;
import static java.time.temporal.ChronoUnit.MICROS;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@NullMarked
@RequiredArgsConstructor
public class FundBankLedger {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;
  private final Clock clock;

  @Transactional
  public LedgerTransaction recordManagementFeePayment(
      TulevaFund fund,
      BigDecimal amount,
      UUID externalReference,
      String description,
      LocalDate bookingDate) {
    LedgerAccount managementFeeAccount = getSystemAccount(SystemAccount.MANAGEMENT_FEE, fund);
    LedgerAccount clearingAccount =
        getSystemAccount(SystemAccount.FUND_INVESTMENT_CASH_CLEARING, fund);

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.getKey(),
            MANAGEMENT_FEE_PAYMENT.name(),
            DESCRIPTION.getKey(),
            description);

    return ledgerTransactionService.createTransaction(
        MANAGEMENT_FEE_PAYMENT,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(managementFeeAccount, amount),
        entry(clearingAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction recordBankFee(
      TulevaFund fund,
      BigDecimal amount,
      UUID externalReference,
      SystemAccount clearingAccount,
      LocalDate bookingDate) {
    LedgerAccount bankFeeExpenseAccount = getSystemAccount(SystemAccount.BANK_FEE, fund);
    LedgerAccount clearingLedgerAccount = getSystemAccount(clearingAccount, fund);

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.getKey(), BANK_FEE.name());

    return ledgerTransactionService.createTransaction(
        BANK_FEE,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(bankFeeExpenseAccount, amount.negate()),
        entry(clearingLedgerAccount, amount));
  }

  @Transactional
  public LedgerTransaction recordInterestReceived(
      TulevaFund fund,
      BigDecimal amount,
      UUID externalReference,
      SystemAccount clearingAccount,
      LocalDate bookingDate) {
    LedgerAccount clearingLedgerAccount = getSystemAccount(clearingAccount, fund);
    LedgerAccount interestIncomeAccount = getSystemAccount(SystemAccount.INTEREST_INCOME, fund);

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.getKey(), INTEREST_RECEIVED.name());

    return ledgerTransactionService.createTransaction(
        INTEREST_RECEIVED,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(clearingLedgerAccount, amount),
        entry(interestIncomeAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction recordManagementFeeRebate(
      TulevaFund fund,
      BigDecimal amount,
      UUID externalReference,
      SystemAccount clearingAccount,
      LocalDate bookingDate,
      String description) {
    LedgerAccount rebateIncomeAccount = getSystemAccount(SystemAccount.MANAGEMENT_FEE_REBATE, fund);
    LedgerAccount clearingLedgerAccount = getSystemAccount(clearingAccount, fund);

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.getKey(),
            MANAGEMENT_FEE_REBATE.name(),
            DESCRIPTION.getKey(),
            description);

    return ledgerTransactionService.createTransaction(
        MANAGEMENT_FEE_REBATE,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(clearingLedgerAccount, amount),
        entry(rebateIncomeAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction recordBankAdjustment(
      TulevaFund fund,
      BigDecimal amount,
      UUID externalReference,
      SystemAccount clearingAccount,
      LocalDate bookingDate) {
    LedgerAccount bankAdjustmentAccount = getSystemAccount(SystemAccount.BANK_ADJUSTMENT, fund);
    LedgerAccount clearingLedgerAccount = getSystemAccount(clearingAccount, fund);

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.getKey(), BANK_ADJUSTMENT.name());

    return ledgerTransactionService.createTransaction(
        BANK_ADJUSTMENT,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(bankAdjustmentAccount, amount.negate()),
        entry(clearingLedgerAccount, amount));
  }

  @Transactional
  public LedgerTransaction recordTradeSettlement(
      TulevaFund fund,
      BigDecimal amount,
      BigDecimal units,
      UUID externalReference,
      SystemAccount clearingAccount,
      String isin,
      String ticker,
      String displayName,
      LocalDate bookingDate) {
    LedgerAccount clearingLedgerAccount = getSystemAccount(clearingAccount, fund);
    LedgerAccount tradeSettlementAccount =
        getInstrumentAccount(SystemAccount.TRADE_CASH_SETTLEMENT, fund, isin);
    LedgerAccount securityUnitsAccount =
        getInstrumentAccount(SystemAccount.TRADE_UNIT_SETTLEMENT, fund, isin);
    LedgerAccount securitiesCustodyAccount =
        getInstrumentAccount(SystemAccount.SECURITIES_CUSTODY, fund, isin);

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.getKey(), TRADE_SETTLEMENT.name(),
            INSTRUMENT.getKey(), isin,
            TICKER.getKey(), ticker,
            DISPLAY_NAME.getKey(), displayName);

    return ledgerTransactionService.createTransaction(
        TRADE_SETTLEMENT,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(clearingLedgerAccount, amount),
        entry(tradeSettlementAccount, amount.negate()),
        entry(securityUnitsAccount, units.negate()),
        entry(securitiesCustodyAccount, units));
  }

  public boolean hasLedgerEntry(UUID externalReference, TransactionType transactionType) {
    return ledgerTransactionService.existsByExternalReferenceAndTransactionType(
        externalReference, transactionType);
  }

  public boolean existsForExternalReference(UUID externalReference) {
    return ledgerTransactionService.existsByExternalReference(externalReference);
  }

  public long countUnresolvedUnclassifiedEntries(TulevaFund fund) {
    return ledgerTransactionService.countUnresolvedByTransactionTypeAndAccountName(
        UNCLASSIFIED_BANK_ENTRY, SystemAccount.UNCLASSIFIED_BANK_ENTRY.getAccountName(fund));
  }

  public List<LedgerTransaction> findUnresolvedUnclassifiedEntries(TulevaFund fund) {
    return ledgerTransactionService.findUnresolvedByTransactionTypeAndAccountName(
        UNCLASSIFIED_BANK_ENTRY, SystemAccount.UNCLASSIFIED_BANK_ENTRY.getAccountName(fund));
  }

  @Transactional
  public LedgerTransaction reclassifySuspenseEntry(
      TulevaFund fund,
      BigDecimal amount,
      UUID externalReference,
      TransactionType targetType,
      LocalDate bookingDate) {
    var existing =
        ledgerTransactionService.findByExternalReferenceAndTransactionType(
            externalReference, targetType);
    if (existing.isPresent()) {
      return existing.get();
    }

    LedgerAccount suspenseAccount = getSystemAccount(SystemAccount.UNCLASSIFIED_BANK_ENTRY, fund);
    LedgerAccount targetAccount = getSystemAccount(reclassificationTarget(targetType), fund);

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.getKey(),
            targetType.name(),
            DESCRIPTION.getKey(),
            "Reclassified from suspense");

    return ledgerTransactionService.createTransaction(
        targetType,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(suspenseAccount, amount),
        entry(targetAccount, amount.negate()));
  }

  private static SystemAccount reclassificationTarget(TransactionType targetType) {
    return switch (targetType) {
      case REGISTRAR_CONTRIBUTION, REGISTRAR_PAYOUT -> SystemAccount.REGISTRAR_CASH_SETTLEMENT;
      case MANAGEMENT_FEE_PAYMENT -> SystemAccount.MANAGEMENT_FEE;
      case MANAGEMENT_FEE_REBATE -> SystemAccount.MANAGEMENT_FEE_REBATE;
      case OWN_ACCOUNT_TRANSFER -> SystemAccount.OWN_ACCOUNT_TRANSFER;
      case BANK_FEE -> SystemAccount.BANK_FEE;
      default ->
          throw new IllegalArgumentException(
              "Unsupported reclassification target: %s".formatted(targetType));
    };
  }

  @Transactional
  public LedgerTransaction recordRegistrarContribution(
      TulevaFund fund,
      BigDecimal amount,
      UUID externalReference,
      LocalDate bookingDate,
      String description) {
    return recordRegistrarCashMovement(
        REGISTRAR_CONTRIBUTION, fund, amount, externalReference, bookingDate, description);
  }

  @Transactional
  public LedgerTransaction recordRegistrarPayout(
      TulevaFund fund,
      BigDecimal amount,
      UUID externalReference,
      LocalDate bookingDate,
      String description) {
    return recordRegistrarCashMovement(
        REGISTRAR_PAYOUT, fund, amount, externalReference, bookingDate, description);
  }

  private LedgerTransaction recordRegistrarCashMovement(
      TransactionType transactionType,
      TulevaFund fund,
      BigDecimal amount,
      UUID externalReference,
      LocalDate bookingDate,
      String description) {
    LedgerAccount cashAccount = getSystemAccount(SystemAccount.FUND_INVESTMENT_CASH_CLEARING, fund);
    LedgerAccount registrarAccount =
        getSystemAccount(SystemAccount.REGISTRAR_CASH_SETTLEMENT, fund);

    Map<String, Object> metadata =
        Map.of(OPERATION_TYPE.getKey(), transactionType.name(), DESCRIPTION.getKey(), description);

    return ledgerTransactionService.createTransaction(
        transactionType,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(cashAccount, amount),
        entry(registrarAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction recordUnclassifiedBankEntry(
      TulevaFund fund,
      BigDecimal amount,
      UUID externalReference,
      SystemAccount clearingAccount,
      LocalDate bookingDate,
      UnclassifiedEntryDetails details) {
    LedgerAccount cashAccount = getSystemAccount(clearingAccount, fund);
    LedgerAccount suspenseAccount = getSystemAccount(SystemAccount.UNCLASSIFIED_BANK_ENTRY, fund);

    var metadata = new HashMap<String, Object>();
    metadata.put(OPERATION_TYPE.getKey(), UNCLASSIFIED_BANK_ENTRY.name());
    if (details.counterpartyName() != null) {
      metadata.put(COUNTERPARTY_NAME.getKey(), details.counterpartyName());
    }
    if (details.counterpartyIban() != null) {
      metadata.put(COUNTERPARTY_IBAN.getKey(), details.counterpartyIban());
    }
    if (details.remittanceInformation() != null) {
      metadata.put(DESCRIPTION.getKey(), details.remittanceInformation());
    }
    if (details.subFamilyCode() != null) {
      metadata.put(SUB_FAMILY_CODE.getKey(), details.subFamilyCode());
    }

    return ledgerTransactionService.createTransaction(
        UNCLASSIFIED_BANK_ENTRY,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(cashAccount, amount),
        entry(suspenseAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction recordOwnAccountTransfer(
      TulevaFund fund,
      BigDecimal amount,
      UUID externalReference,
      LocalDate bookingDate,
      String description) {
    LedgerAccount cashAccount = getSystemAccount(SystemAccount.FUND_INVESTMENT_CASH_CLEARING, fund);
    LedgerAccount ownTransferAccount = getSystemAccount(SystemAccount.OWN_ACCOUNT_TRANSFER, fund);

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.getKey(),
            OWN_ACCOUNT_TRANSFER.name(),
            DESCRIPTION.getKey(),
            description);

    return ledgerTransactionService.createTransaction(
        OWN_ACCOUNT_TRANSFER,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(cashAccount, amount),
        entry(ownTransferAccount, amount.negate()));
  }

  @Transactional
  public void seedOpeningBalanceIfFirstStatement(
      TulevaFund fund, BigDecimal openingBalance, LocalDate asOfDate) {
    if (openingBalance.signum() == 0) {
      return;
    }
    var cashAccountName = SystemAccount.FUND_INVESTMENT_CASH_CLEARING.getAccountName(fund);
    if (ledgerTransactionService.hasEntriesForAccountName(cashAccountName)) {
      return;
    }
    recordOpeningBalance(fund, openingBalance, asOfDate);
    log.info(
        "Seeded opening balance from first bank statement: fund={}, asOfDate={}", fund, asOfDate);
  }

  @Transactional
  public LedgerTransaction recordOpeningBalance(
      TulevaFund fund, BigDecimal amount, LocalDate asOfDate) {
    var externalReference =
        UUID.nameUUIDFromBytes(("OPENING:" + fund.name()).getBytes(StandardCharsets.UTF_8));
    var existing =
        ledgerTransactionService.findByExternalReferenceAndTransactionType(
            externalReference, OPENING_BALANCE);
    if (existing.isPresent()) {
      return existing.get();
    }

    LedgerAccount cashAccount = getSystemAccount(SystemAccount.FUND_INVESTMENT_CASH_CLEARING, fund);
    LedgerAccount registrarAccount =
        getSystemAccount(SystemAccount.REGISTRAR_CASH_SETTLEMENT, fund);

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.getKey(), OPENING_BALANCE.name());

    return ledgerTransactionService.createTransaction(
        OPENING_BALANCE,
        transactionDate(asOfDate),
        externalReference,
        metadata,
        entry(cashAccount, amount),
        entry(registrarAccount, amount.negate()));
  }

  public record UnclassifiedEntryDetails(
      @Nullable String counterpartyName,
      @Nullable String counterpartyIban,
      @Nullable String remittanceInformation,
      @Nullable String subFamilyCode) {}

  private LedgerAccount getSystemAccount(SystemAccount systemAccount, TulevaFund fund) {
    return ledgerAccountService
        .findSystemAccount(systemAccount, fund)
        .orElseGet(() -> ledgerAccountService.createSystemAccount(systemAccount, fund));
  }

  private LedgerAccount getInstrumentAccount(
      SystemAccount systemAccount, TulevaFund fund, String isin) {
    var accountName = systemAccount.getAccountName(fund, isin);
    return ledgerAccountService
        .findSystemAccountByName(
            accountName, systemAccount.getAccountType(), systemAccount.getAssetType())
        .orElseGet(
            () ->
                ledgerAccountService.createSystemAccount(
                    accountName, systemAccount.getAccountType(), systemAccount.getAssetType()));
  }

  private Instant transactionDate(LocalDate bookingDate) {
    Instant now = Instant.now(clock);
    if (now.atZone(ESTONIAN_ZONE).toLocalDate().equals(bookingDate)) {
      return now;
    }
    return bookingDate.atTime(LocalTime.MAX).atZone(ESTONIAN_ZONE).toInstant().truncatedTo(MICROS);
  }

  private LedgerEntryDto entry(LedgerAccount account, BigDecimal amount) {
    return new LedgerEntryDto(account, amount);
  }
}
