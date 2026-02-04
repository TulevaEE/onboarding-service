package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.*;
import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankOperationProcessor {

  private static final String FEES = "FEES";
  private static final String COMM = "COMM";
  private static final String INTR = "INTR";
  private static final String ADJT = "ADJT";

  private final SavingsFundLedger savingsFundLedger;

  public void processBankOperation(
      BankStatementEntry entry, String accountIban, BankAccountType accountType) {
    if (entry.details() != null) {
      return;
    }

    var subFamilyCode = entry.subFamilyCode();
    if (subFamilyCode == null) {
      log.warn(
          "Bank operation without SubFmlyCd: externalId={}, amount={}",
          entry.externalId(),
          entry.amount());
      return;
    }

    var externalReference =
        UUID.nameUUIDFromBytes((accountIban + ":" + entry.externalId()).getBytes(UTF_8));

    var amount = normalizeAmount(entry.amount());
    var clearingAccount = accountType.getLedgerAccount();

    TransactionType transactionType = mapSubFamilyCode(subFamilyCode);
    if (transactionType == null) {
      log.warn(
          "Unknown bank operation SubFmlyCd: subFamilyCode={}, externalId={}, amount={}",
          subFamilyCode,
          entry.externalId(),
          entry.amount());
      return;
    }

    if (savingsFundLedger.hasLedgerEntry(externalReference, transactionType)) {
      log.debug(
          "Ledger entry already exists: subFamilyCode={}, externalRef={}",
          subFamilyCode,
          externalReference);
      return;
    }

    switch (subFamilyCode) {
      case INTR -> {
        log.info(
            "Bank interest received: amount={}, externalRef={}, account={}, description={}",
            amount,
            externalReference,
            accountType,
            entry.remittanceInformation());
        savingsFundLedger.recordInterestReceived(amount, externalReference, clearingAccount);
      }
      case FEES, COMM -> {
        log.info(
            "Bank fee charged: amount={}, externalRef={}, account={}, description={}",
            amount,
            externalReference,
            accountType,
            entry.remittanceInformation());
        savingsFundLedger.recordBankFee(amount, externalReference, clearingAccount);
      }
      case ADJT -> {
        log.info(
            "Bank fee adjustment: amount={}, externalRef={}, account={}, description={}",
            amount,
            externalReference,
            accountType,
            entry.remittanceInformation());
        savingsFundLedger.recordBankAdjustment(amount, externalReference, clearingAccount);
      }
    }
  }

  private TransactionType mapSubFamilyCode(String subFamilyCode) {
    return switch (subFamilyCode) {
      case INTR -> INTEREST_RECEIVED;
      case FEES, COMM -> BANK_FEE;
      case ADJT -> BANK_ADJUSTMENT;
      default -> null;
    };
  }

  private BigDecimal normalizeAmount(BigDecimal amount) {
    var normalized = amount.setScale(2, RoundingMode.HALF_UP);
    if (amount.compareTo(normalized) != 0) {
      log.info("Normalized bank operation amount: original={}, normalized={}", amount, normalized);
    }
    return normalized;
  }
}
