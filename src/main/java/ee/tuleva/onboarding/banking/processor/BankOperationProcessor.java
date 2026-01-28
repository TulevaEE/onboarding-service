package ee.tuleva.onboarding.banking.processor;

import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
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
  private static final String INTR = "INTR";
  private static final String ADJT = "ADJT";

  private final SavingsFundLedger savingsFundLedger;

  public void processBankOperation(BankStatementEntry entry, UUID messageId) {
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
        UUID.nameUUIDFromBytes((messageId + ":" + entry.externalId()).getBytes());

    var amount = normalizeAmount(entry.amount());

    if (savingsFundLedger.hasLedgerEntry(externalReference)) {
      log.debug(
          "Ledger entry already exists: subFamilyCode={}, externalRef={}",
          subFamilyCode,
          externalReference);
      return;
    }

    switch (subFamilyCode) {
      case INTR -> {
        log.info(
            "Bank interest received: amount={}, externalRef={}, description={}",
            amount,
            externalReference,
            entry.remittanceInformation());
        savingsFundLedger.recordInterestReceived(amount, externalReference);
      }
      case FEES -> {
        log.info(
            "Bank fee charged: amount={}, externalRef={}, description={}",
            amount,
            externalReference,
            entry.remittanceInformation());
        savingsFundLedger.recordBankFee(amount, externalReference);
      }
      case ADJT -> {
        log.info(
            "Bank fee adjustment: amount={}, externalRef={}, description={}",
            amount,
            externalReference,
            entry.remittanceInformation());
        savingsFundLedger.recordBankAdjustment(amount, externalReference);
      }
      default ->
          log.warn(
              "Unknown bank operation SubFmlyCd: subFamilyCode={}, externalId={}, amount={}",
              subFamilyCode,
              entry.externalId(),
              entry.amount());
    }
  }

  private BigDecimal normalizeAmount(BigDecimal amount) {
    var normalized = amount.setScale(2, RoundingMode.HALF_UP);
    if (amount.compareTo(normalized) != 0) {
      log.info("Normalized bank operation amount: original={}, normalized={}", amount, normalized);
    }
    return normalized;
  }
}
