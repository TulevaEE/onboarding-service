package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.*;
import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankOperationProcessor {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final String FEES = "FEES";
  private static final String COMM = "COMM";
  private static final String INTR = "INTR";
  private static final String ADJT = "ADJT";
  private static final String OTHR = "OTHR";
  private static final String TRAD = "TRAD";
  private static final String SUBS = "SUBS";
  private static final String BOOK = "BOOK";

  private final FundBankLedger fundBankLedger;
  private final TradeSettlementParser tradeSettlementParser;

  public void processBankOperation(BankStatementEntry entry, BankAccount account) {
    if (entry.details() != null) {
      return;
    }

    if (entry.externalId() == null || entry.externalId().isBlank()) {
      throw new IllegalStateException(
          "Bank entry without external id: account=%s, amount=%s"
              .formatted(account, entry.amount()));
    }

    var externalReference =
        UUID.nameUUIDFromBytes((account.iban() + ":" + entry.externalId()).getBytes(UTF_8));

    var amount = normalizeAmount(entry.amount());
    var clearingAccount = account.ledgerAccount();
    var subFamilyCode = entry.subFamilyCode();

    TransactionType transactionType =
        subFamilyCode == null
            ? null
            : mapSubFamilyCode(subFamilyCode, entry.remittanceInformation());
    if (transactionType == null) {
      parkInSuspense(entry, account, externalReference, amount, "unknown subFamilyCode");
      return;
    }

    if (fundBankLedger.hasLedgerEntry(externalReference, transactionType)) {
      log.debug(
          "Ledger entry already exists: subFamilyCode={}, externalRef={}",
          subFamilyCode,
          externalReference);
      return;
    }

    var bookingDate = bookingDate(entry);

    switch (subFamilyCode) {
      case INTR -> {
        log.info(
            "Bank interest received: amount={}, externalRef={}, account={}, description={}",
            amount,
            externalReference,
            account,
            entry.remittanceInformation());
        fundBankLedger.recordInterestReceived(
            account.fund(), amount, externalReference, clearingAccount, bookingDate);
      }
      case FEES, COMM -> {
        log.info(
            "Bank fee charged: amount={}, externalRef={}, account={}, description={}",
            amount,
            externalReference,
            account,
            entry.remittanceInformation());
        fundBankLedger.recordBankFee(
            account.fund(), amount, externalReference, clearingAccount, bookingDate);
      }
      case ADJT, OTHR -> {
        log.info(
            "Bank adjustment: amount={}, externalRef={}, account={}, description={}",
            amount,
            externalReference,
            account,
            entry.remittanceInformation());
        fundBankLedger.recordBankAdjustment(
            account.fund(), amount, externalReference, clearingAccount, bookingDate);
      }
      case TRAD, SUBS -> {
        var tradeInfo = tradeSettlementParser.parse(entry.remittanceInformation());
        if (tradeInfo.isEmpty()) {
          parkInSuspense(entry, account, externalReference, amount, "unknown ticker");
          return;
        }
        var settlement = tradeInfo.get();
        var units = signedUnits(settlement.units(), amount);
        log.info(
            "Trade settlement: amount={}, units={}, externalRef={}, account={}, ticker={}, isin={}",
            amount,
            units,
            externalReference,
            account,
            settlement.ticker(),
            settlement.isin());
        fundBankLedger.recordTradeSettlement(
            account.fund(),
            amount,
            units,
            externalReference,
            clearingAccount,
            settlement.isin(),
            settlement.ticker(),
            settlement.displayName(),
            bookingDate);
      }
      case BOOK -> {
        log.info(
            "Management fee rebate received: amount={}, externalRef={}, account={}, description={}",
            amount,
            externalReference,
            account,
            entry.remittanceInformation());
        fundBankLedger.recordManagementFeeRebate(
            account.fund(),
            amount,
            externalReference,
            clearingAccount,
            bookingDate,
            entry.remittanceInformation());
      }
      default -> throw new IllegalStateException("Unexpected value: " + subFamilyCode);
    }
  }

  private TransactionType mapSubFamilyCode(String subFamilyCode, String remittanceInformation) {
    return switch (subFamilyCode) {
      case INTR -> INTEREST_RECEIVED;
      case FEES, COMM -> BANK_FEE;
      case ADJT, OTHR -> BANK_ADJUSTMENT;
      case TRAD, SUBS -> TRADE_SETTLEMENT;
      case BOOK -> isManagementFeeRebate(remittanceInformation) ? MANAGEMENT_FEE_REBATE : null;
      default -> null;
    };
  }

  private static boolean isManagementFeeRebate(String remittanceInformation) {
    return remittanceInformation != null
        && remittanceInformation.toLowerCase().contains("kickback");
  }

  private void parkInSuspense(
      BankStatementEntry entry,
      BankAccount account,
      UUID externalReference,
      BigDecimal amount,
      String reason) {
    if (fundBankLedger.hasLedgerEntry(externalReference, UNCLASSIFIED_BANK_ENTRY)) {
      log.debug("Suspense entry already exists: externalRef={}", externalReference);
      return;
    }
    log.error(
        "Unclassified bank operation: account={}, externalId={}, amount={}, subFamilyCode={}, reason={}",
        account,
        entry.externalId(),
        entry.amount(),
        entry.subFamilyCode(),
        reason);
    fundBankLedger.recordUnclassifiedBankEntry(
        account.fund(),
        amount,
        externalReference,
        account.ledgerAccount(),
        bookingDate(entry),
        new FundBankLedger.UnclassifiedEntryDetails(
            null, null, entry.remittanceInformation(), entry.subFamilyCode()));
  }

  private static BigDecimal signedUnits(BigDecimal units, BigDecimal amount) {
    var scaled = units.abs().setScale(5, RoundingMode.HALF_UP);
    return amount.signum() > 0 ? scaled.negate() : scaled;
  }

  private static LocalDate bookingDate(BankStatementEntry entry) {
    var receivedBefore = entry.receivedBefore();
    if (receivedBefore == null) {
      throw new IllegalStateException(
          "Bank entry without booking time: externalId=%s".formatted(entry.externalId()));
    }
    return receivedBefore.atZone(ESTONIAN_ZONE).toLocalDate();
  }

  private BigDecimal normalizeAmount(BigDecimal amount) {
    var normalized = amount.setScale(2, RoundingMode.HALF_UP);
    if (amount.compareTo(normalized) != 0) {
      log.info("Normalized bank operation amount: original={}, normalized={}", amount, normalized);
    }
    return normalized;
  }
}
