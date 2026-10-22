package ee.tuleva.onboarding.banking.seb.processor;

import static ee.tuleva.onboarding.banking.statement.TransactionType.CREDIT;
import static ee.tuleva.onboarding.banking.statement.TransactionType.DEBIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.BANK_ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.BANK_FEE;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.INTEREST_RECEIVED;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.MANAGEMENT_FEE_PAYMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.MANAGEMENT_FEE_REBATE;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.OWN_ACCOUNT_TRANSFER;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REGISTRAR_CONTRIBUTION;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REGISTRAR_PAYOUT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRADE_SETTLEMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.UNCLASSIFIED_BANK_ENTRY;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry.CounterPartyDetails;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
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
      var target = moveOutOfSuspense(fund, suspense);

      if (target == null) {
        remaining++;
        log.info(
            "Suspense entry still unclassifiable: fund={}, externalRef={}",
            fund,
            suspense.getExternalReference());
        continue;
      }

      reclassified++;
      log.info(
          "Reclassified suspense entry: fund={}, externalRef={}, target={}",
          fund,
          suspense.getExternalReference(),
          target);
    }

    return new ReclassificationResult(reclassified, remaining);
  }

  private @Nullable TransactionType moveOutOfSuspense(TulevaFund fund, LedgerTransaction suspense) {
    var cashAmount = cashLegAmount(suspense, fund);
    var externalReference =
        requireNonNull(
            suspense.getExternalReference(), "Suspense transaction missing external reference");
    var bookingDate = suspense.getTransactionDate().atZone(ESTONIAN_ZONE).toLocalDate();

    return switch (classifier.classify(syntheticEntry(suspense, cashAmount))) {
      case PensionFundEntryClassifier.RegistrarContribution() ->
          reclassifyInto(REGISTRAR_CONTRIBUTION, fund, cashAmount, externalReference, bookingDate);
      case PensionFundEntryClassifier.RegistrarPayout() ->
          reclassifyInto(REGISTRAR_PAYOUT, fund, cashAmount, externalReference, bookingDate);
      case PensionFundEntryClassifier.ManagementFeePayment() ->
          reclassifyInto(MANAGEMENT_FEE_PAYMENT, fund, cashAmount, externalReference, bookingDate);
      case PensionFundEntryClassifier.ManagementFeeRebate() ->
          reclassifyInto(MANAGEMENT_FEE_REBATE, fund, cashAmount, externalReference, bookingDate);
      case PensionFundEntryClassifier.OwnAccountTransfer() ->
          reclassifyInto(OWN_ACCOUNT_TRANSFER, fund, cashAmount, externalReference, bookingDate);
      case PensionFundEntryClassifier.BankFee() ->
          reclassifyInto(BANK_FEE, fund, cashAmount, externalReference, bookingDate);
      case PensionFundEntryClassifier.InterestReceived() ->
          bookOnce(
              INTEREST_RECEIVED,
              externalReference,
              () ->
                  fundBankLedger.recordInterestReceived(
                      fund, cashAmount, externalReference, UNCLASSIFIED_BANK_ENTRY, bookingDate));
      case PensionFundEntryClassifier.BankAdjustment() ->
          bookOnce(
              BANK_ADJUSTMENT,
              externalReference,
              () ->
                  fundBankLedger.recordBankAdjustment(
                      fund, cashAmount, externalReference, UNCLASSIFIED_BANK_ENTRY, bookingDate));
      case PensionFundEntryClassifier.TradeSettlement(var trade) ->
          bookOnce(
              TRADE_SETTLEMENT,
              externalReference,
              () ->
                  fundBankLedger.recordTradeSettlement(
                      fund,
                      cashAmount,
                      trade.signedUnits(cashAmount),
                      externalReference,
                      UNCLASSIFIED_BANK_ENTRY,
                      trade.isin(),
                      trade.ticker(),
                      trade.displayName(),
                      bookingDate));
      case PensionFundEntryClassifier.UnrecognisedManagementCompanyCredit(),
          PensionFundEntryClassifier.Unclassified _ ->
          null;
    };
  }

  private TransactionType bookOnce(
      TransactionType target, UUID externalReference, Runnable booking) {
    if (!fundBankLedger.hasLedgerEntry(externalReference, target)) {
      booking.run();
    }
    return target;
  }

  private TransactionType reclassifyInto(
      TransactionType target,
      TulevaFund fund,
      BigDecimal cashAmount,
      UUID externalReference,
      LocalDate bookingDate) {
    fundBankLedger.reclassifySuspenseEntry(
        fund, cashAmount, externalReference, target, bookingDate);
    return target;
  }

  private BigDecimal cashLegAmount(LedgerTransaction suspense, TulevaFund fund) {
    var suspenseAccountName = UNCLASSIFIED_BANK_ENTRY.getAccountName(fund);
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
        cashAmount.signum() >= 0 ? CREDIT : DEBIT,
        (String)
            requireNonNull(metadata.get("description"), "Suspense metadata missing description"),
        "suspense-reclassification",
        null,
        (String) metadata.get("subFamilyCode"),
        null,
        true);
  }

  private static @Nullable CounterPartyDetails counterpartyDetails(Map<String, Object> metadata) {
    var iban = (String) metadata.get("counterpartyIban");
    if (iban == null) {
      return null;
    }
    return new CounterPartyDetails((String) metadata.get("counterpartyName"), iban, null);
  }
}
