package ee.tuleva.onboarding.banking.seb.processor;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.BANK_FEE;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.MANAGEMENT_FEE_PAYMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.MANAGEMENT_FEE_REBATE;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.OWN_ACCOUNT_TRANSFER;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REGISTRAR_CONTRIBUTION;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REGISTRAR_PAYOUT;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry.CounterPartyDetails;
import ee.tuleva.onboarding.banking.statement.TransactionType;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Slf4j
@NullMarked
@RequiredArgsConstructor
public class SuspenseReclassificationService {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final PensionFundEntryClassifier classifier;
  private final FundBankLedger fundBankLedger;

  public record ReclassificationResult(int reclassified, int remaining) {}

  public ReclassificationResult reclassify(TulevaFund fund) {
    var unresolved = fundBankLedger.findUnresolvedUnclassifiedEntries(fund);
    int reclassified = 0;
    int remaining = 0;

    for (LedgerTransaction suspense : unresolved) {
      var cashAmount = cashLegAmount(suspense, fund);
      var target =
          switch (classifier.classify(syntheticEntry(suspense, cashAmount))) {
            case PensionFundEntryClassifier.RegistrarContribution() -> REGISTRAR_CONTRIBUTION;
            case PensionFundEntryClassifier.RegistrarPayout() -> REGISTRAR_PAYOUT;
            case PensionFundEntryClassifier.ManagementFeePayment() -> MANAGEMENT_FEE_PAYMENT;
            case PensionFundEntryClassifier.ManagementFeeRebate() -> MANAGEMENT_FEE_REBATE;
            case PensionFundEntryClassifier.OwnAccountTransfer() -> OWN_ACCOUNT_TRANSFER;
            case PensionFundEntryClassifier.BankFee() -> BANK_FEE;
            default -> null;
          };

      if (target == null) {
        remaining++;
        log.info(
            "Suspense entry still unclassifiable: fund={}, externalRef={}",
            fund,
            suspense.getExternalReference());
        continue;
      }

      fundBankLedger.reclassifySuspenseEntry(
          fund,
          cashAmount,
          suspense.getExternalReference(),
          target,
          suspense.getTransactionDate().atZone(ESTONIAN_ZONE).toLocalDate());
      reclassified++;
      log.info(
          "Reclassified suspense entry: fund={}, externalRef={}, target={}",
          fund,
          suspense.getExternalReference(),
          target);
    }

    return new ReclassificationResult(reclassified, remaining);
  }

  private BigDecimal cashLegAmount(LedgerTransaction suspense, TulevaFund fund) {
    var suspenseAccountName = SystemAccount.UNCLASSIFIED_BANK_ENTRY.getAccountName(fund);
    return suspense.getEntries().stream()
        .filter(entry -> !suspenseAccountName.equals(entry.getAccount().getName()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Suspense transaction without a cash leg: externalRef=%s"
                        .formatted(suspense.getExternalReference())))
        .getAmount();
  }

  private BankStatementEntry syntheticEntry(LedgerTransaction suspense, BigDecimal cashAmount) {
    Map<String, Object> metadata = suspense.getMetadata();
    return new BankStatementEntry(
        counterpartyDetails(metadata),
        cashAmount,
        "EUR",
        cashAmount.signum() >= 0 ? TransactionType.CREDIT : TransactionType.DEBIT,
        (String)
            requireNonNull(metadata.get("description"), "Suspense metadata missing description"),
        "suspense-reclassification",
        null,
        (String) metadata.get("subFamilyCode"),
        null);
  }

  private static @Nullable CounterPartyDetails counterpartyDetails(Map<String, Object> metadata) {
    var name = (String) metadata.get("counterpartyName");
    var iban = (String) metadata.get("counterpartyIban");
    if (name == null || iban == null) {
      return null;
    }
    return new CounterPartyDetails(name, iban, null);
  }
}
